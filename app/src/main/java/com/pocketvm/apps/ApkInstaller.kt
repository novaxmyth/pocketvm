package com.pocketvm.apps

import android.content.Context
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.db.InstalledAppDao
import com.pocketvm.data.model.InstalledApp
import com.pocketvm.engine.io.VirtualFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs and uninstalls APK files inside a virtual-machine instance.
 *
 * The install flow:
 * 1. Parses the APK to extract metadata ([ApkParser]).
 * 2. Copies the APK into the VM's `data/app/<packageName>/` directory.
 * 3. Creates the `data/data/<packageName>/` directory for runtime data.
 * 4. Writes an [InstalledApp] record to the database.
 *
 * Uninstall reverses all three steps.
 *
 * Singleton — call [ApkInstaller.init] once during app startup.
 */
class ApkInstaller private constructor(private val context: Context) {

    private val installedAppDao: InstalledAppDao =
        PocketVmDatabase.getInstance(context).installedAppDao()

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Full install flow for a single APK file into [vmId].
     *
     * @param vmId        Target VM identifier.
     * @param apkFilePath Absolute path to the APK on the host file system.
     * @return The [InstalledApp] record that was persisted to the database.
     * @throws ApkInstallException if parsing or file operations fail.
     */
    suspend fun installApk(vmId: String, apkFilePath: String): InstalledApp =
        withContext(Dispatchers.IO) {
            // 1. Parse
            val parsed = ApkParser.parse(context, apkFilePath)
                ?: throw ApkInstallException("Failed to parse APK: $apkFilePath")

            // 2. Copy APK to VM's app directory
            val apkDir = File(VirtualFileSystem.getApkDir(vmId, parsed.packageName))
            if (!apkDir.exists()) apkDir.mkdirs()

            val destApk = File(apkDir, "base.apk")
            try {
                File(apkFilePath).copyTo(destApk, overwrite = true)
            } catch (e: Exception) {
                throw ApkInstallException(
                    "Failed to copy APK to VM storage: ${e.message}", e
                )
            }

            // Copy split APKs if present.
            parsed.splitApkPaths.forEachIndexed { index, splitPath ->
                try {
                    val splitDest = File(apkDir, "split_$index.apk")
                    File(splitPath).copyTo(splitDest, overwrite = true)
                } catch (e: Exception) {
                    // Non-fatal: log and continue.
                }
            }

            // 3. Create data directory
            val dataDir = File(
                VirtualFileSystem.getAppDataDir(vmId, parsed.packageName)
            )
            if (!dataDir.exists()) dataDir.mkdirs()

            // Create standard sub-directories.
            arrayOf("cache", "code_cache", "files", "shared_prefs", "databases")
                .forEach { sub -> File(dataDir, sub).mkdirs() }

            // 4. Persist to DB
            val installedApp = InstalledApp(
                id = "${vmId}_${parsed.packageName}",
                vmId = vmId,
                packageName = parsed.packageName,
                appName = parsed.appLabel,
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                apkPath = destApk.absolutePath,
                dataDir = dataDir.absolutePath,
            )

            installedAppDao.upsert(installedApp)

            // Update VM storage stats (best-effort).
            try {
                val db = PocketVmDatabase.getInstance(context)
                val sizeBytes = VirtualFileSystem.calculateVmSize(vmId)
                db.vmInstanceDao().updateStorageUsed(vmId, sizeBytes)
            } catch (_: Exception) { }

            installedApp
        }

    /**
     * Uninstalls an app from the VM, removing the APK, data directory, and
     * the database record.
     *
     * @param vmId        Target VM identifier.
     * @param packageName Package name of the app to remove.
     * @throws ApkInstallException if the app is not found in the database.
     */
    suspend fun uninstallApp(vmId: String, packageName: String): Unit =
        withContext(Dispatchers.IO) {
            val record = installedAppDao.getByPackage(vmId, packageName)
                ?: throw ApkInstallException(
                    "App $packageName is not installed in VM $vmId"
                )

            // Remove APK directory.
            try {
                val apkDir = File(VirtualFileSystem.getApkDir(vmId, packageName))
                if (apkDir.exists()) apkDir.deleteRecursively()
            } catch (e: Exception) {
                // Best-effort cleanup.
            }

            // Remove data directory.
            try {
                val dataDir = File(
                    VirtualFileSystem.getAppDataDir(vmId, packageName)
                )
                if (dataDir.exists()) dataDir.deleteRecursively()
            } catch (e: Exception) {
                // Best-effort cleanup.
            }

            // Remove DB record.
            installedAppDao.delete(record)

            // Update storage stats.
            try {
                val db = PocketVmDatabase.getInstance(context)
                val sizeBytes = VirtualFileSystem.calculateVmSize(vmId)
                db.vmInstanceDao().updateStorageUsed(vmId, sizeBytes)
            } catch (_: Exception) { }
        }

    /**
     * Returns a reactive [Flow] of apps installed in [vmId].
     */
    fun getInstalledApps(vmId: String): Flow<List<InstalledApp>> =
        installedAppDao.observeByVm(vmId)

    /**
     * Returns the current snapshot list of apps installed in [vmId].
     */
    suspend fun getInstalledAppsSnapshot(vmId: String): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            // Collect the first emission from the Flow.
            val list = mutableListOf<InstalledApp>()
            installedAppDao.observeByVm(vmId).collect {
                list.addAll(it)
                return@collect // We only need the first emission in the snapshot.
            }
            list
        }

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        @Volatile
        private var INSTANCE: ApkInstaller? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = ApkInstaller(context.applicationContext)
                    }
                }
            }
        }

        val instance: ApkInstaller
            get() = INSTANCE
                ?: throw IllegalStateException(
                    "ApkInstaller is not initialised. " +
                            "Call ApkInstaller.init(context) first."
                )
    }
}

/**
 * Exception thrown when an APK install or uninstall operation fails.
 */
class ApkInstallException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
