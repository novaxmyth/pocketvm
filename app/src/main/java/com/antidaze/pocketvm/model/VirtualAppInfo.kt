package com.antidaze.pocketvm.model

import org.json.JSONArray
import org.json.JSONObject

data class VirtualAppInfo(
    val packageName: String,
    val appLabel: String,
    val versionCode: Long = 1L,
    val versionName: String = "1.0",
    val iconResId: Int = 0,
    val iconPath: String? = null,
    val apkPath: String? = null,
    val isSystemApp: Boolean = false,
    val targetSdk: Int = 34,
    val permissions: List<String> = emptyList(),
    val installedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("versionCode", versionCode)
        put("versionName", versionName)
        put("iconResId", iconResId)
        put("iconPath", iconPath ?: "")
        put("apkPath", apkPath ?: "")
        put("isSystemApp", isSystemApp)
        put("targetSdk", targetSdk)
        val permArr = JSONArray()
        permissions.forEach { permArr.put(it) }
        put("permissions", permArr)
        put("installedAt", installedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): VirtualAppInfo {
            val permList = mutableListOf<String>()
            json.optJSONArray("permissions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    permList.add(arr.optString(i))
                }
            }
            return VirtualAppInfo(
                packageName = json.optString("packageName"),
                appLabel = json.optString("appLabel"),
                versionCode = json.optLong("versionCode", 1L),
                versionName = json.optString("versionName", "1.0"),
                iconResId = json.optInt("iconResId", 0),
                iconPath = json.optString("iconPath").takeIf { it.isNotEmpty() },
                apkPath = json.optString("apkPath").takeIf { it.isNotEmpty() },
                isSystemApp = json.optBoolean("isSystemApp", false),
                targetSdk = json.optInt("targetSdk", 34),
                permissions = permList,
                installedAt = json.optLong("installedAt", System.currentTimeMillis())
            )
        }
    }
}

data class RecentTask(
    val taskId: String,
    val packageName: String,
    val appLabel: String,
    val iconResId: Int,
    val launchedAt: Long = System.currentTimeMillis()
)
