package com.antidaze.pocketvm.core

import android.content.Context
import com.antidaze.pocketvm.model.VirtualInstance
import com.antidaze.pocketvm.model.VmState
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VirtualInstanceManager private constructor(private val context: Context) {

    private val baseDir: File = File(context.filesDir, "vms").apply { mkdirs() }
    private val instancesMap = mutableMapOf<String, VirtualInstance>()

    init {
        loadAllFromDisk()
    }

    @Synchronized
    private fun loadAllFromDisk() {
        instancesMap.clear()
        val dirs = baseDir.listFiles { f -> f.isDirectory } ?: return
        for (dir in dirs) {
            val configFile = File(dir, "config.json")
            if (configFile.isFile) {
                try {
                    val jsonStr = configFile.readText()
                    val inst = VirtualInstance.fromJson(JSONObject(jsonStr))
                    inst.storageUsedBytes = calculateDirSize(dir)
                    instancesMap[inst.id] = inst
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Synchronized
    fun getAllInstances(): List<VirtualInstance> {
        return instancesMap.values.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun getInstance(id: String): VirtualInstance? {
        return instancesMap[id]
    }

    @Synchronized
    fun createInstance(instance: VirtualInstance): VirtualInstance {
        val dir = getVmDir(instance.id)
        dir.mkdirs()

        // Setup standard isolated Android directory hierarchy
        getVmSubdir(instance.id, "data/data").mkdirs()
        getVmSubdir(instance.id, "data/app").mkdirs()
        getVmSubdir(instance.id, "system").mkdirs()
        getVmSubdir(instance.id, "sdcard/Download").mkdirs()
        getVmSubdir(instance.id, "sdcard/DCIM").mkdirs()
        getVmSubdir(instance.id, "sdcard/Documents").mkdirs()
        getVmSubdir(instance.id, "sdcard/Pictures").mkdirs()
        getVmSubdir(instance.id, "sdcard/Shared").mkdirs()
        getVmSubdir(instance.id, "cache").mkdirs()

        // Provision system properties build.prop
        val buildPropFile = File(getVmSubdir(instance.id, "system"), "build.prop")
        buildPropFile.writeText(generateBuildProp(instance))

        // Save metadata
        saveInstance(instance)
        instancesMap[instance.id] = instance

        // Initialize virtual package manager for this instance
        VirtualPackageManager.get(context, instance.id).initializeDefaultSystemApps()

        instance.storageUsedBytes = calculateDirSize(dir)
        return instance
    }

    @Synchronized
    fun saveInstance(instance: VirtualInstance) {
        val dir = getVmDir(instance.id)
        dir.mkdirs()
        val configFile = File(dir, "config.json")
        configFile.writeText(instance.toJson().toString(2))
        instancesMap[instance.id] = instance
    }

    @Synchronized
    fun deleteInstance(id: String): Boolean {
        val dir = getVmDir(id)
        instancesMap.remove(id)
        return dir.deleteRecursively()
    }

    @Synchronized
    fun cloneInstance(sourceId: String, newName: String): VirtualInstance? {
        val source = getInstance(sourceId) ?: return null
        val newInstance = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            createdAt = System.currentTimeMillis(),
            lastLaunchedAt = 0L,
            state = VmState.STOPPED,
            deviceProfile = source.deviceProfile.copy(
                imei = source.deviceProfile.imei.dropLast(4) + (1000..9999).random(),
                androidId = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            )
        )

        val srcDir = getVmDir(sourceId)
        val dstDir = getVmDir(newInstance.id)
        dstDir.mkdirs()
        srcDir.copyRecursively(dstDir, overwrite = true)

        saveInstance(newInstance)
        instancesMap[newInstance.id] = newInstance
        return newInstance
    }

    fun getVmDir(id: String): File = File(baseDir, id)

    fun getVmSubdir(id: String, relativePath: String): File = File(getVmDir(id), relativePath)

    fun calculateStorageUsage(id: String): Long {
        val size = calculateDirSize(getVmDir(id))
        getInstance(id)?.let {
            it.storageUsedBytes = size
            saveInstance(it)
        }
        return size
    }

    fun cleanInstanceCache(id: String): Long {
        val cache = getVmSubdir(id, "cache")
        val before = calculateDirSize(cache)
        cache.deleteRecursively()
        cache.mkdirs()
        calculateStorageUsage(id)
        return before
    }

    fun exportInstance(id: String, targetZip: File, onProgress: ((Int) -> Unit)? = null): Boolean {
        val vmDir = getVmDir(id)
        if (!vmDir.isDirectory) return false

        try {
            val allFiles = mutableListOf<File>()
            fun collect(f: File) {
                if (f.isDirectory) {
                    f.listFiles()?.forEach { collect(it) }
                } else {
                    allFiles.add(f)
                }
            }
            collect(vmDir)

            val total = allFiles.size.coerceAtLeast(1)
            var count = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                for (file in allFiles) {
                    val relPath = file.relativeTo(vmDir).path
                    val entry = ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                    count++
                    onProgress?.invoke((count * 100) / total)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun importInstance(sourceZip: File): VirtualInstance? {
        if (!sourceZip.isFile) return null
        val tempId = java.util.UUID.randomUUID().toString()
        val targetDir = getVmDir(tempId)
        targetDir.mkdirs()

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val destFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos -> zis.copyTo(fos) }
                    }
                    entry = zis.nextEntry
                }
            }

            val configFile = File(targetDir, "config.json")
            if (configFile.isFile) {
                val json = JSONObject(configFile.readText())
                val inst = VirtualInstance.fromJson(json).copy(
                    id = tempId,
                    name = json.optString("name") + " (Imported)",
                    createdAt = System.currentTimeMillis()
                )
                saveInstance(inst)
                instancesMap[inst.id] = inst
                return inst
            } else {
                targetDir.deleteRecursively()
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            targetDir.deleteRecursively()
            return null
        }
    }

    private fun generateBuildProp(instance: VirtualInstance): String {
        return """
            # PocketVM Virtual Android Properties
            ro.build.version.release=${instance.osProfile.release}
            ro.build.version.sdk=${instance.osProfile.apiLevel}
            ro.build.id=${instance.osProfile.buildId}
            ro.build.version.security_patch=${instance.osProfile.securityPatch}
            ro.product.model=${instance.deviceProfile.model}
            ro.product.brand=${instance.deviceProfile.brand}
            ro.product.name=${instance.deviceProfile.device}
            ro.product.device=${instance.deviceProfile.device}
            ro.product.board=${instance.deviceProfile.board}
            ro.product.manufacturer=${instance.deviceProfile.manufacturer}
            ro.product.cpu.abi=arm64-v8a
            ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
            ro.sf.lcd_density=${instance.displayConfig.dpi}
            ro.pocketvm.instance_id=${instance.id}
            ro.pocketvm.engine=pocketvm-native-2.0
            ro.pocketvm.root=${instance.rootConfig.isRootEnabled}
        """.trimIndent()
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val stack = mutableListOf(dir)
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            val files = cur.listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory) {
                    stack.add(f)
                } else {
                    size += f.length()
                }
            }
        }
        return size
    }

    companion object {
        @Volatile
        private var instance: VirtualInstanceManager? = null

        fun get(context: Context): VirtualInstanceManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualInstanceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
