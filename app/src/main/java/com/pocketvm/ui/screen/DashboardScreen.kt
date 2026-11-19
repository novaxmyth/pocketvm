@file:OptIn(ExperimentalMaterial3Api::class)

package com.pocketvm.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketvm.data.model.DeviceProfile
import com.pocketvm.data.model.VmState
import com.pocketvm.ui.component.VmInstanceCard
import com.pocketvm.ui.theme.*
import com.pocketvm.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    onOpenVm: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val instances by viewModel.instances.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PocketVM",
                        fontWeight = FontWeight.Bold,
                        color = Cyan
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = Cyan,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create VM")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (instances.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📱",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No Virtual Machines",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnDarkBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap + to create your first isolated Android instance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(instances, key = { it.id }) { instance ->
                    VmInstanceCard(
                        instance = instance,
                        onBoot = { viewModel.bootVm(instance.id) },
                        onShutdown = { viewModel.shutdownVm(instance.id) },
                        onOpen = { onOpenVm(instance.id) },
                        onSettings = { onOpenSettings(instance.id) },
                        onDelete = { viewModel.deleteVm(instance.id) }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateInstanceSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, profile, ram, root ->
                viewModel.createVm(name, profile, ram, root)
                showCreateSheet = false
            }
        )
    }
}

@Composable
private fun CreateInstanceSheet(
    onDismiss: () -> Unit,
    onCreate: (String, DeviceProfile, Int, Boolean) -> Unit
) {
    val profiles = DeviceProfile.PROFILES
    var name by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(profiles.first()) }
    var ramMb by remember { mutableFloatStateOf(2048f) }
    var rootEnabled by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Create Virtual Machine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Cyan
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Instance Name") },
                placeholder = { Text("My Virtual Phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    cursorColor = Cyan
                )
            )

            // Device Profile
            ExposedDropdownMenuBox(
                expanded = profileExpanded,
                onExpandedChange = { profileExpanded = !profileExpanded }
            ) {
                OutlinedTextField(
                    value = selectedProfile.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device Profile") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan
                    )
                )
                ExposedDropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false }
                ) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                selectedProfile = profile
                                profileExpanded = false
                            }
                        )
                    }
                }
            }

            // RAM slider
            Column {
                Text(
                    "RAM: ${ramMb.toInt()} MB",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDarkBackground
                )
                Slider(
                    value = ramMb,
                    onValueChange = { ramMb = it },
                    valueRange = 1024f..4096f,
                    steps = 2, // 1024, 2048, 3072, 4096
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan,
                        activeTrackColor = Cyan
                    )
                )
            }

            // Root toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Enable Root (SU)", color = OnDarkBackground)
                    Text(
                        "Grants virtual root inside VM only",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSurfaceVariant
                    )
                }
                Switch(
                    checked = rootEnabled,
                    onCheckedChange = { rootEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarningYellow,
                        checkedTrackColor = WarningYellow.copy(alpha = 0.3f)
                    )
                )
            }

            // Create button
            Button(
                onClick = {
                    val finalName = name.ifBlank { selectedProfile.name }
                    onCreate(finalName, selectedProfile, ramMb.toInt(), rootEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = DarkBackground
                )
            ) {
                Text("Create Instance", fontWeight = FontWeight.Bold)
            }
        }
    }
}
