package com.pocketvm.engine.io

import android.content.Context
import java.io.File

/**
 * Manages per-VM sandboxed file system.
 * Each VM instance gets its own isolated directory tree under the app's internal storage.
 */
class VirtualFileSystem private constructor(private val context: Context) {

    private val baseDir: File = File(context.filesDir, "vms").apply { mkdirs() }

    fun getVmDir(vmId: String): File =
        File(baseDir, vmId).apply { mkdirs() }

    fun getAppDataDir(vmId: String, packageName: String): File =
        File(getVmDir(vmId), "data/data/$packageName").apply { mkdirs() }

    fun getApkDir(vmId: String, packageName: String): File =
        File(getVmDir(vmId), "data/app/$packageName").apply { mkdirs() }

    fun getSdcardDir(vmId: String): File =
        File(getVmDir(vmId), "sdcard").apply { mkdirs() }

    fun getSystemDir(vmId: String): File =
        File(getVmDir(vmId), "system").apply { mkdirs() }

    fun getCacheDir(vmId: String): File =
        File(getVmDir(vmId), "cache").apply { mkdirs() }

    fun setupVmDirectories(vmId: String) {
        val vmDir = getVmDir(vmId)
        listOf(
            "data/data", "data/app", "system",
            "sdcard/Download", "sdcard/DCIM", "sdcard/Documents",
            "sdcard/Pictures", "sdcard/Shared", "cache"
        ).forEach { File(vmDir, it).mkdirs() }
    }

    fun calculateVmSize(vmId: String): Long {
        val vmDir = File(baseDir, vmId)
        if (!vmDir.exists()) return 0L
        return vmDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun deleteVmStorage(vmId: String): Boolean {
        val vmDir = File(baseDir, vmId)
        return if (vmDir.exists()) vmDir.deleteRecursively() else true
    }

    fun clearCache(vmId: String) {
        val cache = File(getVmDir(vmId), "cache")
        if (cache.exists()) cache.deleteRecursively()
        cache.mkdirs()
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    companion object {
        @Volatile
        private var INSTANCE: VirtualFileSystem? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = VirtualFileSystem(context.applicationContext)
                    }
                }
            }
        }

        fun get(): VirtualFileSystem =
            INSTANCE ?: throw IllegalStateException("VirtualFileSystem not initialized. Call init() first.")
    }
}
