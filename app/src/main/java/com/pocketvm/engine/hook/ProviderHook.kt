package com.pocketvm.engine.hook

import android.util.Log
import com.pocketvm.engine.VirtualCore
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper that maps guest ContentProvider authorities to sandboxed stub-provider authorities.
 *
 * When a guest app requests a [android.content.ContentProvider] through the system,
 * [ActivityManagerProxy] calls into this class to determine whether the authority belongs
 * to a provider installed inside the VM. If it does, the authority is rewritten to point
 * to one of the numbered [com.pocketvm.engine.stub.StubContentProvider] declarations in
 * the host manifest so the system can find and instantiate a real component.
 *
 * The stub provider, upon receiving calls, then delegates to the actual guest provider
 * code loaded via the guest's [dalvik.system.DexClassLoader].
 */
object ProviderHook {

    private const val TAG = "ProviderHook"

    /**
     * Maximum number of stub content-provider slots declared in the host manifest.
     * Must match the number of `StubProviderN` inner classes in
     * [com.pocketvm.engine.stub.StubContentProvider].
     */
    const val MAX_STUB_PROVIDERS = 10

    /** Prefix used for stub provider authorities in the manifest. */
    const val STUB_AUTHORITY_PREFIX = "com.pocketvm.stub.provider"

    /**
     * Key = `"$vmId:$guestAuthority"`, Value = stub authority string
     * (e.g. `"com.pocketvm.stub.provider3"`).
     */
    private val authorityMap = ConcurrentHashMap<String, String>()

    /**
     * Reverse map: stub authority → `"$vmId:$guestAuthority"`.
     */
    private val reverseMap = ConcurrentHashMap<String, String>()

    /** Next stub slot index to allocate. */
    @Volatile
    private var nextSlotIndex = 0

    // ── public API ───────────────────────────────────────────────────────

    /**
     * Register a guest authority so that future look-ups will redirect to a stub slot.
     *
     * @param vmId          The virtual machine that owns the provider.
     * @param guestAuthority The authority string declared in the guest's manifest.
     * @return The stub authority allocated to this guest authority, or `null` if no
     *         slots remain.
     */
    @Synchronized
    fun registerGuestAuthority(vmId: Long, guestAuthority: String): String? {
        val key = "$vmId:$guestAuthority"
        authorityMap[key]?.let { return it }

        if (nextSlotIndex >= MAX_STUB_PROVIDERS) {
            Log.w(TAG, "No more stub provider slots available (max=$MAX_STUB_PROVIDERS)")
            return null
        }

        val stubAuthority = "$STUB_AUTHORITY_PREFIX$nextSlotIndex"
        authorityMap[key] = stubAuthority
        reverseMap[stubAuthority] = key
        nextSlotIndex++

        Log.d(TAG, "Registered guest authority: $guestAuthority (vm=$vmId) → $stubAuthority")
        return stubAuthority
    }

    /**
     * Resolve a guest authority to its assigned stub authority.
     *
     * @return The stub authority if the guest authority has been registered, otherwise `null`.
     */
    fun resolveGuestAuthority(vmId: Long, guestAuthority: String): String? {
        return authorityMap["$vmId:$guestAuthority"]
    }

    /**
     * Given a stub authority, return the original guest authority and VM id.
     *
     * @return A [Pair] of `(vmId, guestAuthority)` or `null` if the stub authority
     *         is not mapped.
     */
    fun resolveStubAuthority(stubAuthority: String): Pair<Long, String>? {
        val key = reverseMap[stubAuthority] ?: return null
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return null
        val vmId = parts[0].toLongOrNull() ?: return null
        return vmId to parts[1]
    }

    /**
     * Unregister all authority mappings for the given [vmId], freeing their stub slots.
     *
     * Note: Slots are **not** reclaimed (the index keeps growing). For long-running
     * sessions that exceed [MAX_STUB_PROVIDERS] consider a more sophisticated allocator.
     */
    @Synchronized
    fun unregisterVm(vmId: Long) {
        val prefix = "$vmId:"
        val keysToRemove = authorityMap.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            val stub = authorityMap.remove(key)
            if (stub != null) {
                reverseMap.remove(stub)
            }
        }
        Log.d(TAG, "Unregistered ${keysToRemove.size} authorities for VM $vmId")
    }

    /**
     * Clears **all** authority mappings. Useful during engine shutdown.
     */
    @Synchronized
    fun clearAll() {
        authorityMap.clear()
        reverseMap.clear()
        nextSlotIndex = 0
        Log.d(TAG, "All provider authority mappings cleared")
    }
}
