package com.antidaze.pocketvm.vnc

import android.graphics.Bitmap
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
/**
 * Minimal RFB 3.8 client (VNC) tuned for a local QEMU server:
 * 32bpp BGRX pixel format, Raw + CopyRect + DesktopSize encodings,
 * continuous incremental update requests.
 */
class RfbClient(
    private val host: String,
    private val port: Int,
    private val listener: Listener
) {
    interface Listener {
        fun onFramebufferSize(width: Int, height: Int)
        fun onFrame(bitmap: Bitmap)
        fun onStatus(status: String)
        fun onDisconnected(reason: String)
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var fbWidth = 0
    private var fbHeight = 0
    private var buffer = IntArray(0)
    private var bitmap: Bitmap? = null

    @Volatile private var running = false
    @Volatile private var pendingFullRequest = false

    fun connect() {
        running = true
        Thread({ runLoop() }, "rfb-client").start()
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
    }

    private fun runLoop() {
        try {
            listener.onStatus("Connecting…")
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 8000)
            s.soTimeout = 0
            socket = s
            val inp = DataInputStream(s.getInputStream().buffered(1 shl 16))
            val out = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
            input = inp; output = out

            // --- Handshake ---
            val serverVer = ByteArray(12)
            inp.readFully(serverVer)
            val ver = String(serverVer)
            if (!ver.startsWith("RFB ")) throw IllegalStateException("Not an RFB server")
            out.write("RFB 003.008\n".toByteArray()); out.flush()

            val nSec = inp.readUnsignedByte()
            if (nSec == 0) throw IllegalStateException("Server refused connection")
            var chosen = -1
            repeat(nSec) {
                val t = inp.readUnsignedByte()
                if (t == 1) chosen = t // None
            }
            if (chosen != 1) throw IllegalStateException("No unsupported auth schemes offered")
            out.writeByte(chosen); out.flush()
            val result = inp.readInt()
            if (result != 0) throw IllegalStateException("Auth failed ($result)")

            out.writeByte(1); out.flush() // ClientInit: shared

            // --- ServerInit ---
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            val pf = ByteArray(16); inp.readFully(pf)
            val nameLen = inp.readInt()
            val name = ByteArray(nameLen.coerceIn(0, 4096)); inp.readFully(name)
            listener.onStatus("Connected: ${String(name)}")

            allocate(w, h)

            // Client pixel format: 32bpp, depth 24, little-endian, true colour,
            // R<<16 | G<<8 | B -> wire bytes [B, G, R, 0].
            out.write(0)                 // SetPixelFormat
            out.write(0); out.write(0); out.write(0)
            out.write(32)                // bits per pixel
            out.write(24)                // depth
            out.write(0)                 // big endian
            out.write(1)                 // true colour
            out.writeShort(255); out.writeShort(255); out.writeShort(255)
            out.write(16); out.write(8); out.write(0)
            out.write(0); out.write(0); out.write(0)
            sendEncodings(out)
            out.flush()

            listener.onFramebufferSize(w, h)
            requestUpdate(out, false)

            // --- Main read loop ---
            while (running) {
                when (inp.readUnsignedByte()) {
                    0 -> readFramebufferUpdate(inp, out)
                    1 -> { // SetColourMapEntries
                        inp.readUnsignedByte()
                        inp.readUnsignedShort()
                        val n = inp.readUnsignedShort()
                        repeat(n) { inp.readUnsignedShort(); inp.readUnsignedShort(); inp.readUnsignedShort() }
                    }
                    2 -> { /* Bell */ }
                    3 -> { // ServerCutText
                        inp.skipBytes(3)
                        val len = inp.readInt()
                        val text = ByteArray(len.coerceIn(0, 1 shl 20)); inp.readFully(text)
                    }
                    else -> throw IllegalStateException("Unknown message from server")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "rfb loop ended", e)
            if (running) listener.onDisconnected(e.message ?: "connection lost")
        } finally {
            running = false
            try { socket?.close() } catch (e: Exception) { }
        }
    }

    private fun sendEncodings(out: DataOutputStream) {
        out.write(2); out.write(0)
        out.writeShort(2)
        out.writeInt(1)   // CopyRect
        out.writeInt(0)   // Raw
    }

    private fun requestUpdate(out: DataOutputStream, incremental: Boolean) {
        synchronized(outputLock) {
            out.write(3); out.write(0)
            out.write(if (incremental) 1 else 0)
            out.writeShort(0); out.writeShort(0)
            out.writeShort(fbWidth); out.writeShort(fbHeight)
            out.flush()
        }
    }

    private val outputLock = Any()

    @Synchronized
    private fun allocate(w: Int, h: Int) {
        fbWidth = w; fbHeight = h
        buffer = IntArray(w * h)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun readFramebufferUpdate(inp: DataInputStream, out: DataOutputStream) {
        inp.readUnsignedByte()
        val nRects = inp.readUnsignedShort()
        for (i in 0 until nRects) {
            val x = inp.readUnsignedShort()
            val y = inp.readUnsignedShort()
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            val enc = inp.readInt()
            when (enc) {
                0 -> { // Raw
                    val bytes = ByteArray(w * h * 4)
                    inp.readFully(bytes)
                    var bi = y * fbWidth + x
                    for (row in 0 until h) {
                        var b = row * w * 4
                        for (col in 0 until w) {
                            val bl = bytes[b].toInt() and 0xFF
                            val gr = bytes[b + 1].toInt() and 0xFF
                            val rd = bytes[b + 2].toInt() and 0xFF
                            buffer[bi + col] = -0x1000000 or (rd shl 16) or (gr shl 8) or bl
                            b += 4
                        }
                        bi += fbWidth
                    }
                    paintRect(x, y, w, h)
                }
                1 -> { // CopyRect
                    val srcX = inp.readUnsignedShort()
                    val srcY = inp.readUnsignedShort()
                    copyRect(x, y, w, h, srcX, srcY)
                    paintRect(x, y, w, h)
                }
                -223 -> { // DesktopSize: w/h are the new dimensions
                    allocate(w, h)
                    listener.onFramebufferSize(w, h)
                    requestUpdate(out, false)
                    return
                }
                -239 -> { // Cursor pseudo-encoding: shape + palette + mask
                    val pixels = w * h * 4
                    val mask = ((w + 7) / 8) * h
                    val buf = ByteArray(pixels + mask)
                    inp.readFully(buf)
                }
                else -> throw IllegalStateException("Unhandled encoding $enc")
            }
        }
        if (pendingFullRequest) {
            pendingFullRequest = false
            requestUpdate(out, false)
        } else {
            requestUpdate(out, true)
        }
    }

    private fun paintRect(x: Int, y: Int, w: Int, h: Int) {
        val bm = bitmap ?: return
        try {
            bm.setPixels(buffer, y * fbWidth + x, fbWidth, x, y, w, h)
        } catch (e: Exception) { /* clipped rect race on resize */ }
        listener.onFrame(bm)
    }

    private fun copyRect(dx: Int, dy: Int, w: Int, h: Int, sx: Int, sy: Int) {
        if (dy >= sy) {
            for (row in h - 1 downTo 0) {
                System.arraycopy(buffer, (sy + row) * fbWidth + sx, buffer, (dy + row) * fbWidth + dx, w)
            }
        } else {
            for (row in 0 until h) {
                System.arraycopy(buffer, (sy + row) * fbWidth + sx, buffer, (dy + row) * fbWidth + dx, w)
            }
        }
    }

    // ---------------- input ----------------

    fun sendPointer(x: Int, y: Int, buttons: Int) {
        val out = output ?: return
        synchronized(outputLock) {
            try {
                out.write(4); out.write(buttons and 0xFF)
                out.writeShort(x.coerceIn(0, fbWidth - 1))
                out.writeShort(y.coerceIn(0, fbHeight - 1))
                out.flush()
            } catch (e: Exception) { }
        }
    }

    fun sendKey(keysym: Int, down: Boolean) {
        val out = output ?: return
        synchronized(outputLock) {
            try {
                out.write(5); out.write(if (down) 1 else 0); out.write(0); out.write(0)
                out.writeInt(keysym)
                out.flush()
            } catch (e: Exception) { }
        }
    }

    companion object {
        private const val TAG = "RfbClient"
    }
}
