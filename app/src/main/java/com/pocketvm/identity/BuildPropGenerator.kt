package com.pocketvm.identity

import com.pocketvm.data.model.VmInstance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates a complete Android `build.prop` file for a [VmInstance].
 *
 * The output mirrors the format found on real devices, covering all major
 * `ro.product.*`, `ro.build.*`, and `ro.hardware.*` keys, plus PocketVM-
 * specific extensions under the `ro.pocketvm.*` namespace.
 *
 * Usage:
 * ```kotlin
 * val content: String = BuildPropGenerator.generate(vmInstance)
 * File(systemDir, "build.prop").writeText(content)
 * ```
 */
object BuildPropGenerator {

    // Date formatter matching AOSP build date format.
    private val BUILD_DATE_FORMAT =
        SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Produces the full `build.prop` content string for [vm].
     */
    fun generate(vm: VmInstance): String {
        val buildDate = BUILD_DATE_FORMAT.format(Date())
        val buildTimestamp = System.currentTimeMillis() / 1000

        val buildId = "PVM${generateBuildSuffix()}"
        val incrementalId = generateIncremental()
        val buildType = "user"
        val buildTags = "release-keys"

        val displayId = "${vm.spoofedBrand}/${vm.spoofedDevice}/$buildId/$incrementalId:" +
                "${androidVersionFor(vm)}/$buildType/$buildTags"

        val sb = StringBuilder(4096)

        sb.appendHeader()

        // ---- ro.product.* ----
        sb.appendSection("Product info")
        sb.prop("ro.product.model", vm.spoofedModel)
        sb.prop("ro.product.brand", vm.spoofedBrand)
        sb.prop("ro.product.name", vm.spoofedDevice)
        sb.prop("ro.product.device", vm.spoofedDevice)
        sb.prop("ro.product.manufacturer", vm.spoofedManufacturer)
        sb.prop("ro.product.board", vm.spoofedDevice)
        sb.prop("ro.product.cpu.abi", "arm64-v8a")
        sb.prop("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a,armeabi")
        sb.prop("ro.product.cpu.abilist32", "armeabi-v7a,armeabi")
        sb.prop("ro.product.cpu.abilist64", "arm64-v8a")
        sb.prop("ro.product.first_api_level", apiLevelFor(vm).toString())
        sb.prop("ro.product.locale", "en-US")

        // ---- ro.build.* ----
        sb.appendSection("Build info")
        sb.prop("ro.build.id", buildId)
        sb.prop("ro.build.display.id", displayId)
        sb.prop("ro.build.version.incremental", incrementalId)
        sb.prop("ro.build.version.sdk", apiLevelFor(vm).toString())
        sb.prop("ro.build.version.release", androidVersionFor(vm))
        sb.prop("ro.build.version.release_or_codename", androidVersionFor(vm))
        sb.prop("ro.build.version.security_patch", "2026-10-05")
        sb.prop("ro.build.version.base_os", "")
        sb.prop("ro.build.version.preview_sdk", "0")
        sb.prop("ro.build.version.codename", "REL")
        sb.prop("ro.build.version.min_supported_target_sdk", "23")
        sb.prop("ro.build.type", buildType)
        sb.prop("ro.build.tags", buildTags)
        sb.prop("ro.build.user", "android-build")
        sb.prop("ro.build.host", "build.pocketvm.com")
        sb.prop("ro.build.flavor", "${vm.spoofedDevice}-$buildType")
        sb.prop("ro.build.description", "${vm.spoofedDevice}-$buildType ${androidVersionFor(vm)} $buildId $incrementalId $buildTags")
        sb.prop("ro.build.date", buildDate)
        sb.prop("ro.build.date.utc", buildTimestamp.toString())
        sb.prop("ro.build.fingerprint", vm.spoofedFingerprint)
        sb.prop("ro.build.characteristics", "default")

        // ---- Hardware ----
        sb.appendSection("Hardware")
        sb.prop("ro.hardware", vm.spoofedDevice)
        sb.prop("ro.hardware.chipname", "virtual")
        sb.prop("ro.board.platform", "virtual")
        sb.prop("ro.arch", "arm64")

        // ---- Serial / identity ----
        sb.appendSection("Device identity")
        sb.prop("ro.serialno", vm.spoofedSerial)
        sb.prop("ro.boot.serialno", vm.spoofedSerial)

        // ---- Networking ----
        sb.appendSection("Networking")
        sb.prop("ro.com.google.gmsversion", androidVersionFor(vm))
        sb.prop("gsm.version.baseband", "1.0")
        sb.prop("gsm.version.ril-impl", "pocketvm-ril 1.0")
        sb.prop("ro.telephony.default_network", "9")
        sb.prop("ro.url.legal", "http://www.google.com/intl/%s/mobile/android/basic/phone-legal.html")
        sb.prop("ro.com.google.clientidbase", "android-google")

        // ---- Locale / timezone ----
        sb.appendSection("Locale & timezone")
        sb.prop("persist.sys.language", "en")
        sb.prop("persist.sys.country", "US")
        sb.prop("persist.sys.localevar", "")
        sb.prop("persist.sys.timezone", "America/Los_Angeles")
        sb.prop("ro.com.google.ime.theme_id", "5")

        // ---- Dalvik ----
        sb.appendSection("Dalvik")
        sb.prop("dalvik.vm.heapsize", "512m")
        sb.prop("dalvik.vm.heapstartsize", "8m")
        sb.prop("dalvik.vm.heapgrowthlimit", "256m")
        sb.prop("dalvik.vm.heapminfree", "512k")
        sb.prop("dalvik.vm.heapmaxfree", "8m")
        sb.prop("dalvik.vm.heaptargetutilization", "0.75")
        sb.prop("dalvik.vm.usejit", "true")
        sb.prop("dalvik.vm.dex2oat-Xms", "64m")
        sb.prop("dalvik.vm.dex2oat-Xmx", "512m")
        sb.prop("dalvik.vm.image-dex2oat-Xms", "64m")
        sb.prop("dalvik.vm.image-dex2oat-Xmx", "64m")

        // ---- Misc system props ----
        sb.appendSection("System")
        sb.prop("ro.debuggable", "0")
        sb.prop("ro.secure", if (vm.isRootEnabled) "0" else "1")
        sb.prop("ro.adb.secure", if (vm.isRootEnabled) "0" else "1")
        sb.prop("service.bootanim.exit", "1")
        sb.prop("sys.oem_unlock_allowed", "0")
        sb.prop("ro.setupwizard.mode", "DISABLED")
        sb.prop("ro.opa.eligible_device", "true")

        // ---- PocketVM extensions ----
        sb.appendSection("PocketVM")
        sb.prop("ro.pocketvm.version", "3.0")
        sb.prop("ro.pocketvm.instance_id", vm.id)
        sb.prop("ro.pocketvm.root", if (vm.isRootEnabled) "1" else "0")
        sb.prop("ro.pocketvm.mock_location", if (vm.isMockLocationEnabled) "1" else "0")
        if (vm.isMockLocationEnabled) {
            sb.prop("ro.pocketvm.mock_latitude", vm.mockLatitude.toString())
            sb.prop("ro.pocketvm.mock_longitude", vm.mockLongitude.toString())
        }
        sb.prop("ro.pocketvm.ram_mb", vm.ramMb.toString())

        sb.appendLine()
        sb.appendLine("# end of build.prop")

        return sb.toString()
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private fun androidVersionFor(vm: VmInstance): String {
        // Attempt to extract from fingerprint; fall back to "14".
        val parts = vm.spoofedFingerprint.split(':')
        return if (parts.size >= 2) {
            parts[1].split('/').firstOrNull() ?: "14"
        } else {
            "14"
        }
    }

    private fun apiLevelFor(vm: VmInstance): Int {
        return when (androidVersionFor(vm)) {
            "15" -> 35
            "14" -> 34
            "13" -> 33
            "12" -> 31
            "11" -> 30
            "10" -> 29
            else -> 34
        }
    }

    private fun generateBuildSuffix(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun generateIncremental(): String {
        return (1..10).map { (0..9).random() }.joinToString("")
    }

    private fun StringBuilder.appendHeader() {
        appendLine("# begin build.prop")
        appendLine("# autogenerated by PocketVM 3.0")
        appendLine("#")
    }

    private fun StringBuilder.appendSection(title: String) {
        appendLine()
        appendLine("# ------ $title ------")
    }

    private fun StringBuilder.prop(key: String, value: String) {
        appendLine("$key=$value")
    }

    /**
     * Convenience: generates a build.prop and returns it line-by-line.
     */
    fun generateLines(vm: VmInstance): List<String> = generate(vm).lines()
}
