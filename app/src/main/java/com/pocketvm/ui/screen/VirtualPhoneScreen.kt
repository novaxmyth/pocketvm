@file:OptIn(ExperimentalMaterial3Api::class)

package com.pocketvm.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketvm.data.model.InstalledApp
import com.pocketvm.data.model.VmInstance
import com.pocketvm.ui.component.VirtualNavigationBar
import com.pocketvm.ui.component.VirtualStatusBar
import com.pocketvm.ui.theme.*
import com.pocketvm.ui.viewmodel.VirtualPhoneViewModel

@Composable
fun VirtualPhoneScreen(
    vmId: String,
    onNavigateToApps: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInstall: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VirtualPhoneViewModel = viewModel()
) {
    val vm by viewModel.vmInstance.collectAsState()
    val apps by viewModel.installedApps.collectAsState()

    val instance = vm

    if (instance == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ── Status Bar ──────────────────────────────────────────
        VirtualStatusBar()

        // ── VM Info Bar ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = instance.name,
                style = MaterialTheme.typography.titleSmall,
                color = Cyan,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNavigateToInstall, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, "Install App", tint = Cyan, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = OnDarkSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── App Grid (Main Content) ─────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (apps.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnDarkSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No apps installed",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnDarkSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onNavigateToInstall,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Cyan.copy(alpha = 0.15f),
                            contentColor = Cyan
                        )
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install Apps")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.id }) { app ->
                        AppGridItem(
                            app = app,
                            onClick = { viewModel.launchApp(app.packageName) }
                        )
                    }
                }
            }
        }

        // ── Navigation Bar ──────────────────────────────────────
        VirtualNavigationBar(
            onBack = onNavigateBack,
            onHome = { /* Already home */ },
            onRecents = onNavigateToApps
        )
    }
}

@Composable
private fun AppGridItem(
    app: InstalledApp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App icon placeholder
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.appName.take(2).uppercase(),
                color = Cyan,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 64.dp)
        )
    }
}
