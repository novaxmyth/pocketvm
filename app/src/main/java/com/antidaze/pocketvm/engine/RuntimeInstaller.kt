package com.antidaze.pocketvm.engine

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extracts the bundled QEMU engine (assets/runtime.zip, assembled in CI from
 * Termux GPL builds) into filesDir/runtime. Binaries are executed directly from
 * app data, which is only possible because the app targets SDK 28 (Termux model).
 */
object RuntimeInstaller {

    const val ASSET_ZIP = "runtime.zip"
    const val ASSET_INFO = "runtime_info.json"

    fun isArm64(): Boolean = Build.SUPPORTED_ABIS.any { it.startsWith("arm64") }

    fun bundledVersion(context: Context): String? = try {
        context.assets.open(ASSET_INFO).bufferedReader().use { JSONObject(it.readText()).optString("qemuVersion") }
    } catch (e: Exception) {
        null
    }

    fun isInstalled(context: Context, version: String?): Boolean {
        val root = File(context.filesDir, "runtime")
        if (version.isNullOrEmpty()) return false
        val marker = File(root, ".version")
        return marker.isFile && marker.readText().trim() == version &&
            File(root, "bin/qemu-system-aarch64").let { it.isFile && it.canExecute() }
    }

    /** Extracts the engine. Returns installed engine version. Runs on caller thread (use IO). */
    @Throws(Exception::class)
    fun install(context: Context): String {
        val root = File(context.filesDir, "runtime")
        val tmp = File(context.filesDir, "runtime.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        context.assets.open(ASSET_ZIP).use { raw ->
            ZipInputStream(raw.buffered(1 shl 19)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(tmp, e.name)
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it, 1 shl 16) }
                    }
                    zip.closeEntry()
                }
            }
        }
        // Make everything traversable and binaries executable.
        setPerms(tmp, dirs = true)
        setPerms(tmp, dirs = false)
        root.deleteRecursively()
        if (!tmp.renameTo(root)) {
            tmp.copyRecursively(root, overwrite = true)
            tmp.deleteRecursively()
        }
        val version = bundledVersion(context) ?: "unknown"
        File(root, ".version").writeText(version)
        return version
    }

    private fun setPerms(root: File, dirs: Boolean) {
        root.walkTopDown().forEach { f ->
            if (f.isDirectory == dirs) {
                if (dirs) {
                    f.setExecutable(true, false)
                    f.setReadable(true, false)
                } else {
                    val inBin = f.parentFile?.name == "bin"
                    f.setExecutable(inBin, false)
                    f.setReadable(true, false)
                    f.setWritable(true, true)
                }
            }
        }
    }

    fun qemuBinary(context: Context): File = File(context.filesDir, "runtime/bin/qemu-system-aarch64")
    fun libDir(context: Context): File = File(context.filesDir, "runtime/lib")
    fun shareDir(context: Context): File = File(context.filesDir, "runtime/share")
}
