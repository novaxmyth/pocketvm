@file:OptIn(ExperimentalMaterial3Api::class)

package com.pocketvm.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketvm.data.model.VmInstance
import com.pocketvm.identity.PresetLocation
import com.pocketvm.ui.theme.*
import com.pocketvm.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    vmId: String,
    onNavigateBack: () -> Unit,
    onVmDeleted: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val vm by viewModel.vmInstance.collectAsState()
    val storageUsage by viewModel.storageUsage.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()

    val instance = vm

    if (instance == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VM Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Device Identity ─────────────────────────────────
            SettingsSection("Device Identity") {
                SettingsInfoRow("Model", instance.spoofedModel)
                SettingsInfoRow("Manufacturer", instance.spoofedManufacturer)
                SettingsInfoRowAction("IMEI", instance.spoofedImei) {
                    viewModel.regenerateImei()
                }
                SettingsInfoRowAction("Android ID", instance.spoofedAndroidId) {
                    viewModel.regenerateAndroidId()
                }
                SettingsInfoRowAction("Serial", instance.spoofedSerial) {
                    viewModel.regenerateSerial()
                }
                SettingsInfoRow("Fingerprint", instance.spoofedFingerprint)
            }

            // ── Display ─────────────────────────────────────────
            SettingsSection("Display") {
                SettingsInfoRow("Resolution", "${instance.resolutionWidth} × ${instance.resolutionHeight}")
                SettingsInfoRow("DPI", "${instance.dpi}")
                SettingsInfoRow("Refresh Rate", "${instance.refreshRateHz} Hz")
            }

            // ── Performance ─────────────────────────────────────
            SettingsSection("Performance") {
                var ramSlider by remember { mutableFloatStateOf(instance.ramMb.toFloat()) }
                Column {
                    Text(
                        "RAM: ${ramSlider.toInt()} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkBackground
                    )
                    Slider(
                        value = ramSlider,
                        onValueChange = { ramSlider = it },
                        onValueChangeFinished = { viewModel.updateRam(ramSlider.toInt()) },
                        valueRange = 1024f..4096f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan,
                            activeTrackColor = Cyan
                        )
                    )
                }
            }

            // ── Security ────────────────────────────────────────
            SettingsSection("Security") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Virtual Root (SU)", color = OnDarkBackground)
                        Text(
                            "Safe: host device stays unrooted",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkSurfaceVariant
                        )
                    }
                    Switch(
                        checked = instance.isRootEnabled,
                        onCheckedChange = { viewModel.toggleRoot() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarningYellow,
                            checkedTrackColor = WarningYellow.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // ── Mock Location ───────────────────────────────────
            SettingsSection("Mock GPS Location") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Mock Location", color = OnDarkBackground)
                    Switch(
                        checked = instance.isMockLocationEnabled,
                        onCheckedChange = { viewModel.toggleMockLocation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Lime,
                            checkedTrackColor = Lime.copy(alpha = 0.3f)
                        )
                    )
                }

                if (instance.isMockLocationEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = OnDarkSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val presets = PresetLocation.ALL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.take(3).forEach { preset ->
                            AssistChip(
                                onClick = { viewModel.setMockLocation(preset.latitude, preset.longitude) },
                                label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.drop(3).forEach { preset ->
                            AssistChip(
                                onClick = { viewModel.setMockLocation(preset.latitude, preset.longitude) },
                                label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Current: ${instance.mockLatitude}, ${instance.mockLongitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSurfaceVariant
                    )
                }
            }

            // ── Storage ─────────────────────────────────────────
            SettingsSection("Storage") {
                SettingsInfoRow("Used", storageUsage)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.clearCache() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnDarkBackground)
                    ) {
                        Text("Clear Cache")
                    }
                }
            }

            // ── About ───────────────────────────────────────────
            SettingsSection("About") {
                SettingsInfoRow("Profile", instance.profileName)
                SettingsInfoRow("Android", "${instance.androidVersion} (API ${instance.apiLevel})")
                SettingsInfoRow("Created", formatTimestamp(instance.createdAt))
                if (instance.lastBootedAt > 0) {
                    SettingsInfoRow("Last Boot", formatTimestamp(instance.lastBootedAt))
                }
            }

            // ── Danger Zone ─────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Danger Zone", fontWeight = FontWeight.Bold, color = ErrorRed)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = OnDarkBackground
                        ),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OnDarkBackground,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Virtual Machine")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete VM?") },
            text = { Text("\"${instance.name}\" and ALL its data will be permanently destroyed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteVm(onVmDeleted)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Cyan
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = OnDarkSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = OnDarkBackground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsInfoRowAction(label: String, value: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = OnDarkSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                color = OnDarkBackground,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onAction, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Refresh, "Regenerate", tint = Cyan, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
