package com.pocketvm.engine.hook

import android.util.Log
import com.pocketvm.engine.VirtualCore
import java.lang.reflect.Field
import java.lang.reflect.Proxy

/**
 * Core hooking engine.
 *
 * Uses Java reflection to obtain the singleton `IActivityManager` and `IPackageManager`
 * Binder interfaces from `ActivityThread` and replaces each one with a Java dynamic-proxy
 * wrapper ([ActivityManagerProxy] / [PackageManagerProxy]).
 *
 * The proxies intercept selected method calls so that guest apps living inside a PocketVM
 * container receive virtualised responses while the host app continues to work normally.
 */
class ServiceHookManager(private val core: VirtualCore) {

    companion object {
        private const val TAG = "ServiceHookManager"
    }

    /** The original (un-proxied) IActivityManager instance.  */
    @Volatile
    var originalActivityManager: Any? = null
        private set

    /** The original (un-proxied) IPackageManager instance. */
    @Volatile
    var originalPackageManager: Any? = null
        private set

    /** The VM id that hooks are currently installed for, or `null`. */
    @Volatile
    private var hookedVmId: Long? = null

    val isHooked: Boolean
        get() = hookedVmId != null

    // ── public API ───────────────────────────────────────────────────────

    /**
     * Install all system-level hooks for the given [vmId].
     */
    fun installHooks(vmId: Long) {
        if (hookedVmId == vmId) return
        try {
            hookActivityManager(vmId)
            hookPackageManager(vmId)
            hookedVmId = vmId
            Log.i(TAG, "Hooks installed for VM $vmId")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install hooks for VM $vmId", t)
            uninstallHooks()
        }
    }

    /**
     * Remove all hooks, restoring the original binder references.
     */
    fun uninstallHooks() {
        try {
            restoreActivityManager()
            restorePackageManager()
        } catch (t: Throwable) {
            Log.e(TAG, "Error restoring hooks", t)
        } finally {
            hookedVmId = null
            Log.i(TAG, "Hooks uninstalled")
        }
    }

    // ── ActivityManager hooking ──────────────────────────────────────────

    /**
     * Hooks `IActivityManager` by replacing the cached Singleton inside
     * `ActivityManager` (API 26+) or `ActivityManagerNative` (older APIs)
     * with a [Proxy] backed by [ActivityManagerProxy].
     */
    @Suppress("PrivateApi")
    fun hookActivityManager(vmId: Long) {
        try {
            // 1. Get the IActivityManagerSingleton field from android.app.ActivityManager
            val amClass = Class.forName("android.app.ActivityManager")
            val singletonField: Field = amClass.getDeclaredField("IActivityManagerSingleton")
            singletonField.isAccessible = true
            val singletonObj: Any = singletonField.get(null)
                ?: throw IllegalStateException("IActivityManagerSingleton is null")

            // 2. The Singleton<IActivityManager> stores its instance in `mInstance`
            val singletonClass = Class.forName("android.util.Singleton")
            val instanceField: Field = singletonClass.getDeclaredField("mInstance")
            instanceField.isAccessible = true

            val originalAm: Any = instanceField.get(singletonObj)
                ?: throw IllegalStateException("Singleton.mInstance (IActivityManager) is null")
            originalActivityManager = originalAm

            // 3. Determine the IActivityManager interface
            val iamInterface = Class.forName("android.app.IActivityManager")

            // 4. Create the dynamic proxy
            val proxy = Proxy.newProxyInstance(
                iamInterface.classLoader,
                arrayOf(iamInterface),
                ActivityManagerProxy(core, originalAm, vmId)
            )

            // 5. Replace the singleton instance with our proxy
            instanceField.set(singletonObj, proxy)
            Log.d(TAG, "IActivityManager hooked successfully")
        } catch (e: ClassNotFoundException) {
            // Fallback for pre-O: try ActivityManagerNative.gDefault
            hookActivityManagerLegacy(vmId)
        }
    }

    /**
     * Legacy path for API < 26 where the singleton lives in
     * `ActivityManagerNative.gDefault`.
     */
    @Suppress("PrivateApi")
    private fun hookActivityManagerLegacy(vmId: Long) {
        try {
            val amnClass = Class.forName("android.app.ActivityManagerNative")
            val gDefaultField: Field = amnClass.getDeclaredField("gDefault")
            gDefaultField.isAccessible = true
            val singletonObj: Any = gDefaultField.get(null)
                ?: throw IllegalStateException("gDefault is null")

            val singletonClass = Class.forName("android.util.Singleton")
            val instanceField: Field = singletonClass.getDeclaredField("mInstance")
            instanceField.isAccessible = true

            val originalAm: Any = instanceField.get(singletonObj)
                ?: throw IllegalStateException("Singleton.mInstance (legacy) is null")
            originalActivityManager = originalAm

            val iamInterface = Class.forName("android.app.IActivityManager")

            val proxy = Proxy.newProxyInstance(
                iamInterface.classLoader,
                arrayOf(iamInterface),
                ActivityManagerProxy(core, originalAm, vmId)
            )

            instanceField.set(singletonObj, proxy)
            Log.d(TAG, "IActivityManager hooked (legacy path)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hook IActivityManager (legacy)", e)
        }
    }

    @Suppress("PrivateApi")
    private fun restoreActivityManager() {
        val original = originalActivityManager ?: return
        try {
            val amClass = Class.forName("android.app.ActivityManager")
            val singletonField: Field = amClass.getDeclaredField("IActivityManagerSingleton")
            singletonField.isAccessible = true
            val singletonObj: Any = singletonField.get(null) ?: return

            val singletonClass = Class.forName("android.util.Singleton")
            val instanceField: Field = singletonClass.getDeclaredField("mInstance")
            instanceField.isAccessible = true
            instanceField.set(singletonObj, original)
        } catch (e: Exception) {
            try {
                val amnClass = Class.forName("android.app.ActivityManagerNative")
                val gDefaultField: Field = amnClass.getDeclaredField("gDefault")
                gDefaultField.isAccessible = true
                val singletonObj: Any = gDefaultField.get(null) ?: return
                val singletonClass = Class.forName("android.util.Singleton")
                val instanceField: Field = singletonClass.getDeclaredField("mInstance")
                instanceField.isAccessible = true
                instanceField.set(singletonObj, original)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to restore IActivityManager", e2)
            }
        }
        originalActivityManager = null
    }

    // ── PackageManager hooking ───────────────────────────────────────────

    /**
     * Hooks `IPackageManager` by replacing the `sPackageManager` field inside
     * `ActivityThread`.
     */
    @Suppress("PrivateApi")
    fun hookPackageManager(vmId: Long) {
        try {
            // 1. Get ActivityThread.currentActivityThread()
            val atClass = Class.forName("android.app.ActivityThread")
            val currentMethod = atClass.getDeclaredMethod("currentActivityThread")
            currentMethod.isAccessible = true
            val currentActivityThread: Any = currentMethod.invoke(null)
                ?: throw IllegalStateException("currentActivityThread is null")

            // 2. Get the sPackageManager static field
            val spmField: Field = atClass.getDeclaredField("sPackageManager")
            spmField.isAccessible = true
            val originalPm: Any = spmField.get(currentActivityThread)
                ?: throw IllegalStateException("sPackageManager is null")
            originalPackageManager = originalPm

            // 3. Determine the IPackageManager interface
            val ipmInterface = Class.forName("android.content.pm.IPackageManager")

            // 4. Create the dynamic proxy
            val proxy = Proxy.newProxyInstance(
                ipmInterface.classLoader,
                arrayOf(ipmInterface),
                PackageManagerProxy(core, originalPm, vmId)
            )

            // 5. Replace the static field
            spmField.set(currentActivityThread, proxy)

            // 6. Also replace inside the ApplicationPackageManager of the main context
            try {
                val pmField = core.getContext().packageManager.javaClass.getDeclaredField("mPM")
                pmField.isAccessible = true
                pmField.set(core.getContext().packageManager, proxy)
            } catch (_: Exception) {
                // Some ROMs name the field differently — non-fatal.
            }

            Log.d(TAG, "IPackageManager hooked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hook IPackageManager", e)
        }
    }

    @Suppress("PrivateApi")
    private fun restorePackageManager() {
        val original = originalPackageManager ?: return
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentMethod = atClass.getDeclaredMethod("currentActivityThread")
            currentMethod.isAccessible = true
            val currentActivityThread: Any = currentMethod.invoke(null) ?: return

            val spmField: Field = atClass.getDeclaredField("sPackageManager")
            spmField.isAccessible = true
            spmField.set(currentActivityThread, original)

            try {
                val pmField = core.getContext().packageManager.javaClass.getDeclaredField("mPM")
                pmField.isAccessible = true
                pmField.set(core.getContext().packageManager, original)
            } catch (_: Exception) { /* best effort */ }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore IPackageManager", e)
        }
        originalPackageManager = null
    }
}
