package com.antidaze.pocketvm.engine

import android.util.Log
import com.antidaze.pocketvm.data.VmConfig
import com.antidaze.pocketvm.data.VmRepository
import java.io.File
import java.io.FileOutputStream

/**
 * Runs one QEMU (TCG, aarch64) child process per VM and owns its lifecycle.
 *
 * Display is exposed over VNC on 127.0.0.1 (the app's built-in RFB client
 * attaches to it); the serial console goes to serial.log for debugging.
 */
class QemuEngine(
    private val repo: VmRepository,
    private val cfg: VmConfig
) {
    private var process: Process? = null
    /** Set by VmForegroundService so exit handling is registered exactly once. */
    @Volatile var exitWatched: Boolean = false
    @Volatile var vncPort: Int = -1
        private set
    @Volatile var running: Boolean = false
        private set
    var onExited: ((code: Int) -> Unit)? = null
    var keepAwake: Boolean = true

    private val dir: File get() = repo.vmDir(cfg.id)
    private val logFile: File get() = repo.engineLogFile(cfg.id)

    fun start(context: android.content.Context): Boolean {
        check(!running) { "VM already running" }
        val bin = RuntimeInstaller.qemuBinary(context)
        require(bin.isFile && bin.canExecute()) { "Engine not installed" }

        dir.mkdirs()
        logFile.appendText("\n==== launch ${System.currentTimeMillis()} ====\n")

        val port = 5950 + (cfg.id.hashCode().mod(400))
        vncPort = port
        val args = buildArgs(context, port)

        val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
        pb.directory(dir)
        val env = pb.environment()
        env["LD_LIBRARY_PATH"] = RuntimeInstaller.libDir(context).absolutePath
        env["HOME"] = dir.absolutePath
        env["TMPDIR"] = dir.absolutePath
        env["PATH"] = "/system/bin:/system/xbin"
        env["QEMU_AUDIO_DRV"] = "none"

        Log.i(TAG, "exec: ${args.joinToString(" ")}")
        val p = try {
            pb.start()
        } catch (e: Exception) {
            logFile.appendText("failed to exec: $e\n")
            throw e
        }
        process = p
        running = true

        Thread({ pump(p.inputStream) }, "qemu-stdout").start()
        Thread({ pump(p.errorStream) }, "qemu-stderr").start()
        Thread({
            val code = try { p.waitFor() } catch (e: InterruptedException) { -1 }
            running = false
            logFile.appendText("exited with code $code\n")
            onExited?.invoke(code)
        }, "qemu-wait").start()

        return true
    }

    fun stop() {
        process?.destroy()
        val p = process ?: return
        val exited = try {
            p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }
        if (!exited) p.destroyForcibly()
    }

    private fun pump(stream: java.io.InputStream) {
        try {
            stream.bufferedReader().forEachLine { line ->
                synchronized(this) {
                    try { logFile.appendText("$line\n") } catch (e: Exception) { /* log dir gone */ }
                }
            }
        } catch (e: Exception) { /* process closed stream */ }
    }

    private fun buildArgs(context: android.content.Context, vncPort: Int): List<String> {
        val a = mutableListOf<String>()
        val data = repo.dataDiskFile(cfg.id)
        val system = cfg.systemImagePath

        a += "-no-user-config"
        a += listOf("-machine", "virt,highmem=on")
        a += listOf("-cpu", "max")
        a += listOf("-accel", "tcg,thread=multi")
        a += listOf("-smp", cfg.cpus.coerceIn(1, 8).toString())
        a += listOf("-m", cfg.ramMb.coerceIn(128, 8192).toString())
        a += listOf("-name", "pocketvm-${cfg.id}")

        if (!cfg.kernelPath.isNullOrEmpty()) {
            a += listOf("-kernel", cfg.kernelPath)
            if (!cfg.kernelCmdline.isNullOrEmpty()) a += listOf("-append", cfg.kernelCmdline)
        } else {
            a += listOf("-bios", File(RuntimeInstaller.shareDir(context), "QEMU_EFI.fd").absolutePath)
        }

        // Input: virtio-mmio keyboard + absolute pointer, driven by the VNC client.
        a += listOf("-device", "virtio-keyboard-device")
        a += listOf("-device", "virtio-tablet-device")

        if (system != null && File(system).isFile) {
            val fmt = if (system.endsWith(".qcow2")) "qcow2" else "raw"
            if (cfg.systemIsCdrom) {
                a += listOf("-drive", "file=$system,format=raw,if=none,id=cd0,readonly=on")
            } else {
                a += listOf("-drive", "file=$system,format=$fmt,if=none,id=hd0")
            }
            a += listOf("-device", "virtio-blk-device,drive=cd0")
        }
        if (data.isFile) {
            a += listOf("-drive", "file=${data.absolutePath},format=raw,if=none,id=hd1")
            a += listOf("-device", "virtio-blk-device,drive=hd1")
        }

        a += listOf("-device", "virtio-net-device,netdev=net0")
        a += listOf("-netdev", "user,id=net0")
        a += listOf("-device", "ramfb")
        a += listOf("-audiodev", "none,id=noaudio")
        a += listOf("-serial", "file:${repo.serialLogFile(cfg.id).absolutePath}")
        a += listOf("-monitor", "none")
        a += listOf("-vnc", "127.0.0.1:${vncPort - 5900}")
        a += listOf("-msg", "timestamp=on")
        return a
    }

    companion object {
        private const val TAG = "QemuEngine"
    }
}
