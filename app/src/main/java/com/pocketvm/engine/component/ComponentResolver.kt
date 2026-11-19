package com.pocketvm.engine.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pocketvm.apps.ApkParser
import com.pocketvm.apps.ParsedApk
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.engine.io.VirtualFileSystem
import java.io.File

/**
 * Resolves guest intent filters to actual guest components.
 * Searches installed apps in the VM for matching Activities/Services/Receivers.
 */
class ComponentResolver private constructor() {

    data class ResolvedComponent(
        val packageName: String,
        val className: String,
        val componentName: ComponentName = ComponentName(packageName, className)
    )

    /**
     * Resolve the best matching Activity for the given intent within a VM.
     */
    suspend fun resolveActivity(
        context: Context,
        vmId: String,
        intent: Intent,
        db: PocketVmDatabase
    ): ResolvedComponent? {
        val candidates = queryActivities(context, vmId, intent, db)
        return candidates.firstOrNull()
    }

    /**
     * Resolve the best matching Service for the given intent within a VM.
     */
    suspend fun resolveService(
        vmId: String,
        intent: Intent,
        db: PocketVmDatabase
    ): ResolvedComponent? {
        // If the intent has an explicit component, resolve directly
        intent.component?.let { comp ->
            val app = db.installedAppDao().getByPackage(vmId, comp.packageName)
            if (app != null) {
                return ResolvedComponent(comp.packageName, comp.className)
            }
            return null
        }
        return null
    }

    /**
     * Query all Activities matching the intent within a VM's installed apps.
     */
    suspend fun queryActivities(
        context: Context,
        vmId: String,
        intent: Intent,
        db: PocketVmDatabase
    ): List<ResolvedComponent> {
        val results = mutableListOf<ResolvedComponent>()
        val installedApps = db.installedAppDao().getByVm(vmId)

        // Explicit component
        intent.component?.let { comp ->
            val app = installedApps.find { it.packageName == comp.packageName }
            if (app != null) {
                results.add(ResolvedComponent(comp.packageName, comp.className))
            }
            return results
        }

        // For implicit intents (action-based), we check if it's a MAIN/LAUNCHER intent
        val action = intent.action
        if (action == Intent.ACTION_MAIN) {
            val category = intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
            if (category) {
                // Return the launcher activity of each installed app
                for (app in installedApps) {
                    val apkFile = File(app.apkPath)
                    if (apkFile.exists()) {
                        try {
                            val parsed = ApkParser.parse(context, apkFile.absolutePath)
                            parsed?.mainActivity?.let { launcher ->
                                results.add(ResolvedComponent(app.packageName, launcher))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return results
    }

    companion object {
        @Volatile
        private var INSTANCE: ComponentResolver? = null

        fun get(): ComponentResolver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComponentResolver().also { INSTANCE = it }
            }
        }
    }
}
