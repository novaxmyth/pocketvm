package com.pocketvm.engine.hook

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.pocketvm.engine.VirtualCore
import com.pocketvm.engine.stub.StubActivity
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * [InvocationHandler] that sits in front of the real `IActivityManager`.
 *
 * It intercepts calls coming from guest processes and either modifies arguments
 * (e.g. replacing a guest [Intent] with one that targets a host stub activity) or
 * filters results (e.g. hiding host processes from `getRunningAppProcesses`).
 *
 * Calls that originate from the host application or that target methods we do not
 * care about are forwarded unchanged.
 */
class ActivityManagerProxy(
    private val core: VirtualCore,
    private val original: Any,
    private val vmId: Long
) : InvocationHandler {

    companion object {
        private const val TAG = "AMProxy"

        /** Extra key used to stash the original guest intent inside the stub intent. */
        const val EXTRA_ORIGINAL_INTENT = "_pocketvm_original_intent"

        /** Extra key for the target VM id. */
        const val EXTRA_VM_ID = "_pocketvm_vm_id"

        /** Extra key for the target component class name. */
        const val EXTRA_TARGET_COMPONENT = "_pocketvm_target_component"

        /** Extra key for the target package name. */
        const val EXTRA_TARGET_PACKAGE = "_pocketvm_target_package"
    }

    // ── InvocationHandler ────────────────────────────────────────────────

    @Throws(Throwable::class)
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        try {
            return when (method.name) {
                "startActivity" -> handleStartActivity(method, args)
                "getRunningAppProcesses" -> handleGetRunningAppProcesses(method, args)
                "getContentProvider" -> handleGetContentProvider(method, args)
                else -> invokeOriginal(method, args)
            }
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    // ── startActivity ────────────────────────────────────────────────────

    /**
     * Intercepts `startActivity(...)`.
     *
     * If the caller is a guest process we wrap the real intent inside a stub-activity
     * intent so the system sees a component declared in our manifest.
     *
     * The standard AOSP signature (API 26+) is:
     * ```
     * int startActivity(IApplicationThread caller, String callingPackage,
     *     Intent intent, String resolvedType, IBinder resultTo,
     *     String resultWho, int requestCode, int flags, ProfilerInfo profilerInfo,
     *     Bundle options)
     * ```
     * The [Intent] is typically at index 2.
     */
    private fun handleStartActivity(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val intentIndex = args.indexOfFirst { it is Intent }
        if (intentIndex == -1) return invokeOriginal(method, args)

        val originalIntent = args[intentIndex] as Intent

        // Only intercept if the calling process belongs to a guest VM
        val callingPid = android.os.Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) {
            return invokeOriginal(method, args)
        }

        // Resolve target component inside the guest
        val targetComponent = originalIntent.component
        val targetPackage = targetComponent?.packageName ?: originalIntent.`package`

        // Build a stub intent that the real system can launch
        val stubIntent = StubActivity.createLaunchIntent(
            core.getContext(),
            vmId,
            targetComponent,
            originalIntent
        )

        // Replace the intent argument with our stub intent
        val mutableArgs = args.toMutableList()
        mutableArgs[intentIndex] = stubIntent

        // Also replace the callingPackage (usually index 1) with the host's package
        val callingPkgIndex = args.indexOfFirst { it is String }
        if (callingPkgIndex != -1) {
            mutableArgs[callingPkgIndex] = core.hostPackageName
        }

        Log.d(TAG, "startActivity intercepted: $targetPackage/${targetComponent?.className} → stub")
        return invokeOriginal(method, mutableArgs.toTypedArray())
    }

    // ── getRunningAppProcesses ───────────────────────────────────────────

    /**
     * Filters the process list so a guest app only sees processes belonging to
     * its own VM, preventing it from detecting the host or other VMs.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleGetRunningAppProcesses(method: Method, args: Array<out Any?>?): Any? {
        val result = invokeOriginal(method, args) ?: return null

        val callingPid = android.os.Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) {
            return result
        }

        // result is List<ActivityManager.RunningAppProcessInfo>
        val processList = result as? List<ActivityManager.RunningAppProcessInfo> ?: return result

        val processManager = core.getProcessManager()
        val guestPids = processManager.getRunningProcesses(vmId).map { it.pid }.toSet()

        return processList.filter { info ->
            info.pid in guestPids
        }.map { info ->
            // Map host UID → virtual UID
            val vRecord = processManager.getProcessByPid(info.pid)
            if (vRecord != null) {
                ActivityManager.RunningAppProcessInfo().apply {
                    processName = vRecord.processName
                    pid = vRecord.vpid
                    uid = vRecord.vuid
                    pkgList = arrayOf(vRecord.packageName)
                    importance = info.importance
                }
            } else {
                info
            }
        }
    }

    // ── getContentProvider ───────────────────────────────────────────────

    /**
     * Redirects content-provider acquisition to our stub providers when the
     * requested authority belongs to a guest-installed provider.
     */
    private fun handleGetContentProvider(method: Method, args: Array<out Any?>?): Any? {
        if (args == null) return invokeOriginal(method, args)

        val callingPid = android.os.Binder.getCallingPid()
        if (!core.isGuestProcess(callingPid)) {
            return invokeOriginal(method, args)
        }

        // The authority string is typically at index 1 or 2
        val authorityIndex = args.indexOfFirst { it is String && !it.contains("/") }
        if (authorityIndex != -1) {
            val authority = args[authorityIndex] as String
            val redirectedAuthority = ProviderHook.resolveGuestAuthority(vmId, authority)
            if (redirectedAuthority != null && redirectedAuthority != authority) {
                val mutableArgs = args.toMutableList()
                mutableArgs[authorityIndex] = redirectedAuthority
                Log.d(TAG, "getContentProvider redirected: $authority → $redirectedAuthority")
                return invokeOriginal(method, mutableArgs.toTypedArray())
            }
        }

        return invokeOriginal(method, args)
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun invokeOriginal(method: Method, args: Array<out Any?>?): Any? {
        return if (args == null) {
            method.invoke(original)
        } else {
            method.invoke(original, *args)
        }
    }
}
