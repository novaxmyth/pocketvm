package com.pocketvm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an APK installed inside a virtual machine instance.
 * Tracks both the guest package info and where the APK file is stored on the host.
 */
@Entity(tableName = "installed_apps")
data class InstalledApp(
    @PrimaryKey
    val id: String, // vmId + ":" + packageName
    val vmId: String,
    val packageName: String,
    val appName: String,
    val versionName: String = "1.0",
    val versionCode: Long = 1,
    val apkPath: String, // Path to the APK in host storage (inside VM sandbox)
    val dataDir: String, // Sandboxed data directory path
    val installedAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long = 0L,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val sizeBytes: Long = 0L,
)
