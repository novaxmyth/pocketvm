package com.antidaze.pocketvm.core

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.model.VirtualAppInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class VirtualPackageManager private constructor(
    private val context: Context,
    private val vmId: String
) {
    private val vmManager = VirtualInstanceManager.get(context)
    private val systemDir = vmManager.getVmSubdir(vmId, "system")
    private val packagesFile = File(systemDir, "packages.json")
    private val appMap = mutableMapOf<String, VirtualAppInfo>()

    init {
        loadPackages()
    }

    @Synchronized
    private fun loadPackages() {
        appMap.clear()
        if (packagesFile.isFile) {
            try {
                val json = JSONObject(packagesFile.readText())
                val arr = json.optJSONArray("packages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val appJson = arr.getJSONObject(i)
                    val app = VirtualAppInfo.fromJson(appJson)
                    appMap[app.packageName] = app
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (appMap.isEmpty()) {
            initializeDefaultSystemApps()
        }
    }

    @Synchronized
    fun initializeDefaultSystemApps() {
        val systemApps = listOf(
            VirtualAppInfo(
                packageName = "com.pocketvm.settings",
                appLabel = context.getString(R.string.app_settings),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_settings,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.files",
                appLabel = context.getString(R.string.app_files),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_folder,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.browser",
                appLabel = context.getString(R.string.app_browser),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_browser,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.terminal",
                appLabel = context.getString(R.string.app_terminal),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_terminal,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.installer",
                appLabel = context.getString(R.string.app_store),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_apps,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.sensor",
                appLabel = context.getString(R.string.app_sensor),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_sensors,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.su",
                appLabel = context.getString(R.string.app_su),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_shield,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.camera",
                appLabel = context.getString(R.string.app_camera),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_camera,
                isSystemApp = true,
                targetSdk = 34
            ),
            VirtualAppInfo(
                packageName = "com.pocketvm.deviceinfo",
                appLabel = context.getString(R.string.app_device_info),
                versionCode = 200L,
                versionName = "2.0",
                iconResId = R.drawable.ic_info,
                isSystemApp = true,
                targetSdk = 34
            )
        )

        for (app in systemApps) {
            appMap[app.packageName] = app
        }
        savePackages()
    }

    @Synchronized
    fun getInstalledApps(): List<VirtualAppInfo> {
        return appMap.values.sortedWith(
            compareBy<VirtualAppInfo> { !it.isSystemApp }
                .thenBy { it.appLabel.lowercase() }
        )
    }

    @Synchronized
    fun getApp(packageName: String): VirtualAppInfo? {
        return appMap[packageName]
    }

    @Synchronized
    fun installApk(apkFile: File): Result<VirtualAppInfo> {
        if (!apkFile.isFile) {
            return Result.failure(IllegalArgumentException("APK file not found: ${apkFile.path}"))
        }

        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
            ) ?: return Result.failure(IllegalStateException("Failed to parse APK archive"))

            val pkgName = pkgInfo.packageName
            val appInfo = pkgInfo.applicationInfo
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath

            val label = appInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkgName
            val iconDrawable = appInfo.loadIcon(pm)

            // Prepare destination directories
            val appDir = vmManager.getVmSubdir(vmId, "data/app/$pkgName")
            appDir.mkdirs()
            val destApk = File(appDir, "base.apk")
            apkFile.copyTo(destApk, overwrite = true)

            val dataDir = vmManager.getVmSubdir(vmId, "data/data/$pkgName")
            dataDir.mkdirs()
            File(dataDir, "files").mkdirs()
            File(dataDir, "cache").mkdirs()
            File(dataDir, "shared_prefs").mkdirs()

            // Save icon to appDir
            val iconFile = File(appDir, "icon.png")
            saveDrawableToPng(iconDrawable, iconFile)

            val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()

            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }

            val virtualApp = VirtualAppInfo(
                packageName = pkgName,
                appLabel = label,
                versionCode = vCode,
                versionName = pkgInfo.versionName ?: "1.0",
                iconResId = 0,
                iconPath = iconFile.absolutePath,
                apkPath = destApk.absolutePath,
                isSystemApp = false,
                targetSdk = appInfo.targetSdkVersion,
                permissions = perms,
                installedAt = System.currentTimeMillis()
            )

            appMap[pkgName] = virtualApp
            savePackages()
            vmManager.calculateStorageUsage(vmId)

            return Result.success(virtualApp)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    @Synchronized
    fun cloneFromHost(hostPackageName: String): Result<VirtualAppInfo> {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(hostPackageName, 0)
            val sourceApk = File(appInfo.sourceDir)
            if (!sourceApk.isFile) {
                return Result.failure(IllegalStateException("Host APK not accessible: ${sourceApk.path}"))
            }
            return installApk(sourceApk)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    @Synchronized
    fun uninstallApp(packageName: String): Boolean {
        val app = appMap[packageName] ?: return false
        if (app.isSystemApp) return false

        appMap.remove(packageName)
        savePackages()

        // Clean app storage
        vmManager.getVmSubdir(vmId, "data/app/$packageName").deleteRecursively()
        vmManager.getVmSubdir(vmId, "data/data/$packageName").deleteRecursively()
        vmManager.calculateStorageUsage(vmId)
        return true
    }

    @Synchronized
    private fun savePackages() {
        val root = JSONObject()
        val arr = JSONArray()
        for (app in appMap.values) {
            arr.put(app.toJson())
        }
        root.put("packages", arr)
        systemDir.mkdirs()
        packagesFile.writeText(root.toString(2))
    }

    private fun saveDrawableToPng(drawable: Drawable, file: File) {
        try {
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val instances = mutableMapOf<String, VirtualPackageManager>()

        @Synchronized
        fun get(context: Context, vmId: String): VirtualPackageManager {
            return instances.getOrPut(vmId) {
                VirtualPackageManager(context.applicationContext, vmId)
            }
        }
    }
}
