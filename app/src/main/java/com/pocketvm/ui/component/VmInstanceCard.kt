package com.pocketvm.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketvm.data.model.VmInstance
import com.pocketvm.data.model.VmState
import com.pocketvm.ui.theme.*

@Composable
fun VmInstanceCard(
    instance: VmInstance,
    onBoot: () -> Unit,
    onShutdown: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = instance.state == VmState.RUNNING
    val borderColor by animateColorAsState(
        targetValue = if (isRunning) Cyan.copy(alpha = 0.6f) else Color.Transparent,
        label = "borderGlow"
    )

    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface,
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkBackground
                    )
                    Text(
                        text = "${instance.profileName} · Android ${instance.androidVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSurfaceVariant
                    )
                }
                StatusChip(state = instance.state)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Info Row ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoBadge(
                    icon = Icons.Outlined.Memory,
                    text = "${instance.ramMb} MB",
                )
                InfoBadge(
                    icon = Icons.Outlined.AspectRatio,
                    text = "${instance.resolutionWidth}×${instance.resolutionHeight}",
                )
                if (instance.isRootEnabled) {
                    InfoBadge(
                        icon = Icons.Filled.Shield,
                        text = "Root",
                        tint = WarningYellow,
                    )
                }
                InfoBadge(
                    icon = Icons.Outlined.Storage,
                    text = formatBytes(instance.storageUsedBytes),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Actions ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (instance.state == VmState.RUNNING) {
                    FilledTonalButton(
                        onClick = onShutdown,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ErrorRed.copy(alpha = 0.15f),
                            contentColor = ErrorRed
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onBoot,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SuccessGreen.copy(alpha = 0.15f),
                            contentColor = SuccessGreen
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Boot", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Boot")
                    }
                }

                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    enabled = instance.state == VmState.RUNNING,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Cyan.copy(alpha = 0.15f),
                        contentColor = Cyan
                    )
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open")
                }

                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = OnDarkSurfaceVariant)
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = ErrorRed.copy(alpha = 0.7f))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete VM?") },
            text = { Text("\"${instance.name}\" and all its data will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatusChip(state: VmState) {
    val (color, label) = when (state) {
        VmState.STOPPED -> OnDarkSurfaceVariant to "Stopped"
        VmState.BOOTING -> WarningYellow to "Booting"
        VmState.RUNNING -> SuccessGreen to "Running"
        VmState.PAUSED -> InfoBlue to "Paused"
        VmState.ERROR -> ErrorRed to "Error"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InfoBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = OnDarkSurfaceVariant
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
