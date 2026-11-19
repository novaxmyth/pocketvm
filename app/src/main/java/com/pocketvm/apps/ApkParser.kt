package com.pocketvm.apps

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Parses APK files to extract package metadata.
 *
 * Uses Android's [PackageManager.getPackageArchiveInfo] under the hood. The
 * result is returned as a [ParsedApk] data class containing everything needed
 * to install the APK into a VM and display it in the UI.
 *
 * Split APK bundles are handled gracefully: the base APK is parsed normally,
 * and additional split paths can be recorded alongside it via [parseSplitApks].
 */
object ApkParser {

    // Flags we request from PackageManager so we get components & permissions.
    @Suppress("DEPRECATION")
    private const val PARSE_FLAGS: Int =
        PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_META_DATA

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Parses a single APK file and returns a [ParsedApk], or `null` if the
     * file is not a valid APK.
     */
    suspend fun parse(context: Context, apkPath: String): ParsedApk? =
        withContext(Dispatchers.IO) {
            val file = File(apkPath)
            if (!file.exists() || !file.isFile) return@withContext null

            try {
                val pm = context.packageManager
                val info: PackageInfo? = pm.getPackageArchiveInfo(apkPath, PARSE_FLAGS)
                    ?: return@withContext null

                info?.let { buildParsedApk(it, apkPath, pm) }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Parses a base APK together with optional split APKs.
     *
     * @param baseApkPath Path to the base APK.
     * @param splitPaths  Paths to any split APKs (config splits, feature modules, etc.).
     * @return A [ParsedApk] with [ParsedApk.splitApkPaths] populated, or `null`
     *         if the base APK is invalid.
     */
    suspend fun parseSplitApks(
        context: Context,
        baseApkPath: String,
        splitPaths: List<String> = emptyList(),
    ): ParsedApk? = withContext(Dispatchers.IO) {
        val baseParsed = parse(context, baseApkPath) ?: return@withContext null

        val validSplits = splitPaths.filter { path ->
            val f = File(path)
            f.exists() && f.isFile
        }

        baseParsed.copy(splitApkPaths = validSplits)
    }

    // ------------------------------------------------------------------ //
    //  Internals
    // ------------------------------------------------------------------ //

    @Suppress("DEPRECATION")
    private fun buildParsedApk(
        info: PackageInfo,
        apkPath: String,
        pm: PackageManager,
    ): ParsedApk {
        // ApplicationInfo needs sourceDir set for label/icon resolution.
        info.applicationInfo?.sourceDir = apkPath

        val appLabel: String = try {
            info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
        } catch (_: Exception) {
            info.packageName
        }

        val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

        val permissions: List<String> = info.requestedPermissions?.toList() ?: emptyList()

        val activities: List<String> =
            info.activities?.mapNotNull { it.name } ?: emptyList()

        val services: List<String> =
            info.services?.mapNotNull { it.name } ?: emptyList()

        val receivers: List<String> =
            info.receivers?.mapNotNull { it.name } ?: emptyList()

        val providers: List<String> =
            info.providers?.mapNotNull { it.name } ?: emptyList()

        val minSdk: Int = info.applicationInfo?.minSdkVersion ?: 1

        val targetSdk: Int = info.applicationInfo?.targetSdkVersion ?: 0

        return ParsedApk(
            packageName = info.packageName,
            appLabel = appLabel,
            versionName = info.versionName ?: "unknown",
            versionCode = versionCode,
            apkPath = apkPath,
            splitApkPaths = emptyList(),
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
        )
    }
}

/**
 * Fully-parsed metadata extracted from an APK file.
 */
data class ParsedApk(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val splitApkPaths: List<String>,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
) {
    /** `true` when the APK is part of a split/bundle install. */
    val isSplitApk: Boolean get() = splitApkPaths.isNotEmpty()

    /** Returns the main launcher activity, or `null` if none is obvious. */
    val mainActivity: String?
        get() = activities.firstOrNull { name ->
            name.contains("MainActivity", ignoreCase = true) ||
                    name.contains("LauncherActivity", ignoreCase = true) ||
                    name.contains(".Main", ignoreCase = true)
        } ?: activities.firstOrNull()
}
