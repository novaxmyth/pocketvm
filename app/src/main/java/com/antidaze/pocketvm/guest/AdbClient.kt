package com.antidaze.pocketvm.guest

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ADB protocol client (TCP transport) — enough to push a file, run a
 * shell command, and open raw streams (used to drive the scrcpy server inside
 * the Android guest via QEMU's slirp port-forward).
 *
 * Wire format: 24-byte header (cmd, arg0, arg1, dataLen, checksum, magic),
 * little-endian; checksum is the sum of payload bytes; magic = cmd ^ 0xFFFFFFFF.
 */
class AdbClient(private val host: String, private val port: Int) {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var maxData = 4096
    private val nextLocalId = AtomicInteger(1)
    @Volatile private var closed = false

    fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 15_000
        socket = s
        input = DataInputStream(s.getInputStream().buffered(1 shl 16))
        output = DataOutputStream(s.getOutputStream().buffered(1 shl 16))

        val hello = message(CMD_CNXN, VERSION, maxData, "host::".toByteArray())
        output!!.write(hello); output!!.flush()

        val reply = readMessage()
        when (reply.cmd) {
            CMD_CNXN -> {
                maxData = reply.arg1
            }
            CMD_AUTH -> throw AdbException("Guest adb requires authentication (expected ro.adb.secure=0)")
            else -> throw AdbException("Unexpected adb handshake response: ${cmdStr(reply.cmd)}")
        }
    }

    /** Runs a shell command and returns its combined output. */
    fun shell(command: String): String {
        Stream(this, "shell:$command").use { st -> return st.readAllAsString() }
    }

    /** Pushes a file to the device via the sync service. */
    fun pushFile(data: ByteArray, remotePath: String) {
        Stream(this, "sync:").use { st ->
            val buf = ByteBuffer.allocate(8 + remotePath.length + 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("SEND".toByteArray())
            buf.putInt(remotePath.length + 4)
            buf.put(remotePath.toByteArray())
            buf.putInt(0x81A4) // S_IFREG | 0644
            st.rawWrite(buf.array()); st.flush()

            var offset = 0
            while (offset < data.size) {
                val chunk = minOf(data.size - offset, 32 * 1024)
                val db = ByteBuffer.allocate(8 + chunk).order(ByteOrder.LITTLE_ENDIAN)
                db.put("DATA".toByteArray())
                db.putInt(chunk)
                db.put(data, offset, chunk)
                st.rawWrite(db.array())
                offset += chunk
            }
            val done = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            done.put("DONE".toByteArray())
            done.putInt((System.currentTimeMillis() / 1000).toInt())
            st.rawWrite(done.array()); st.flush()

            val resp = st.readSyncResponse()
            if (resp != "OKAY") throw AdbException("adb push failed: $resp")

            val quit = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            quit.put("QUIT".toByteArray())
            st.rawWrite(quit.array()); st.flush()
        }
    }

    /** Opens a raw bidirectional stream to a service (e.g. localabstract:scrcpy_N). */
    fun openStream(service: String): Stream = Stream(this, service)

    private fun newLocalId(): Int = nextLocalId.getAndIncrement()

    private fun send(cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        if (closed) throw AdbException("adb connection closed")
        output!!.write(message(cmd, arg0, arg1, data))
        output!!.flush()
    }

    private fun message(cmd: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cmd)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(data.size)
        buf.putInt(checksum(data))
        buf.putInt(cmd.inv())
        buf.put(data)
        return buf.array()
    }

    private fun readMessage(): Msg {
        val inp = input ?: throw AdbException("adb not connected")
        val header = ByteArray(24)
        inp.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = bb.int
        val arg0 = bb.int
        val arg1 = bb.int
        val len = bb.int
        val cks = bb.int
        val magic = bb.int
        if (magic != cmd.inv()) throw AdbException("adb corrupt header")
        val data = if (len > 0) ByteArray(len).also { inp.readFully(it) } else ByteArray(0)
        if (checksum(data) != cks) throw AdbException("adb corrupt payload")
        return Msg(cmd, arg0, arg1, data)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0L
        for (b in data) sum += b.toLong() and 0xFF
        return sum.toInt()
    }

    fun close() {
        closed = true
        try { socket?.close() } catch (e: Exception) { }
    }

    private data class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /**
     * One adb stream (localabstract: / shell: / sync:). Reads pump a
     * listener; writes are guarded. CLSE from either side ends the stream.
     */
    inner class Stream internal constructor(
        private val client: AdbClient,
        service: String
    ) : AutoCloseable {
        val localId = newLocalId()
        @Volatile var remoteId: Int = -1
            private set
        @Volatile var open = true
            private set
        private val writeLock = Any()

        init {
            client.send(CMD_OPEN, localId, 0, (service + "\u0000").toByteArray())
            var reply = client.readMessage()
            if (reply.cmd == CMD_OKAY && reply.arg1 == localId) {
                remoteId = reply.arg0
            } else if (reply.cmd == CMD_CLSE && reply.arg1 == localId) {
                open = false
                throw AdbException("adb service refused: $service")
            } else {
                throw AdbException("adb open failed: ${cmdStr(reply.cmd)}")
            }
        }

        fun read(): ByteArray {
            while (true) {
                if (!open) throw EOFException("stream closed")
                val m = client.readMessage()
                when (m.cmd) {
                    CMD_WRTE -> {
                        client.send(CMD_OKAY, localId, m.arg0, ByteArray(0))
                        return m.data
                    }
                    CMD_CLSE -> {
                        open = false
                        throw EOFException("stream closed by device")
                    }
                    CMD_OKAY -> continue
                    else -> throw AdbException("unexpected ${cmdStr(m.cmd)} on stream")
                }
            }
        }

        fun readAllAsString(): String {
            val sb = StringBuilder()
            while (true) {
                try {
                    sb.append(String(read()))
                } catch (e: EOFException) {
                    break
                }
            }
            return sb.toString()
        }

        /** sync: mode — reads one 4-byte sync token (OKAY/FAIL) + payload. */
        fun readSyncResponse(): String {
            val inp = client.input ?: throw AdbException("not connected")
            val id = ByteArray(4); inp.readFully(id)
            val len = ByteArray(4); inp.readFully(len)
            val n = ByteBuffer.wrap(len).order(ByteOrder.LITTLE_ENDIAN).int
            val msg = if (n > 0) ByteArray(n).also { inp.readFully(it) } else ByteArray(0)
            return String(id) + ": " + String(msg)
        }

        fun rawWrite(data: ByteArray) {
            synchronized(writeLock) {
                client.send(CMD_WRTE, localId, remoteId, data)
            }
        }

        fun write(data: ByteArray) = rawWrite(data)
        fun flush() { /* adb WRTE has no buffering layer of our own */ }

        override fun close() {
            if (open) {
                open = false
                try { client.send(CMD_CLSE, localId, remoteId, ByteArray(0)) } catch (e: Exception) { }
            }
        }
    }

    class AdbException(msg: String) : Exception(msg)

    companion object {
        const val VERSION = 0x01000001
        val CMD_CNXN = "CNXN".toIntLE()
        val CMD_AUTH = "AUTH".toIntLE()
        val CMD_OPEN = "OPEN".toIntLE()
        val CMD_OKAY = "OKAY".toIntLE()
        val CMD_CLSE = "CLSE".toIntLE()
        val CMD_WRTE = "WRTE".toIntLE()

        private fun String.toIntLE(): Int =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(toByteArray()).getInt(0)

        private fun cmdStr(cmd: Int): String =
            ByteBuffer.allocate(4).putInt(cmd).array().reversedArray().decodeToString()
    }
}
