package com.pocketvm.instance

import android.content.Context
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.db.VmInstanceDao
import com.pocketvm.data.model.DeviceProfile
import com.pocketvm.data.model.VmInstance
import com.pocketvm.data.model.VmState
import com.pocketvm.engine.io.VirtualFileSystem
import com.pocketvm.identity.DeviceIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * High-level manager for VM instances.
 *
 * Provides lifecycle operations (create, delete, boot, shutdown, clone) and
 * observation of the full instance list via Room-backed [Flow].
 *
 * Singleton — call [InstanceManager.init] with an application [Context] before
 * accessing [InstanceManager.instance].
 */
class InstanceManager private constructor(context: Context) {

    private val database: PocketVmDatabase = PocketVmDatabase.getInstance(context)
    private val vmInstanceDao: VmInstanceDao = database.vmInstanceDao()

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Creates a brand-new VM instance pre-configured from [profile].
     *
     * 1. Builds a [VmInstance] with a fresh UUID.
     * 2. Persists it to the database.
     * 3. Bootstraps the file-system directories the VM will need at runtime
     *    (system, sdcard, app data).
     *
     * @param name       Human-readable display name chosen by the user.
     * @param profile    [DeviceProfile] that determines spoofed device props.
     * @param ramMb      Amount of RAM (in MiB) to allocate.
     * @param rootEnabled Whether the guest OS should expose root access.
     * @return The newly-created [VmInstance].
     */
    suspend fun createInstance(
        name: String,
        profile: DeviceProfile,
        ramMb: Int = 2048,
        rootEnabled: Boolean = false,
    ): VmInstance = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()

        val vmInstance = VmInstance(
            id = id,
            name = name,
            profileName = profile.name,
            spoofedModel = profile.model,
            spoofedManufacturer = profile.manufacturer,
            spoofedBrand = profile.brand,
            spoofedDevice = profile.device,
            spoofedFingerprint = profile.fingerprint,
            spoofedAndroidId = generateHexString(16),
            spoofedImei = generateImei(),
            spoofedSerial = generateSerial(),
            isRootEnabled = rootEnabled,
            ramMb = ramMb,
            state = VmState.STOPPED,
            createdAt = System.currentTimeMillis(),
            lastBootedAt = 0L,
            storageUsedBytes = 0L,
            mockLatitude = 0.0,
            mockLongitude = 0.0,
            isMockLocationEnabled = false,
        )

        vmInstanceDao.upsert(vmInstance)

        // Bootstrap file-system directories.
        try {
            VirtualFileSystem.getSystemDir(id)
            VirtualFileSystem.getSdcardDir(id)
        } catch (e: Exception) {
            // Best-effort — dirs may already exist or VFS may defer creation.
        }

        vmInstance
    }

    /**
     * Permanently deletes the instance identified by [id], including all
     * on-disk storage managed by [VirtualFileSystem].
     */
    suspend fun deleteInstance(id: String): Unit = withContext(Dispatchers.IO) {
        val existing = vmInstanceDao.getById(id)
            ?: throw IllegalArgumentException("VM instance $id not found")

        // Ensure the VM is stopped before deletion.
        if (existing.state == VmState.RUNNING || existing.state == VmState.BOOTING) {
            vmInstanceDao.updateState(id, VmState.STOPPED)
        }

        vmInstanceDao.delete(existing)

        try {
            VirtualFileSystem.deleteVmStorage(id)
        } catch (e: Exception) {
            // Storage may already have been cleaned up.
        }
    }

    /**
     * Boots the VM instance through the BOOTING → RUNNING lifecycle.
     *
     * A small artificial delay simulates real boot-up time so observers see
     * the intermediate [VmState.BOOTING] state.
     */
    suspend fun bootInstance(id: String): Unit = withContext(Dispatchers.IO) {
        val existing = vmInstanceDao.getById(id)
            ?: throw IllegalArgumentException("VM instance $id not found")

        require(existing.state == VmState.STOPPED || existing.state == VmState.ERROR) {
            "Cannot boot instance in state ${existing.state}"
        }

        vmInstanceDao.updateState(id, VmState.BOOTING)
        vmInstanceDao.updateLastBooted(id, System.currentTimeMillis())

        // Simulate boot delay so the UI can display a boot animation.
        delay(BOOT_DELAY_MS)

        vmInstanceDao.updateState(id, VmState.RUNNING)

        // Refresh storage stats post-boot.
        try {
            val sizeBytes = VirtualFileSystem.calculateVmSize(id)
            vmInstanceDao.updateStorageUsed(id, sizeBytes)
        } catch (_: Exception) { }
    }

    /**
     * Gracefully shuts down the VM instance, transitioning it to [VmState.STOPPED].
     */
    suspend fun shutdownInstance(id: String): Unit = withContext(Dispatchers.IO) {
        val existing = vmInstanceDao.getById(id)
            ?: throw IllegalArgumentException("VM instance $id not found")

        require(existing.state == VmState.RUNNING || existing.state == VmState.PAUSED) {
            "Cannot shut down instance in state ${existing.state}"
        }

        vmInstanceDao.updateState(id, VmState.STOPPED)

        // Persist final storage usage.
        try {
            val sizeBytes = VirtualFileSystem.calculateVmSize(id)
            vmInstanceDao.updateStorageUsed(id, sizeBytes)
        } catch (_: Exception) { }
    }

    /**
     * Creates a full clone of the source instance under [newName], assigning
     * it a new UUID. The clone starts in [VmState.STOPPED].
     *
     * @return The newly-created clone [VmInstance].
     */
    suspend fun cloneInstance(sourceId: String, newName: String): VmInstance =
        withContext(Dispatchers.IO) {
            val source = vmInstanceDao.getById(sourceId)
                ?: throw IllegalArgumentException("Source VM instance $sourceId not found")

            val cloneId = UUID.randomUUID().toString()

            val clone = VmInstance(
                id = cloneId,
                name = newName,
                profileName = source.profileName,
                spoofedModel = source.spoofedModel,
                spoofedManufacturer = source.spoofedManufacturer,
                spoofedBrand = source.spoofedBrand,
                spoofedDevice = source.spoofedDevice,
                spoofedFingerprint = source.spoofedFingerprint,
                spoofedAndroidId = generateHexString(16),   // unique identity
                spoofedImei = generateImei(),
                spoofedSerial = generateSerial(),
                isRootEnabled = source.isRootEnabled,
                ramMb = source.ramMb,
                state = VmState.STOPPED,
                createdAt = System.currentTimeMillis(),
                lastBootedAt = 0L,
                storageUsedBytes = 0L,
                mockLatitude = source.mockLatitude,
                mockLongitude = source.mockLongitude,
                isMockLocationEnabled = source.isMockLocationEnabled,
            )

            vmInstanceDao.upsert(clone)

            // Bootstrap file-system directories for the clone.
            try {
                VirtualFileSystem.getSystemDir(cloneId)
                VirtualFileSystem.getSdcardDir(cloneId)
            } catch (_: Exception) { }

            clone
        }

    /**
     * Observes the full list of VM instances as a reactive [Flow].
     */
    fun observeInstances(): Flow<List<VmInstance>> = vmInstanceDao.observeAll()

    /**
     * Returns the [VmInstance] with the given [id], or `null` if it doesn't exist.
     */
    suspend fun getInstance(id: String): VmInstance? = withContext(Dispatchers.IO) {
        vmInstanceDao.getById(id)
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private fun generateHexString(length: Int): String {
        val chars = "0123456789abcdef"
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Generates a plausible 15-digit IMEI with a valid Luhn check digit.
     */
    private fun generateImei(): String {
        val base = (1..14).map { (0..9).random() }.joinToString("")
        val checkDigit = luhnCheckDigit(base)
        return "$base$checkDigit"
    }

    private fun luhnCheckDigit(number: String): Int {
        var sum = 0
        var alternate = true
        for (i in number.indices.reversed()) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return (10 - (sum % 10)) % 10
    }

    private fun generateSerial(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        private const val BOOT_DELAY_MS = 1500L

        @Volatile
        private var INSTANCE: InstanceManager? = null

        /**
         * Initialises the singleton. Must be called once — typically in
         * `Application.onCreate()`.
         */
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = InstanceManager(context.applicationContext)
                    }
                }
            }
        }

        /**
         * Returns the initialised singleton.
         *
         * @throws IllegalStateException if [init] has not been called.
         */
        val instance: InstanceManager
            get() = INSTANCE
                ?: throw IllegalStateException(
                    "InstanceManager is not initialised. Call InstanceManager.init(context) first."
                )

        fun get(): InstanceManager = instance
    }
}
