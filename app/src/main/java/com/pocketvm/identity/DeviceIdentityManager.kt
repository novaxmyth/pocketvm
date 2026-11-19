package com.pocketvm.identity

import android.content.Context
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.db.VmInstanceDao
import com.pocketvm.data.model.DeviceProfile
import com.pocketvm.data.model.VmInstance
import com.pocketvm.engine.io.VirtualFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages per-VM device identity spoofing.
 *
 * Each VM carries its own set of device identifiers (IMEI, Android ID, serial
 * number, MAC address, Build properties). This manager can generate a fresh
 * identity set from a [DeviceProfile], persist it to the VM's `build.prop`
 * file, read it back, and selectively regenerate individual fields.
 *
 * Singleton — call [DeviceIdentityManager.init] once before using [instance].
 */
class DeviceIdentityManager private constructor(context: Context) {

    private val vmInstanceDao: VmInstanceDao =
        PocketVmDatabase.getInstance(context).vmInstanceDao()

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Generates a complete [DeviceIdentity] derived from [profile] with
     * randomised unique identifiers.
     */
    fun generateIdentity(profile: DeviceProfile): DeviceIdentity {
        return DeviceIdentity(
            model = profile.model,
            manufacturer = profile.manufacturer,
            brand = profile.brand,
            device = profile.device,
            fingerprint = profile.fingerprint,
            androidVersion = profile.androidVersion,
            apiLevel = profile.apiLevel,
            imei = generateImei(),
            androidId = generateHexString(16),
            serial = generateSerial(),
            macAddress = generateMacAddress(),
            buildDisplayId = "${profile.brand}/${profile.device}/${profile.model}:" +
                    "${profile.androidVersion}/BUILD_${generateAlphanumeric(6)}:user/release-keys",
        )
    }

    /**
     * Writes the identity stored in the [VmInstance] record to the VM's
     * `system/build.prop` file. If the file already exists it is overwritten.
     */
    suspend fun applyIdentity(vmId: String): Unit = withContext(Dispatchers.IO) {
        val vmInstance = vmInstanceDao.getById(vmId)
            ?: throw IllegalArgumentException("VM instance $vmId not found")

        val buildPropContent = BuildPropGenerator.generate(vmInstance)

        val systemDir = VirtualFileSystem.getSystemDir(vmId)
        val buildPropFile = File(systemDir, BUILD_PROP_FILENAME)

        buildPropFile.parentFile?.mkdirs()
        buildPropFile.writeText(buildPropContent)
    }

    /**
     * Reads the current identity from the VM's `build.prop` file and returns
     * it as a [Map] of property key → value.
     *
     * @return A map of all `build.prop` entries, or an empty map when the
     *         file does not exist yet.
     */
    suspend fun getIdentity(vmId: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val systemDir = VirtualFileSystem.getSystemDir(vmId)
            val buildPropFile = File(systemDir, BUILD_PROP_FILENAME)

            if (!buildPropFile.exists()) return@withContext emptyMap()

            buildPropFile.readLines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith('#') && '=' in it }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
        }

    /**
     * Regenerates a single identity [field] for the VM identified by [vmId],
     * persists the change to the database, and re-writes `build.prop`.
     *
     * Supported fields: `"imei"`, `"androidId"`, `"serial"`, `"fingerprint"`.
     *
     * @throws IllegalArgumentException for an unknown field name.
     */
    suspend fun regenerateId(vmId: String, field: String): Unit =
        withContext(Dispatchers.IO) {
            val vmInstance = vmInstanceDao.getById(vmId)
                ?: throw IllegalArgumentException("VM instance $vmId not found")

            val updated: VmInstance = when (field.lowercase()) {
                "imei" -> vmInstance.copy(spoofedImei = generateImei())
                "androidid", "android_id" -> vmInstance.copy(spoofedAndroidId = generateHexString(16))
                "serial" -> vmInstance.copy(spoofedSerial = generateSerial())
                "fingerprint" -> {
                    val newFingerprint =
                        "${vmInstance.spoofedBrand}/${vmInstance.spoofedDevice}/" +
                                "${vmInstance.spoofedModel}:${generateAlphanumeric(4)}/" +
                                "BUILD_${generateAlphanumeric(6)}:user/release-keys"
                    vmInstance.copy(spoofedFingerprint = newFingerprint)
                }
                else -> throw IllegalArgumentException("Unknown identity field: $field")
            }

            vmInstanceDao.upsert(updated)
            applyIdentity(vmId)
        }

    // ------------------------------------------------------------------ //
    //  Identity generators
    // ------------------------------------------------------------------ //

    private fun generateHexString(length: Int): String {
        val chars = "0123456789abcdef"
        return (1..length).map { chars.random() }.joinToString("")
    }

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

    private fun generateMacAddress(): String {
        // The locally administered bit is set (second hex digit is even + 2).
        val octets = (1..6).map { idx ->
            if (idx == 1) {
                // Ensure unicast + locally administered: set bit1, clear bit0.
                val b = (0..255).random() or 0x02 and 0xFE
                String.format("%02X", b)
            } else {
                String.format("%02X", (0..255).random())
            }
        }
        return octets.joinToString(":")
    }

    private fun generateAlphanumeric(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    companion object {

        private const val BUILD_PROP_FILENAME = "build.prop"

        @Volatile
        private var INSTANCE: DeviceIdentityManager? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = DeviceIdentityManager(context.applicationContext)
                    }
                }
            }
        }

        val instance: DeviceIdentityManager
            get() = INSTANCE
                ?: throw IllegalStateException(
                    "DeviceIdentityManager is not initialised. " +
                            "Call DeviceIdentityManager.init(context) first."
                )
    }
}

/**
 * Complete set of device identifiers generated for a VM.
 */
data class DeviceIdentity(
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val fingerprint: String,
    val androidVersion: String,
    val apiLevel: Int,
    val imei: String,
    val androidId: String,
    val serial: String,
    val macAddress: String,
    val buildDisplayId: String,
)
