package com.pocketvm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketvm.data.model.DeviceProfile
import com.pocketvm.data.model.VmInstance
import com.pocketvm.instance.InstanceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val instanceManager = InstanceManager.get()

    val instances: StateFlow<List<VmInstance>> = instanceManager
        .observeInstances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createVm(
        name: String,
        profile: DeviceProfile,
        ramMb: Int,
        rootEnabled: Boolean
    ) {
        viewModelScope.launch {
            instanceManager.createInstance(name, profile, ramMb, rootEnabled)
        }
    }

    fun deleteVm(id: String) {
        viewModelScope.launch {
            instanceManager.deleteInstance(id)
        }
    }

    fun bootVm(id: String) {
        viewModelScope.launch {
            instanceManager.bootInstance(id)
        }
    }

    fun shutdownVm(id: String) {
        viewModelScope.launch {
            instanceManager.shutdownInstance(id)
        }
    }

    fun cloneVm(id: String, newName: String) {
        viewModelScope.launch {
            instanceManager.cloneInstance(id, newName)
        }
    }
}
