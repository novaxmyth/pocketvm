package com.pocketvm.identity

import android.content.Context
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.db.VmInstanceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides mock GPS coordinates for VM instances.
 *
 * Locations are persisted in the [VmInstance] record (mockLatitude,
 * mockLongitude, isMockLocationEnabled). A set of well-known [PresetLocation]s
 * is exposed for quick selection in the UI.
 *
 * Singleton — call [MockLocationProvider.init] once during app startup.
 */
class MockLocationProvider private constructor(context: Context) {

    private val vmInstanceDao: VmInstanceDao =
        PocketVmDatabase.getInstance(context).vmInstanceDao()

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Sets the mock location for [vmId] and enables location mocking.
     */
    suspend fun setLocation(vmId: String, lat: Double, lng: Double): Unit =
        withContext(Dispatchers.IO) {
            val vm = vmInstanceDao.getById(vmId)
                ?: throw IllegalArgumentException("VM instance $vmId not found")

            val updated = vm.copy(
                mockLatitude = lat,
                mockLongitude = lng,
                isMockLocationEnabled = true,
            )
            vmInstanceDao.upsert(updated)
        }

    /**
     * Sets the mock location from a [PresetLocation] constant.
     */
    suspend fun setPresetLocation(vmId: String, preset: PresetLocation): Unit =
        setLocation(vmId, preset.latitude, preset.longitude)

    /**
     * Disables mock location for [vmId].
     */
    suspend fun disableLocation(vmId: String): Unit =
        withContext(Dispatchers.IO) {
            val vm = vmInstanceDao.getById(vmId)
                ?: throw IllegalArgumentException("VM instance $vmId not found")

            val updated = vm.copy(isMockLocationEnabled = false)
            vmInstanceDao.upsert(updated)
        }

    /**
     * Returns the current mock location for [vmId], or `null` if the VM
     * does not exist.
     */
    suspend fun getLocation(vmId: String): MockLocation? =
        withContext(Dispatchers.IO) {
            val vm = vmInstanceDao.getById(vmId) ?: return@withContext null

            MockLocation(
                latitude = vm.mockLatitude,
                longitude = vm.mockLongitude,
                isEnabled = vm.isMockLocationEnabled,
            )
        }

    /**
     * Returns the full list of built-in preset locations.
     */
    fun getPresets(): List<PresetLocation> = PresetLocation.ALL

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        @Volatile
        private var INSTANCE: MockLocationProvider? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = MockLocationProvider(context.applicationContext)
                    }
                }
            }
        }

        val instance: MockLocationProvider
            get() = INSTANCE
                ?: throw IllegalStateException(
                    "MockLocationProvider is not initialised. " +
                            "Call MockLocationProvider.init(context) first."
                )
    }
}

/**
 * Snapshot of a VM's mock location state.
 */
data class MockLocation(
    val latitude: Double,
    val longitude: Double,
    val isEnabled: Boolean,
)

/**
 * Well-known city locations for quick mock-location selection.
 */
enum class PresetLocation(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
) {
    SAN_FRANCISCO("San Francisco", 37.7749, -122.4194),
    TOKYO("Tokyo", 35.6762, 139.6503),
    LONDON("London", 51.5074, -0.1278),
    PARIS("Paris", 48.8566, 2.3522),
    NEW_YORK("New York", 40.7128, -74.0060),
    SYDNEY("Sydney", -33.8688, 151.2093);

    companion object {
        /** All presets as an immutable list, handy for UI spinners. */
        val ALL: List<PresetLocation> = entries.toList()
    }
}
