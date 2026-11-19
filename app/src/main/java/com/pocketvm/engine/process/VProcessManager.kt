package com.pocketvm.engine.process

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages virtual process records for guest apps.
 *
 * Every time a guest app is launched inside a VM, a [VProcessRecord] is created
 * that maps a host-level PID to a virtual PID/UID pair visible to the guest.
 * This manager also exposes lookup helpers used by the hooking layer to decide
 * whether a given calling PID belongs to a guest process.
 */
class VProcessManager {

    companion object {
        private const val TAG = "VProcessManager"

        /**
         * Base value for virtual UIDs. Each (vmId, packageName) pair is assigned
         * a unique UID starting from this value.
         */
        private const val VUID_BASE = 10_000

        /**
         * Base value for virtual PIDs. Incremented for every new process record.
         */
        private const val VPID_BASE = 1_000
    }

    /** Atomic counter for virtual PIDs. */
    private val vpidCounter = AtomicInteger(VPID_BASE)

    /** Atomic counter for virtual UIDs. */
    private val vuidCounter = AtomicInteger(VUID_BASE)

    /** All live process records, keyed by their virtual PID (vpid). */
    private val processesByVpid = ConcurrentHashMap<Int, VProcessRecord>()

    /** Fast reverse map: host PID → vpid. */
    private val hostPidToVpid = ConcurrentHashMap<Int, Int>()

    /** Stable UID assignments: `"$vmId:$packageName"` → virtual UID. */
    private val uidAssignments = ConcurrentHashMap<String, Int>()

    // ── public API ───────────────────────────────────────────────────────

    /**
     * Creates a new virtual process record for a guest app launch.
     *
     * @param vmId        VM that owns the process.
     * @param packageName Package being launched.
     * @param hostPid     The real PID assigned by the OS (e.g. from `Process.myPid()`
     *                    of the stub process).
     * @param processName Optional process name override; defaults to [packageName].
     * @return The newly created [VProcessRecord].
     */
    fun startProcess(
        vmId: String,
        packageName: String,
        hostPid: Int = android.os.Process.myPid(),
        processName: String = packageName
    ): VProcessRecord {
        val vpid = vpidCounter.getAndIncrement()
        val vuid = getOrAssignUid(vmId, packageName)

        val record = VProcessRecord(
            vpid = vpid,
            vuid = vuid,
            pid = hostPid,
            uid = android.os.Process.myUid(),
            vmId = vmId,
            packageName = packageName,
            processName = processName,
            startTimeMillis = System.currentTimeMillis()
        )

        processesByVpid[vpid] = record
        hostPidToVpid[hostPid] = vpid

        Log.i(TAG, "startProcess: $packageName [vpid=$vpid, vuid=$vuid, hostPid=$hostPid, vm=$vmId]")
        return record
    }

    /**
     * Kills (removes) a virtual process by its virtual PID.
     *
     * @return `true` if a process with that vpid existed and was removed.
     */
    fun killProcess(vpid: Int): Boolean {
        val record = processesByVpid.remove(vpid) ?: return false
        hostPidToVpid.remove(record.pid)
        Log.i(TAG, "killProcess: vpid=$vpid (${record.packageName})")
        return true
    }

    /**
     * Kills all processes belonging to [vmId].
     */
    fun killAllProcesses(vmId: String) {
        val toRemove = processesByVpid.values.filter { it.vmId == vmId }
        for (record in toRemove) {
            processesByVpid.remove(record.vpid)
            hostPidToVpid.remove(record.pid)
        }
        Log.i(TAG, "killAllProcesses: vm=$vmId, killed=${toRemove.size}")
    }

    /**
     * Returns all running [VProcessRecord]s for the given [vmId].
     */
    fun getRunningProcesses(vmId: String): List<VProcessRecord> =
        processesByVpid.values.filter { it.vmId == vmId }

    /**
     * Returns the [VProcessRecord] for a specific package running inside [vmId],
     * or `null` if no such process exists.
     */
    fun getProcessForPackage(vmId: String, packageName: String): VProcessRecord? =
        processesByVpid.values.firstOrNull { it.vmId == vmId && it.packageName == packageName }

    /**
     * Returns the [VProcessRecord] mapped to a given host [pid], or `null`.
     */
    fun getProcessByPid(pid: Int): VProcessRecord? {
        val vpid = hostPidToVpid[pid] ?: return null
        return processesByVpid[vpid]
    }

    /**
     * Returns `true` if the given host [pid] belongs to a guest process.
     */
    fun isGuestPid(pid: Int): Boolean = hostPidToVpid.containsKey(pid)

    /**
     * Returns or assigns a stable virtual UID for the (vmId, packageName) pair.
     */
    fun getOrAssignUid(vmId: String, packageName: String): Int {
        val key = "$vmId:$packageName"
        return uidAssignments.getOrPut(key) { vuidCounter.getAndIncrement() }
    }

    /**
     * Returns the total number of running virtual processes across all VMs.
     */
    fun totalProcessCount(): Int = processesByVpid.size

    /**
     * Returns all running [VProcessRecord]s across every VM.
     */
    fun allRunningProcesses(): List<VProcessRecord> = processesByVpid.values.toList()
}

/**
 * Represents a single virtual process inside a PocketVM container.
 */
data class VProcessRecord(
    /** Virtual PID seen by the guest. */
    val vpid: Int,
    /** Virtual UID seen by the guest. */
    val vuid: Int,
    /** Actual host-level PID. */
    val pid: Int,
    /** Actual host-level UID. */
    val uid: Int,
    /** ID of the owning VM. */
    val vmId: String,
    /** Package name of the guest app. */
    val packageName: String,
    /** Process name (may differ from package for multi-process apps). */
    val processName: String,
    /** Wall-clock time when the process was started. */
    val startTimeMillis: Long
)
