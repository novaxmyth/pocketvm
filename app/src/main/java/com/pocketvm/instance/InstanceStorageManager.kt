package com.pocketvm.instance

import android.os.Environment
import com.pocketvm.engine.io.VirtualFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

/**
 * Manages the shared-storage bridge between the host device and guest VMs.
 *
 * A single shared folder on external storage (`/PocketVM/Shared/`) acts as a
 * drop-box: files placed there can be synced *into* any VM and files produced
 * inside a VM can be exported back to the host.
 *
 * Singleton — access via [InstanceStorageManager.instance].
 */
class InstanceStorageManager private constructor() {

    /** Root shared directory on the host's external storage. */
    private val sharedDir: File by lazy {
        val dir = File(
            Environment.getExternalStorageDirectory(),
            "$POCKETVM_DIR/$SHARED_DIR"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Copies every file in the host shared folder into the VM's
     * `sdcard/Shared/` directory, creating it if necessary.
     */
    suspend fun syncToVm(vmId: String): Int = withContext(Dispatchers.IO) {
        val vmSharedDir = File(VirtualFileSystem.getSdcardDir(vmId), SHARED_DIR)
        if (!vmSharedDir.exists()) {
            vmSharedDir.mkdirs()
        }

        val files = sharedDir.listFiles() ?: emptyArray()
        var count = 0

        for (file in files) {
            if (file.isFile) {
                try {
                    val dest = File(vmSharedDir, file.name)
                    file.copyTo(dest, overwrite = true)
                    count++
                } catch (e: Exception) {
                    // Skip individual file failures, continue with the rest.
                }
            } else if (file.isDirectory) {
                try {
                    val dest = File(vmSharedDir, file.name)
                    file.copyRecursively(dest, overwrite = true)
                    count++
                } catch (e: Exception) {
                    // Skip.
                }
            }
        }

        count
    }

    /**
     * Exports a single file from inside the VM's sdcard to the host shared
     * folder.
     *
     * @param vmId     Target VM identifier.
     * @param filePath Relative path inside the VM's sdcard (e.g. `DCIM/photo.jpg`).
     * @return The [File] on the host where the data was written.
     * @throws IllegalArgumentException if the source file does not exist.
     */
    suspend fun exportFromVm(vmId: String, filePath: String): File =
        withContext(Dispatchers.IO) {
            val sdcard = VirtualFileSystem.getSdcardDir(vmId)
            val source = File(sdcard, filePath)

            require(source.exists() && source.isFile) {
                "Source file does not exist in VM $vmId: $filePath"
            }

            val dest = File(sharedDir, source.name)
            source.copyTo(dest, overwrite = true)
            dest
        }

    /**
     * Lists all files currently sitting in the host shared folder.
     */
    suspend fun getSharedFiles(): List<SharedFileInfo> = withContext(Dispatchers.IO) {
        val files = sharedDir.listFiles() ?: emptyArray()
        files.map { file ->
            SharedFileInfo(
                name = file.name,
                absolutePath = file.absolutePath,
                sizeBytes = if (file.isFile) file.length() else file.walkTopDown().sumOf { it.length() },
                isDirectory = file.isDirectory,
                lastModified = file.lastModified(),
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Returns a human-readable string describing the total disk usage for the
     * given VM (e.g. `"128.5 MB"`).
     */
    suspend fun getVmStorageUsage(vmId: String): String = withContext(Dispatchers.IO) {
        try {
            val bytes = VirtualFileSystem.calculateVmSize(vmId)
            formatFileSize(bytes)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Returns the host shared directory [File], creating it if needed.
     */
    fun getSharedDirectory(): File = sharedDir

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.lastIndex)
        val formatter = DecimalFormat("#,##0.#")
        return "${formatter.format(bytes / Math.pow(1024.0, safeGroup.toDouble()))} ${units[safeGroup]}"
    }

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        private const val POCKETVM_DIR = "PocketVM"
        private const val SHARED_DIR = "Shared"

        val instance: InstanceStorageManager by lazy { InstanceStorageManager() }

        fun get(): InstanceStorageManager = instance
    }
}

/**
 * Lightweight descriptor for a file in the shared folder.
 */
data class SharedFileInfo(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
)
