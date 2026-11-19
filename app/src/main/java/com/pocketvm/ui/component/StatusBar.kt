package com.pocketvm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketvm.ui.theme.DarkBackground
import com.pocketvm.ui.theme.OnDarkBackground
import com.pocketvm.ui.theme.OnDarkSurfaceVariant
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simulated Android status bar showing time, WiFi, signal, and battery.
 */
@Composable
fun VirtualStatusBar(
    modifier: Modifier = Modifier,
    batteryLevel: Int = 85,
) {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            kotlinx.coroutines.delay(30_000L) // Update every 30s
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Time
        Text(
            text = currentTime,
            color = OnDarkBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // Right: Status icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = "WiFi",
                modifier = Modifier.size(14.dp),
                tint = OnDarkBackground
            )
            Icon(
                Icons.Filled.SignalCellular4Bar,
                contentDescription = "Signal",
                modifier = Modifier.size(14.dp),
                tint = OnDarkBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$batteryLevel%",
                    color = OnDarkSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    when {
                        batteryLevel > 80 -> Icons.Filled.BatteryFull
                        batteryLevel > 50 -> Icons.Filled.Battery5Bar
                        batteryLevel > 20 -> Icons.Filled.Battery3Bar
                        else -> Icons.Filled.Battery1Bar
                    },
                    contentDescription = "Battery",
                    modifier = Modifier.size(14.dp),
                    tint = if (batteryLevel <= 15) Color.Red else OnDarkBackground
                )
            }
        }
    }
}

private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
