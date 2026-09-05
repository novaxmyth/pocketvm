package com.antidaze.pocketvm.guest

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * scrcpy client for the Android guest: pushes the bundled scrcpy-server into
 * the guest over adb, starts it in tunnel-forward mode, then decodes the
 * H.264 stream onto a Surface and sends touch/key input back.
 *
 * Protocol: scrcpy 2.1 (audio disabled; video and control over two
 * localabstract:scrcpy_<scid> sockets, forward mode).
 */
class ScrcpyClient(
    private val adb: AdbClient,
    private val serverJar: ByteArray,
    private val surface: Surface,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(status: String)
        fun onVideoSize(width: Int, height: Int)
        fun onFatal(reason: String)
    }

    private val scid = java.util.Random().nextInt(0x40000000)
    private val controlExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var controlStream: AdbClient.Stream? = null
    private var videoSize = 0 to 0

    /** Starts the server and streams. Blocking — call from a worker thread. */
    fun start() {
        try {
            running = true
            listener.onStatus("Installing Android display server…")
            adb.pushFile(serverJar, "/data/local/tmp/scrcpy-server.jar")

            listener.onStatus("Starting Android display…")
            val cmd = "CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / " +
                "com.genymobile.scrcpy.Server 2.1 " +
                "scid=$scid log_level=info audio=false tunnel_forward=true " +
                "max_size=720 max_fps=20 stay_awake=true send_dummy_byte=true"
            val shellStream = adb.openStream("shell:$cmd")
            Thread({
                try {
                    while (running) {
                        val chunk = try { shellStream.read() } catch (e: Exception) { break }
                        if (chunk.isNotEmpty()) Log.i("scrcpy", String(chunk))
                    }
                } catch (e: Exception) { }
            }, "scrcpy-logs").start()

            listener.onStatus("Connecting to guest display…")
            val video = adb.openStream("localabstract:scrcpy_$scid")
            val vin = AdbBufferedInput(video)

            // forward mode: dummy byte, then device meta (name), then codec meta
            vin.skip(1)
            val nameLen = vin.readIntLE()
            if (nameLen in 1..256) vin.skip(nameLen)
            val codecId = vin.readIntLE()
            val w = vin.readIntLE()
            val h = vin.readIntLE()
            if (codecId != 0x68323634 /* 'h264' */) throw ScrcpyException("guest video codec not h264")
            videoSize = w to h

            controlStream = adb.openStream("localabstract:scrcpy_$scid")

            listener.onVideoSize(w, h)
            listener.onStatus("Android display connected")
            decodeLoop(vin)
        } catch (e: Exception) {
            Log.w(TAG, "scrcpy failed", e)
            if (running) listener.onFatal(e.message ?: "scrcpy failed")
        }
    }

    fun stop() {
        running = false
        controlExecutor.shutdownNow()
        try { controlStream?.close() } catch (e: Exception) { }
    }

    private fun decodeLoop(vin: AdbBufferedInput) {
        val codec = MediaCodec.createDecoderByType("video/avc")
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoSize.first, videoSize.second)
            codec.configure(format, surface, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            while (running) {
                val ptsFlags = vin.readLongLE()
                val size = vin.readIntLE()
                if (size <= 0 || size > 8 * 1024 * 1024) throw ScrcpyException("bad frame size $size")
                val frame = ByteArray(size); vin.readFully(frame)
                val pts = ptsFlags and 0x1FFFFFFFFFFFFFL
                val isConfig = ptsFlags and (1L shl 63) != 0L
                val idx = codec.dequeueInputBuffer(50_000)
                if (idx >= 0) {
                    val ib = codec.getInputBuffer(idx) ?: continue
                    ib.clear()
                    ib.put(frame)
                    val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                    codec.queueInputBuffer(idx, 0, frame.size, if (isConfig) 0 else pts, flags)
                }
                var out = codec.dequeueOutputBuffer(info, 0)
                while (out != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (out >= 0) {
                        codec.releaseOutputBuffer(out, true)
                    } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        val ow = f.getInteger(MediaFormat.KEY_WIDTH)
                        val oh = f.getInteger(MediaFormat.KEY_HEIGHT)
                        if (ow > 0 && oh > 0 && (ow to oh) != videoSize) {
                            videoSize = ow to oh
                            listener.onVideoSize(ow, oh)
                        }
                    }
                    out = codec.dequeueOutputBuffer(info, 0)
                }
            }
        } finally {
            try { codec.stop() } catch (e: Exception) { }
            try { codec.release() } catch (e: Exception) { }
        }
    }

    // ---------- control messages ----------

    /** Sends a touch as a scrcpy mouse event (pointerId -1, left button). */
    fun sendTouch(action: Int, x: Int, y: Int) {
        val cs = controlStream ?: return
        val (w, h) = videoSize
        if (w == 0 || h == 0) return
        controlExecutor.execute {
            try {
                val buf = ByteBuffer.allocate(1 + 1 + 8 + 4 + 4 + 2 + 2 + 2 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
                buf.put(2.toByte())                       // INJECT_TOUCH_EVENT
                buf.put(action.toByte())                  // ACTION_DOWN/MOVE/UP
                buf.putLong(-1L)                          // pointerId: mouse
                buf.putInt(x.coerceIn(0, w - 1))
                buf.putInt(y.coerceIn(0, h - 1))
                buf.putShort(w.toShort())
                buf.putShort(h.toShort())
                buf.putShort(0xFFFF.toShort())            // pressure
                buf.putInt(if (action != android.view.MotionEvent.ACTION_UP) 1 else 0) // actionButton
                buf.putInt(if (action != android.view.MotionEvent.ACTION_UP) 1 else 0) // buttons
                cs.write(buf.array())
            } catch (e: Exception) { }
        }
    }

    /** Injects an Android keycode (BACK=4, HOME=3, …). */
    fun sendKeycode(keycode: Int, down: Boolean) {
        val cs = controlStream ?: return
        controlExecutor.execute {
            try {
                val buf = ByteBuffer.allocate(1 + 1 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN)
                buf.put(0.toByte())                       // INJECT_KEYCODE
                buf.put(if (down) 1.toByte() else 0.toByte())
                buf.putInt(keycode)
                buf.putInt(0)                             // metaState
                buf.put(0.toByte())                       // repeat
                cs.write(buf.array())
            } catch (e: Exception) { }
        }
    }

    class ScrcpyException(msg: String) : Exception(msg)

    companion object {
        private const val TAG = "ScrcpyClient"
    }
}

/** Reassembles adb WRTE chunks into a continuous little-endian byte stream. */
class AdbBufferedInput(private val stream: AdbClient.Stream) {
    private var buf: ByteArray = ByteArray(0)
    private var pos = 0

    private fun ensure(n: Int) {
        while (buf.size - pos < n) {
            val chunk = stream.read()
            if (pos > 0) {
                buf = buf.copyOfRange(pos, buf.size)
                pos = 0
            }
            buf += chunk
        }
    }

    private fun take(n: Int): ByteArray {
        ensure(n)
        val out = buf.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun readFully(b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val n = minOf(b.size - off, 32 * 1024)
            System.arraycopy(take(n), 0, b, off, n)
            off += n
        }
    }

    fun skip(n: Int) {
        var left = n
        while (left > 0) left -= take(minOf(left, 32 * 1024)).size
    }

    fun readIntLE(): Int = ByteBuffer.wrap(take(4)).order(ByteOrder.LITTLE_ENDIAN).int

    fun readLongLE(): Long = ByteBuffer.wrap(take(8)).order(ByteOrder.LITTLE_ENDIAN).long
}
