package com.antidaze.pocketvm.data

import org.json.JSONException
import org.json.JSONObject

/** Persistent description of one virtual machine. */
data class VmConfig(
    val id: String,
    var name: String,
    var ramMb: Int = 1024,
    var cpus: Int = 2,
    /** Absolute path to system disk image (.qcow2, .img raw, or .iso). */
    var systemImagePath: String? = null,
    /** True when systemImagePath is a bootable installer ISO (attached read-only). */
    var systemIsCdrom: Boolean = false,
    /** Optional direct kernel boot (e.g. AOSP/Cuttlefish images). Overrides BIOS. */
    var kernelPath: String? = null,
    var kernelCmdline: String? = null,
    /** "linux" = VNC console, "android" = adb + scrcpy console. */
    var guest: String = "linux",
    /** True while a background download is being fetched for this VM. */
    var preparing: Boolean = false,
    /** Last non-fatal note (e.g. failed download) shown in the VM list. */
    var statusNote: String = "",
    /** Download job descriptor, persisted so an app update can re-enqueue it. */
    var pendingKind: String = "",
    var pendingUrl: String = ""
) {
    @Throws(JSONException::class)
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ramMb", ramMb)
        put("cpus", cpus)
        put("systemImagePath", systemImagePath ?: "")
        put("systemIsCdrom", systemIsCdrom)
        put("kernelPath", kernelPath ?: "")
        put("kernelCmdline", kernelCmdline ?: "")
        put("guest", guest)
        put("preparing", preparing)
        put("statusNote", statusNote)
        put("pendingKind", pendingKind)
        put("pendingUrl", pendingUrl)
    }

    companion object {
        fun fromJson(o: JSONObject): VmConfig = VmConfig(
            id = o.getString("id"),
            name = o.getString("name"),
            ramMb = o.optInt("ramMb", 1024),
            cpus = o.optInt("cpus", 2),
            systemImagePath = o.optString("systemImagePath", "").ifEmpty { null },
            systemIsCdrom = o.optBoolean("systemIsCdrom", false),
            kernelPath = o.optString("kernelPath", "").ifEmpty { null },
            kernelCmdline = o.optString("kernelCmdline", "").ifEmpty { null },
            guest = o.optString("guest", "linux"),
            preparing = o.optBoolean("preparing", false),
            statusNote = o.optString("statusNote", ""),
            pendingKind = o.optString("pendingKind", ""),
            pendingUrl = o.optString("pendingUrl", "")
        )
    }
}
