package com.pocketvm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pocketvm.data.db.PocketVmDatabase
import com.pocketvm.data.model.VmInstance
import com.pocketvm.engine.io.VirtualFileSystem
import com.pocketvm.identity.DeviceIdentityManager
import com.pocketvm.identity.MockLocationProvider
import com.pocketvm.instance.InstanceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val vmId: String = savedStateHandle.get<String>("vmId") ?: ""

    private val db = PocketVmDatabase.getInstance(application)
    private val instanceManager = InstanceManager.get()
    private val identityManager = DeviceIdentityManager.get()
    private val locationProvider = MockLocationProvider.get()
    private val vfs = VirtualFileSystem.get()

    val vmInstance: StateFlow<VmInstance?> = db.vmInstanceDao()
        .observeById(vmId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _storageUsage = MutableStateFlow("")
    val storageUsage: StateFlow<String> = _storageUsage.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    init {
        refreshStorageUsage()
    }

    private fun refreshStorageUsage() {
        viewModelScope.launch {
            val bytes = vfs.calculateVmSize(vmId)
            _storageUsage.value = vfs.formatSize(bytes)
        }
    }

    fun updateRam(mb: Int) {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(current.copy(ramMb = mb))
        }
    }

    fun updateResolution(w: Int, h: Int, dpi: Int) {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(
                current.copy(resolutionWidth = w, resolutionHeight = h, dpi = dpi)
            )
        }
    }

    fun toggleRoot() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(current.copy(isRootEnabled = !current.isRootEnabled))
        }
    }

    fun setMockLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            locationProvider.setLocation(vmId, lat, lng)
        }
    }

    fun toggleMockLocation() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(
                current.copy(isMockLocationEnabled = !current.isMockLocationEnabled)
            )
        }
    }

    fun regenerateImei() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(
                current.copy(spoofedImei = VmInstance.generateImei())
            )
        }
    }

    fun regenerateAndroidId() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(
                current.copy(spoofedAndroidId = VmInstance.generateAndroidId())
            )
        }
    }

    fun regenerateSerial() {
        viewModelScope.launch {
            val current = vmInstance.value ?: return@launch
            db.vmInstanceDao().upsert(
                current.copy(spoofedSerial = VmInstance.generateSerial())
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            vfs.clearCache(vmId)
            refreshStorageUsage()
        }
    }

    fun deleteVm(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isDeleting.value = true
            instanceManager.deleteInstance(vmId)
            _isDeleting.value = false
            onComplete()
        }
    }
}
