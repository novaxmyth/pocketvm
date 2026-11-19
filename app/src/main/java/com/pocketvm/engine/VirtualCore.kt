package com.pocketvm.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.model.VmState
import com.pocketvm.engine.hook.ServiceHookManager
import com.pocketvm.engine.io.VirtualFileSystem
import com.pocketvm.engine.process.VProcessManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton core of the PocketVM virtualisation engine.
 *
 * Must be initialised exactly once (typically from [Application.onCreate]) before
 * any other engine component is used.
 */
object VirtualCore {

    private val initialized = AtomicBoolean(false)

    @SuppressLint("StaticFieldLeak") // We hold the Application context, which is safe.
    private lateinit var appContext: Context
    private lateinit var database: PocketVmDatabase
    private lateinit var hookManager: ServiceHookManager
    private lateinit var processManager: VProcessManager
    private lateinit var virtualFileSystem: VirtualFileSystem

    /** Host application's package name. */
    lateinit var hostPackageName: String
        private set

    /** Host application info cached at init time. */
    lateinit var hostApplicationInfo: ApplicationInfo
        private set

    /** PID of the main host process. */
    var hostPid: Int = -1
        private set

    /** UID of the main host process. */
    var hostUid: Int = -1
        private set

    /**
     * Map of currently-running VM IDs → their [VmState].
     * Updated by the process manager and lifecycle hooks.
     */
    private val runningVms = ConcurrentHashMap<Long, VmState>()

    // ── public API ───────────────────────────────────────────────────────

    val isInitialized: Boolean
        get() = initialized.get()

    /**
     * Initialise the engine.
     *
     * @param context Any context — the application context is extracted automatically.
     * @throws IllegalStateException if called more than once.
     */
    fun init(context: Context) {
        check(!initialized.getAndSet(true)) { "VirtualCore is already initialised." }

        appContext = context.applicationContext
        hostPackageName = appContext.packageName
        hostPid = Process.myPid()
        hostUid = Process.myUid()

        hostApplicationInfo = try {
            appContext.packageManager.getApplicationInfo(hostPackageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException("Cannot find host application info", e)
        }

        database = PocketVmDatabase.getInstance(appContext)
        virtualFileSystem = VirtualFileSystem(appContext)
        processManager = VProcessManager()
        hookManager = ServiceHookManager(this)
    }

    /**
     * Returns the application-scoped [Context].
     */
    fun getContext(): Context {
        ensureInitialised()
        return appContext
    }

    /**
     * Returns the Room database instance.
     */
    fun getDatabase(): PocketVmDatabase {
        ensureInitialised()
        return database
    }

    /**
     * Returns the [ServiceHookManager] responsible for installing / removing system hooks.
     */
    fun getHookManager(): ServiceHookManager {
        ensureInitialised()
        return hookManager
    }

    /**
     * Returns the [VProcessManager] that tracks virtual processes.
     */
    fun getProcessManager(): VProcessManager {
        ensureInitialised()
        return processManager
    }

    /**
     * Returns the [VirtualFileSystem] for per-VM sandboxed storage.
     */
    fun getFileSystem(): VirtualFileSystem {
        ensureInitialised()
        return virtualFileSystem
    }

    // ── running VM bookkeeping ───────────────────────────────────────────

    /**
     * Marks a VM as running with the given [state].
     */
    fun setVmState(vmId: Long, state: VmState) {
        if (state == VmState.STOPPED) {
            runningVms.remove(vmId)
        } else {
            runningVms[vmId] = state
        }
    }

    /**
     * Returns the current state of [vmId], defaulting to [VmState.STOPPED] if unknown.
     */
    fun getVmState(vmId: Long): VmState =
        runningVms.getOrDefault(vmId, VmState.STOPPED)

    /**
     * Returns all VM IDs that are not in the [VmState.STOPPED] state.
     */
    fun getRunningVmIds(): Set<Long> = runningVms.keys.toSet()

    /**
     * Returns `true` when at least one VM is currently in [VmState.RUNNING].
     */
    fun hasRunningVm(): Boolean =
        runningVms.values.any { it == VmState.RUNNING }

    /**
     * Checks whether a given [pid] belongs to a guest process (as opposed to the host).
     */
    fun isGuestProcess(pid: Int): Boolean =
        processManager.isGuestPid(pid)

    // ── internals ────────────────────────────────────────────────────────

    private fun ensureInitialised() {
        check(initialized.get()) { "VirtualCore.init(context) has not been called." }
    }
}
