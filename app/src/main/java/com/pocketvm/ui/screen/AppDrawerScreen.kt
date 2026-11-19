@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.pocketvm.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.pocketvm.ui.theme.*
import com.pocketvm.ui.viewmodel.VirtualPhoneViewModel

@Composable
fun AppDrawerScreen(
    vmId: String,
    onNavigateToInstall: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VirtualPhoneViewModel = viewModel()
) {
    val apps by viewModel.installedApps.collectAsState()
    var contextApp by remember { mutableStateOf<InstalledApp?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Installed Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToInstall,
                containerColor = Cyan,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Filled.Add, "Install App")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📦", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("No apps installed yet", color = OnDarkBackground)
                    Spacer(Modifier.height(8.dp))
                    Text("Clone apps from your phone or install APK files",
                        color = OnDarkSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(apps, key = { it.id }) { app ->
                    Column(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { viewModel.launchApp(app.packageName) },
                                onLongClick = { contextApp = app }
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.appName.take(2).uppercase(),
                                color = Magenta,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnDarkBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Context menu dialog
    contextApp?.let { app ->
        AlertDialog(
            onDismissRequest = { contextApp = null },
            title = { Text(app.appName) },
            text = {
                Column {
                    Text("Package: ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                    Text("Version: ${app.versionName}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.launchApp(app.packageName)
                        contextApp = null
                    }) { Text("Launch") }
                    TextButton(
                        onClick = {
                            viewModel.uninstallApp(app.packageName)
                            contextApp = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                    ) { Text("Uninstall") }
                }
            },
            dismissButton = {
                TextButton(onClick = { contextApp = null }) { Text("Cancel") }
            }
        )
    }
}
