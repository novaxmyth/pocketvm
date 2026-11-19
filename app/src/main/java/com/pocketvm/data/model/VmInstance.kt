package com.pocketvm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single virtual machine instance with its configuration
 * and runtime metadata.
 */
@Entity(tableName = "vm_instances")
data class VmInstance(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val profileName: String = "AOSP Generic",
    val androidVersion: String = "14",
    val apiLevel: Int = 34,
    val ramMb: Int = 2048,
    val resolutionWidth: Int = 1080,
    val resolutionHeight: Int = 2400,
    val dpi: Int = 420,
    val isRootEnabled: Boolean = false,
    val state: VmState = VmState.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val lastBootedAt: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val spoofedModel: String = "Pixel 8",
    val spoofedManufacturer: String = "Google",
    val spoofedBrand: String = "google",
    val spoofedDevice: String = "shiba",
    val spoofedFingerprint: String = "google/shiba/shiba:14/AP2A.240905.003/12231197:user/release-keys",
    val spoofedAndroidId: String = generateAndroidId(),
    val spoofedImei: String = generateImei(),
    val spoofedSerial: String = generateSerial(),
    val mockLatitude: Double = 0.0,
    val mockLongitude: Double = 0.0,
    val isMockLocationEnabled: Boolean = false,
    val refreshRateHz: Int = 60,
) {
    companion object {
        fun generateAndroidId(): String {
            val chars = "0123456789abcdef"
            return (1..16).map { chars.random() }.joinToString("")
        }

        fun generateImei(): String {
            val digits = (1..14).map { (0..9).random() }
            val checkDigit = luhnCheckDigit(digits)
            return (digits + checkDigit).joinToString("")
        }

        fun generateSerial(): String {
            val chars = "0123456789ABCDEF"
            return (1..16).map { chars.random() }.joinToString("")
        }

        private fun luhnCheckDigit(digits: List<Int>): Int {
            var sum = 0
            for (i in digits.indices) {
                var d = digits[digits.size - 1 - i]
                if (i % 2 == 0) {
                    d *= 2
                    if (d > 9) d -= 9
                }
                sum += d
            }
            return (10 - (sum % 10)) % 10
        }
    }
}
