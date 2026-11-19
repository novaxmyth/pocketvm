package com.pocketvm.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.model.VmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launches guest apps inside a running VM.
 *
 * The launch mechanism wraps the guest [Intent] inside a host-side
 * `StubActivity` intent. PocketVM's runtime engine intercepts the stub and
 * redirects execution into the virtualised environment.
 *
 * Singleton — call [AppLauncher.init] once during app startup.
 */
class AppLauncher private constructor(private val context: Context) {

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Launches the main/launcher activity of [packageName] inside [vmId].
     *
     * 1. Verifies the VM is in [VmState.RUNNING].
     * 2. Looks up the installed app record.
     * 3. Resolves the main activity from the parsed APK.
     * 4. Wraps it into a stub-activity intent and starts it.
     *
     * @throws AppLaunchException if the VM is not running, the app is not
     *         installed, or no launchable activity is found.
     */
    suspend fun launchApp(
        context: Context,
        vmId: String,
        packageName: String,
    ): Unit = withContext(Dispatchers.Main) {
        val db = PocketVmDatabase.getInstance(context)

        // Verify VM state.
        val vm = db.vmInstanceDao().getById(vmId)
            ?: throw AppLaunchException("VM instance $vmId not found")

        if (vm.state != VmState.RUNNING) {
            throw AppLaunchException(
                "Cannot launch app: VM $vmId is in state ${vm.state} (expected RUNNING)"
            )
        }

        // Look up the installed app.
        val installedApp = db.installedAppDao().getByPackage(vmId, packageName)
            ?: throw AppLaunchException(
                "App $packageName is not installed in VM $vmId"
            )

        // Resolve main activity from the APK.
        val parsed = withContext(Dispatchers.IO) {
            ApkParser.parse(context, installedApp.apkPath)
        } ?: throw AppLaunchException(
            "Failed to parse APK for $packageName"
        )

        val mainActivity = parsed.mainActivity
            ?: throw AppLaunchException(
                "No launchable activity found in $packageName"
            )

        val component = ComponentName(packageName, mainActivity)
        launchActivity(context, vmId, component)
    }

    /**
     * Launches a specific [componentName] (activity) inside [vmId].
     *
     * This is the low-level entry point; prefer [launchApp] for the
     * standard "open app" use case.
     *
     * @throws AppLaunchException if the VM is not running.
     */
    suspend fun launchActivity(
        context: Context,
        vmId: String,
        componentName: ComponentName,
    ): Unit = withContext(Dispatchers.Main) {
        val db = PocketVmDatabase.getInstance(context)

        // Verify VM state.
        val vm = db.vmInstanceDao().getById(vmId)
            ?: throw AppLaunchException("VM instance $vmId not found")

        if (vm.state != VmState.RUNNING) {
            throw AppLaunchException(
                "Cannot launch activity: VM $vmId is in state ${vm.state} (expected RUNNING)"
            )
        }

        // Build the guest intent.
        val guestIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = componentName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Wrap it into the stub-activity intent.
        val stubIntent = createStubIntent(context, vmId, guestIntent)
        context.startActivity(stubIntent)
    }

    // ------------------------------------------------------------------ //
    //  Stub wrapping
    // ------------------------------------------------------------------ //

    /**
     * Wraps a [guestIntent] inside a host-side `StubActivity` intent that
     * PocketVM's engine will intercept at runtime.
     */
    private fun createStubIntent(
        context: Context,
        vmId: String,
        guestIntent: Intent,
    ): Intent {
        val stubComponent = ComponentName(
            context.packageName,
            STUB_ACTIVITY_CLASS,
        )

        return Intent().apply {
            component = stubComponent
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Extras consumed by the PocketVM engine.
            putExtra(EXTRA_VM_ID, vmId)
            putExtra(EXTRA_GUEST_PKG, guestIntent.component?.packageName)
            putExtra(EXTRA_GUEST_ACTIVITY, guestIntent.component?.className)
            putExtra(EXTRA_GUEST_INTENT, guestIntent)
        }
    }

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        /**
         * Fully-qualified class name of the host stub activity that proxies
         * guest launches. This activity must be declared in the host manifest.
         */
        private const val STUB_ACTIVITY_CLASS =
            "com.pocketvm.engine.stub.StubActivity"

        // Intent extras understood by the stub / engine.
        private const val EXTRA_VM_ID = "pocketvm.extra.VM_ID"
        private const val EXTRA_GUEST_PKG = "pocketvm.extra.GUEST_PKG"
        private const val EXTRA_GUEST_ACTIVITY = "pocketvm.extra.GUEST_ACTIVITY"
        private const val EXTRA_GUEST_INTENT = "pocketvm.extra.GUEST_INTENT"

        @Volatile
        private var INSTANCE: AppLauncher? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = AppLauncher(context.applicationContext)
                    }
                }
            }
        }

        val instance: AppLauncher
            get() = INSTANCE
                ?: throw IllegalStateException(
                    "AppLauncher is not initialised. " +
                            "Call AppLauncher.init(context) first."
                )
    }
}

/**
 * Exception thrown when an app launch operation fails.
 */
class AppLaunchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
