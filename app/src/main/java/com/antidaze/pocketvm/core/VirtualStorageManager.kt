package com.antidaze.pocketvm.core

import android.content.Context
import android.os.Environment
import java.io.File

class VirtualStorageManager private constructor(private val context: Context) {

    val sharedFolder: File
        get() {
            val publicDir = File(Environment.getExternalStorageDirectory(), "PocketVM/Shared")
            return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                publicDir.apply { mkdirs() }
            } else {
                File(context.filesDir, "shared").apply { mkdirs() }
            }
        }

    fun listSharedFiles(): List<File> {
        return sharedFolder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun importFileToShared(sourceFile: File): File? {
        if (!sourceFile.isFile) return null
        val target = File(sharedFolder, sourceFile.name)
        sourceFile.copyTo(target, overwrite = true)
        return target
    }

    fun syncSharedFolderToVm(vmId: String) {
        val vmSharedDir = VirtualInstanceManager.get(context).getVmSubdir(vmId, "sdcard/Shared")
        vmSharedDir.mkdirs()
        sharedFolder.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.copyTo(File(vmSharedDir, file.name), overwrite = true)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: VirtualStorageManager? = null

        fun get(context: Context): VirtualStorageManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualStorageManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
