@file:OptIn(ExperimentalMaterial3Api::class)

package com.pocketvm.ui.screen

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketvm.apps.ApkInstaller
import com.pocketvm.apps.HostAppInfo
import com.pocketvm.apps.HostAppScanner
import com.pocketvm.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class ApkInstallerViewModel(application: Application) : AndroidViewModel(application) {

    private val _hostApps = MutableStateFlow<List<HostAppInfo>>(emptyList())
    val hostApps: StateFlow<List<HostAppInfo>> = _hostApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _installProgress = MutableStateFlow<String?>(null)
    val installProgress: StateFlow<String?> = _installProgress.asStateFlow()

    fun loadHostApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val context = getApplication<Application>()
                val apps = HostAppScanner.getHostApps(context)
                _hostApps.value = apps
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }

    fun cloneApp(vmId: String, packageName: String) {
        viewModelScope.launch {
            _installProgress.value = "Cloning $packageName..."
            try {
                val app: Application = getApplication()
                HostAppScanner.cloneToVm(app, vmId, packageName)
                _installProgress.value = "Installed successfully!"
            } catch (e: Exception) {
                _installProgress.value = "Failed: ${e.message}"
            }
            kotlinx.coroutines.delay(2000)
            _installProgress.value = null
        }
    }
}

@Composable
fun ApkInstallerScreen(
    vmId: String,
    onNavigateBack: () -> Unit,
    viewModel: ApkInstallerViewModel = viewModel()
) {
    val hostApps by viewModel.hostApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadHostApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install Apps", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.padding(padding)) {
            // ── Tab Row ─────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = Cyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Clone from Host") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Install APK") }
                )
            }

            // ── Progress Snackbar ───────────────────────────────
            installProgress?.let { progress ->
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Cyan,
                    trackColor = DarkSurfaceVariant
                )
                Text(
                    text = progress,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Cyan,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (selectedTab) {
                0 -> {
                    // ── Clone from Host tab ─────────────────────
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Cyan)
                        }
                    } else if (hostApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No apps found on host device", color = OnDarkSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(hostApps, key = { it.packageName }) { app ->
                                HostAppRow(
                                    app = app,
                                    onClone = { viewModel.cloneApp(vmId, app.packageName) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // ── Install APK file tab ────────────────────
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = OnDarkSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "APK file picker",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnDarkBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Select an APK file from storage to install into this VM",
                                color = OnDarkSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { /* File picker would go here */ },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Cyan.copy(alpha = 0.15f),
                                    contentColor = Cyan
                                )
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Browse Files")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostAppRow(app: HostAppInfo, onClone: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = OnDarkBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onClone,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Cyan.copy(alpha = 0.15f),
                    contentColor = Cyan
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clone", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
