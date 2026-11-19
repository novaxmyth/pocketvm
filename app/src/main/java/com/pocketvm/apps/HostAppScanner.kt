package com.pocketvm.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans apps installed on the host device so they can be cloned into VMs.
 *
 * Uses Android's [PackageManager] to enumerate non-system applications and
 * expose their metadata (name, package, icon, APK path) for the UI.
 */
object HostAppScanner {

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Returns every user-installed (non-system) application on the host.
     *
     * @param context         Any valid [Context].
     * @param includeSystemApps If `true`, system apps are included as well.
     * @return A sorted list of [HostAppInfo] descriptors.
     */
    suspend fun getHostApps(
        context: Context,
        includeSystemApps: Boolean = false,
    ): List<HostAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }

        installedPackages
            .filter { pkg ->
                val appInfo = pkg.applicationInfo ?: return@filter false
                if (includeSystemApps) {
                    true
                } else {
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
            }
            .mapNotNull { pkg ->
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                try {
                    HostAppInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = pkg.packageName,
                        versionName = pkg.versionName ?: "unknown",
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkg.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkg.versionCode.toLong()
                        },
                        apkPath = appInfo.sourceDir,
                        splitApkPaths = appInfo.splitSourceDirs?.toList() ?: emptyList(),
                        icon = appInfo.loadIcon(pm),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Clones a host app into the given VM by copying its APK and running the
     * install flow through [ApkInstaller].
     *
     * @param context     Any valid [Context].
     * @param vmId        Target VM identifier.
     * @param packageName Package name of the host app to clone.
     * @throws ApkInstallException if the app cannot be found or installed.
     */
    suspend fun cloneToVm(
        context: Context,
        vmId: String,
        packageName: String,
    ): Unit = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val appInfo: ApplicationInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            throw ApkInstallException(
                "Host app $packageName not found: ${e.message}", e
            )
        }

        val apkPath = appInfo.sourceDir
            ?: throw ApkInstallException("APK path not available for $packageName")

        // Install the base APK into the VM.
        ApkInstaller.instance.installApk(vmId, apkPath)

        // If split APKs exist, copy them alongside (the installer's parse
        // step records split paths for the UI; actual split installation
        // is handled inside installApk when ApkParser detects them).
        appInfo.splitSourceDirs?.forEach { splitPath ->
            try {
                val parsed = ApkParser.parse(context, splitPath)
                // Split APKs share the same packageName; we simply copy them
                // into the VM's apk directory.
                if (parsed != null) {
                    val apkDir = com.pocketvm.engine.io.VirtualFileSystem.getApkDir(vmId, packageName)
                    val dest = java.io.File(apkDir, java.io.File(splitPath).name)
                    java.io.File(splitPath).copyTo(dest, overwrite = true)
                }
            } catch (_: Exception) {
                // Non-fatal: split may be architecture-specific and not needed.
            }
        }
    }

    /**
     * Checks whether a specific host app is already installed inside a VM.
     */
    suspend fun isAlreadyCloned(
        context: Context,
        vmId: String,
        packageName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = com.pocketvm.data.db.PocketVmDatabase.getInstance(context)
            .installedAppDao()
        dao.getByPackage(vmId, packageName) != null
    }
}

/**
 * Lightweight descriptor of an app installed on the host device.
 */
data class HostAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val splitApkPaths: List<String>,
    val icon: Drawable?,
    val isSystemApp: Boolean,
)
