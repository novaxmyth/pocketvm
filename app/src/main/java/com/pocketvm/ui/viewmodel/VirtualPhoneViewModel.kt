package com.pocketvm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pocketvm.apps.ApkInstaller
import com.pocketvm.apps.AppLauncher
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.model.InstalledApp
import com.pocketvm.data.model.VmInstance
import com.pocketvm.identity.MockLocationProvider
import com.pocketvm.instance.InstanceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VirtualPhoneViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val vmId: String = savedStateHandle.get<String>("vmId") ?: ""

    private val db = PocketVmDatabase.getInstance(application)
    private val instanceManager = InstanceManager.get()

    val vmInstance: StateFlow<VmInstance?> = db.vmInstanceDao()
        .observeById(vmId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val installedApps: StateFlow<List<InstalledApp>> = db.installedAppDao()
        .observeByVm(vmId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun launchApp(packageName: String) {
        viewModelScope.launch {
            try {
                AppLauncher.get().launchApp(getApplication(), vmId, packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            try {
                ApkInstaller.get().uninstallApp(vmId, packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleRoot() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            val updated = current.copy(isRootEnabled = !current.isRootEnabled)
            db.vmInstanceDao().upsert(updated)
        }
    }

    fun updateMockLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            MockLocationProvider.get().setLocation(vmId, lat, lng)
        }
    }
}
