package com.antidaze.pocketvm.model

import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class VmState {
    STOPPED,
    STARTING,
    RUNNING,
    PAUSED
}

data class DeviceProfile(
    val manufacturer: String = "Google",
    val model: String = "Pixel 8 Pro",
    val brand: String = "google",
    val board: String = "husky",
    val hardware: String = "husky",
    val device: String = "husky",
    val imei: String = generateImei(),
    val androidId: String = generateAndroidId(),
    val macAddress: String = generateMacAddress()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("manufacturer", manufacturer)
        put("model", model)
        put("brand", brand)
        put("board", board)
        put("hardware", hardware)
        put("device", device)
        put("imei", imei)
        put("androidId", androidId)
        put("macAddress", macAddress)
    }

    companion object {
        fun fromJson(json: JSONObject): DeviceProfile = DeviceProfile(
            manufacturer = json.optString("manufacturer", "Google"),
            model = json.optString("model", "Pixel 8 Pro"),
            brand = json.optString("brand", "google"),
            board = json.optString("board", "husky"),
            hardware = json.optString("hardware", "husky"),
            device = json.optString("device", "husky"),
            imei = json.optString("imei", generateImei()),
            androidId = json.optString("androidId", generateAndroidId()),
            macAddress = json.optString("macAddress", generateMacAddress())
        )

        fun pixel8Pro() = DeviceProfile("Google", "Pixel 8 Pro", "google", "husky", "husky", "husky")
        fun galaxyS24() = DeviceProfile("Samsung", "Galaxy S24 Ultra", "samsung", "e3q", "qcom", "e3q")
        fun rogPhone8() = DeviceProfile("Asus", "ROG Phone 8 Pro", "asus", "AI2401", "qcom", "AI2401")
        fun cleanAosp() = DeviceProfile("AOSP", "AOSP on ARM64", "android", "generic_arm64", "cutf", "generic_arm64")

        private fun generateImei(): String {
            val sb = StringBuilder("86")
            for (i in 0 until 13) sb.append((0..9).random())
            return sb.toString()
        }

        private fun generateAndroidId(): String {
            val chars = "0123456789abcdef"
            return (1..16).map { chars.random() }.joinToString("")
        }

        private fun generateMacAddress(): String {
            val hex = "0123456789ABCDEF"
            val parts = (1..6).map { "${hex.random()}${hex.random()}" }
            return parts.joinToString(":")
        }
    }
}

data class AndroidVersionProfile(
    val versionName: String = "Android 14 (UpsideDownCake)",
    val release: String = "14",
    val apiLevel: Int = 34,
    val buildId: String = "AP2A.240905.003",
    val securityPatch: String = "2024-11-01",
    val kernelVersion: String = "Linux 5.15.148-android-pocketvm+"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("versionName", versionName)
        put("release", release)
        put("apiLevel", apiLevel)
        put("buildId", buildId)
        put("securityPatch", securityPatch)
        put("kernelVersion", kernelVersion)
    }

    companion object {
        fun fromJson(json: JSONObject): AndroidVersionProfile = AndroidVersionProfile(
            versionName = json.optString("versionName", "Android 14 (UpsideDownCake)"),
            release = json.optString("release", "14"),
            apiLevel = json.optInt("apiLevel", 34),
            buildId = json.optString("buildId", "AP2A.240905.003"),
            securityPatch = json.optString("securityPatch", "2024-11-01"),
            kernelVersion = json.optString("kernelVersion", "Linux 5.15.148-android-pocketvm+")
        )

        fun android14() = AndroidVersionProfile("Android 14 (UpsideDownCake)", "14", 34, "AP2A.240905.003", "2024-11-01")
        fun android13() = AndroidVersionProfile("Android 13 (Tiramisu)", "13", 33, "TQ3A.230901.001", "2023-09-01")
        fun android12() = AndroidVersionProfile("Android 12 (SnowCone)", "12", 31, "SQ3A.220705.003", "2022-07-01")
    }
}

data class DisplayConfig(
    val width: Int = 1080,
    val height: Int = 2400,
    val dpi: Int = 420,
    val refreshRateHz: Int = 60,
    val autoRotate: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("width", width)
        put("height", height)
        put("dpi", dpi)
        put("refreshRateHz", refreshRateHz)
        put("autoRotate", autoRotate)
    }

    companion object {
        fun fromJson(json: JSONObject): DisplayConfig = DisplayConfig(
            width = json.optInt("width", 1080),
            height = json.optInt("height", 2400),
            dpi = json.optInt("dpi", 420),
            refreshRateHz = json.optInt("refreshRateHz", 60),
            autoRotate = json.optBoolean("autoRotate", true)
        )

        fun fhdPlus420() = DisplayConfig(1080, 2400, 420, 60)
        fun fhd400() = DisplayConfig(1080, 1920, 400, 60)
        fun hd320() = DisplayConfig(720, 1280, 320, 60)
        fun gaming120Hz() = DisplayConfig(1080, 2400, 420, 120)
    }
}

data class RootConfig(
    var isRootEnabled: Boolean = false,
    var selinuxMode: String = "Enforcing",
    var mockMagisk: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isRootEnabled", isRootEnabled)
        put("selinuxMode", selinuxMode)
        put("mockMagisk", mockMagisk)
    }

    companion object {
        fun fromJson(json: JSONObject): RootConfig = RootConfig(
            isRootEnabled = json.optBoolean("isRootEnabled", false),
            selinuxMode = json.optString("selinuxMode", "Enforcing"),
            mockMagisk = json.optBoolean("mockMagisk", false)
        )
    }
}

data class SensorConfig(
    var mockGpsEnabled: Boolean = false,
    var latitude: Double = 37.7749,
    var longitude: Double = -122.4194,
    var altitude: Double = 15.0,
    var mockBatteryLevel: Int = 100,
    var mockIsCharging: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mockGpsEnabled", mockGpsEnabled)
        put("latitude", latitude)
        put("longitude", longitude)
        put("altitude", altitude)
        put("mockBatteryLevel", mockBatteryLevel)
        put("mockIsCharging", mockIsCharging)
    }

    companion object {
        fun fromJson(json: JSONObject): SensorConfig = SensorConfig(
            mockGpsEnabled = json.optBoolean("mockGpsEnabled", false),
            latitude = json.optDouble("latitude", 37.7749),
            longitude = json.optDouble("longitude", -122.4194),
            altitude = json.optDouble("altitude", 15.0),
            mockBatteryLevel = json.optInt("mockBatteryLevel", 100),
            mockIsCharging = json.optBoolean("mockIsCharging", false)
        )
    }
}

data class VirtualInstance(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Android 14 VM",
    val createdAt: Long = System.currentTimeMillis(),
    var lastLaunchedAt: Long = 0L,
    var state: VmState = VmState.STOPPED,
    var ramMb: Int = 2048,
    var cpuCores: Int = 4,
    var deviceProfile: DeviceProfile = DeviceProfile.pixel8Pro(),
    var osProfile: AndroidVersionProfile = AndroidVersionProfile.android14(),
    var displayConfig: DisplayConfig = DisplayConfig.fhdPlus420(),
    var rootConfig: RootConfig = RootConfig(),
    var sensorConfig: SensorConfig = SensorConfig(),
    var storageQuotaMb: Int = 16384,
    var storageUsedBytes: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("lastLaunchedAt", lastLaunchedAt)
        put("state", state.name)
        put("ramMb", ramMb)
        put("cpuCores", cpuCores)
        put("deviceProfile", deviceProfile.toJson())
        put("osProfile", osProfile.toJson())
        put("displayConfig", displayConfig.toJson())
        put("rootConfig", rootConfig.toJson())
        put("sensorConfig", sensorConfig.toJson())
        put("storageQuotaMb", storageQuotaMb)
        put("storageUsedBytes", storageUsedBytes)
    }

    companion object {
        fun fromJson(json: JSONObject): VirtualInstance = VirtualInstance(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Android 14 VM"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            lastLaunchedAt = json.optLong("lastLaunchedAt", 0L),
            state = try {
                VmState.valueOf(json.optString("state", VmState.STOPPED.name))
            } catch (e: Exception) {
                VmState.STOPPED
            },
            ramMb = json.optInt("ramMb", 2048),
            cpuCores = json.optInt("cpuCores", 4),
            deviceProfile = json.optJSONObject("deviceProfile")?.let { DeviceProfile.fromJson(it) } ?: DeviceProfile.pixel8Pro(),
            osProfile = json.optJSONObject("osProfile")?.let { AndroidVersionProfile.fromJson(it) } ?: AndroidVersionProfile.android14(),
            displayConfig = json.optJSONObject("displayConfig")?.let { DisplayConfig.fromJson(it) } ?: DisplayConfig.fhdPlus420(),
            rootConfig = json.optJSONObject("rootConfig")?.let { RootConfig.fromJson(it) } ?: RootConfig(),
            sensorConfig = json.optJSONObject("sensorConfig")?.let { SensorConfig.fromJson(it) } ?: SensorConfig(),
            storageQuotaMb = json.optInt("storageQuotaMb", 16384),
            storageUsedBytes = json.optLong("storageUsedBytes", 0L)
        )
    }
}
