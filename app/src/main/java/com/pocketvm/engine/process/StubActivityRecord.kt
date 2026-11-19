package com.pocketvm.engine.process

import android.content.Intent

/**
 * Records the mapping between a stub Activity class (declared in the host manifest)
 * and the actual guest Activity it is hosting at runtime.
 *
 * The [StubActivityPool] (or equivalent allocator) maintains a list of these records
 * to know which stub slots are occupied and what guest component each one is running.
 *
 * @property stubIndex          Index of the stub class (0–49), corresponding to
 *                              `StubActivity.Stub0` … `StubActivity.Stub49`.
 * @property vmId               The virtual-machine ID that owns this guest activity.
 * @property packageName        Package name of the guest app.
 * @property targetActivityName Fully-qualified class name of the guest [Activity].
 * @property launchIntent       The original [Intent] that was used to start the guest
 *                              activity (before wrapping in a stub intent).
 * @property processRecord      The [VProcessRecord] of the process hosting this activity,
 *                              or `null` if not yet assigned.
 */
data class StubActivityRecord(
    val stubIndex: Int,
    val vmId: String,
    val packageName: String,
    val targetActivityName: String,
    val launchIntent: Intent,
    val processRecord: VProcessRecord? = null
) {
    companion object {
        /** Total number of stub activity slots available in the manifest pool. */
        const val MAX_STUB_COUNT = 50

        /**
         * Returns the simple class name (e.g. `"Stub12"`) for a given [index].
         */
        fun stubClassName(index: Int): String {
            require(index in 0 until MAX_STUB_COUNT) {
                "Stub index $index out of range [0, $MAX_STUB_COUNT)"
            }
            return "Stub$index"
        }
    }

    /**
     * Whether this record is still associated with a living process.
     */
    val isAlive: Boolean
        get() = processRecord != null
}
