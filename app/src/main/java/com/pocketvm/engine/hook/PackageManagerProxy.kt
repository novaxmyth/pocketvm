package com.pocketvm.engine.hook

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Binder
import android.util.Log
import com.pocketvm.engine.VirtualCore
import com.pocketvm.engine.component.ComponentResolver
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * [InvocationHandler] for the `IPackageManager` dynamic proxy.
 *
 * Intercepts package-query methods so that guest apps receive information
 * about packages installed inside their VM while host-originated queries
 * pass through to the real implementation.
 */
class PackageManagerProxy(
    private val core: VirtualCore,
    private val original: Any,
    private val vmId: Long
) : InvocationHandler {

    companion object {
        private const val TAG = "PMProxy"
    }

    private val componentResolver: ComponentResolver by lazy { ComponentResolver(core) }

    @Throws(Throwable::class)
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        try {
            return when (method.name) {
                "getPackageInfo" -> handleGetPackageInfo(method, args)
                "getApplicationInfo" -> handleGetApplicationInfo(method, args)
                "resolveIntent" -> handleResolveIntent(method, args)
                "queryIntentActivities" -> handleQueryIntentActivities(method, args)
                else -> invokeOriginal(method, args)
            }
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    // ── getPackageInfo ───────────────────────────────────────────────────

    /**
     * Signature: `PackageInfo getPackageInfo(String packageName, int flags, int userId)`
     *
     * When a guest process asks for a package that is installed inside the VM,
     * we build and return synthetic [PackageInfo] from our records. Otherwise
     * we fall through to the real PM.
     */
    private fun handleGetPackageInfo(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val callingPid = Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) return invokeOriginal(method, args)

        val packageName = args.firstOrNull { it is String } as? String
            ?: return invokeOriginal(method, args)

        val installedAppDao = core.getDatabase().installedAppDao()
        val app = installedAppDao.getByPackage(vmId, packageName)
            ?: return invokeOriginal(method, args)

        val pi = PackageInfo().apply {
            this.packageName = app.packageName
            versionName = app.versionName
            @Suppress("DEPRECATION")
            versionCode = app.versionCode.toInt()
            firstInstallTime = app.installedAt
            lastUpdateTime = app.installedAt
            applicationInfo = buildApplicationInfo(app.packageName, app.appName, app.apkPath, app.dataDir)
        }
        Log.d(TAG, "getPackageInfo → guest: ${app.packageName}")
        return pi
    }

    // ── getApplicationInfo ───────────────────────────────────────────────

    /**
     * Signature: `ApplicationInfo getApplicationInfo(String packageName, int flags, int userId)`
     */
    private fun handleGetApplicationInfo(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val callingPid = Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) return invokeOriginal(method, args)

        val packageName = args.firstOrNull { it is String } as? String
            ?: return invokeOriginal(method, args)

        val installedAppDao = core.getDatabase().installedAppDao()
        val app = installedAppDao.getByPackage(vmId, packageName)
            ?: return invokeOriginal(method, args)

        Log.d(TAG, "getApplicationInfo → guest: ${app.packageName}")
        return buildApplicationInfo(app.packageName, app.appName, app.apkPath, app.dataDir)
    }

    // ── resolveIntent ────────────────────────────────────────────────────

    /**
     * Signature: `ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId)`
     */
    private fun handleResolveIntent(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val callingPid = Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) return invokeOriginal(method, args)

        val intent = args.firstOrNull { it is Intent } as? Intent
            ?: return invokeOriginal(method, args)

        val resolved = componentResolver.resolveActivity(vmId, intent)
        if (resolved != null) {
            Log.d(TAG, "resolveIntent → guest: ${resolved.activityInfo?.name}")
            return resolved
        }

        return invokeOriginal(method, args)
    }

    // ── queryIntentActivities ────────────────────────────────────────────

    /**
     * Signature: `ParceledListSlice queryIntentActivities(Intent intent, String resolvedType, int flags, int userId)`
     *
     * We return a plain list; the caller (ApplicationPackageManager) usually wraps it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleQueryIntentActivities(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val callingPid = Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) return invokeOriginal(method, args)

        val intent = args.firstOrNull { it is Intent } as? Intent
            ?: return invokeOriginal(method, args)

        val guestResults = componentResolver.queryActivities(vmId, intent)
        if (guestResults.isNotEmpty()) {
            Log.d(TAG, "queryIntentActivities → guest: ${guestResults.size} results")

            // Try to wrap in ParceledListSlice for AIDL compat
            return try {
                val plsClass = Class.forName("android.content.pm.ParceledListSlice")
                val ctor = plsClass.getConstructor(List::class.java)
                ctor.newInstance(guestResults)
            } catch (_: Exception) {
                guestResults
            }
        }

        return invokeOriginal(method, args)
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a synthetic [ApplicationInfo] for a guest package.
     */
    private fun buildApplicationInfo(
        packageName: String,
        label: String?,
        apkPath: String?,
        dataDir: String?
    ): ApplicationInfo {
        return ApplicationInfo().apply {
            this.packageName = packageName
            this.nonLocalizedLabel = label ?: packageName
            this.sourceDir = apkPath ?: ""
            this.dataDir = dataDir ?: core.getFileSystem().getAppDataDir(vmId, packageName).absolutePath
            this.nativeLibraryDir = this.dataDir + "/lib"
            this.flags = ApplicationInfo.FLAG_INSTALLED
            this.uid = core.getProcessManager().getOrAssignUid(vmId, packageName)
            this.targetSdkVersion = android.os.Build.VERSION.SDK_INT
        }
    }

    private fun invokeOriginal(method: Method, args: Array<out Any?>?): Any? {
        return if (args == null) {
            method.invoke(original)
        } else {
            method.invoke(original, *args)
        }
    }
}
