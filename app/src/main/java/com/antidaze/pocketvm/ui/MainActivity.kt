package com.antidaze.pocketvm.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.model.VirtualInstance
import com.antidaze.pocketvm.model.VmState
import com.antidaze.pocketvm.ui.vm.VirtualPhoneActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var vmManager: VirtualInstanceManager
    private lateinit var adapter: VmCardAdapter

    private lateinit var recyclerVms: RecyclerView
    private lateinit var viewEmptyState: LinearLayout
    private lateinit var textTotalInstances: TextView
    private lateinit var textStorageUsed: TextView
    private lateinit var badgeActiveVms: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vmManager = VirtualInstanceManager.get(this)

        // Find views
        recyclerVms = findViewById(R.id.recycler_vms)
        viewEmptyState = findViewById(R.id.view_empty_state)
        textTotalInstances = findViewById(R.id.text_total_instances)
        textStorageUsed = findViewById(R.id.text_storage_used)
        badgeActiveVms = findViewById(R.id.badge_active_vms)

        recyclerVms.layoutManager = LinearLayoutManager(this)

        adapter = VmCardAdapter(
            vms = emptyList(),
            onLaunch = { vm -> launchVirtualMachine(vm) },
            onClone = { vm -> cloneVirtualMachine(vm) },
            onDelete = { vm -> confirmDeleteVm(vm) }
        )
        recyclerVms.adapter = adapter

        // Setup bottom navigation
        findViewById<LinearLayout>(R.id.btn_nav_vms).setOnClickListener {
            // Already on VMs dashboard
        }
        findViewById<LinearLayout>(R.id.btn_nav_shared).setOnClickListener {
            startActivity(Intent(this, SharedStorageActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btn_nav_diagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        // FAB
        findViewById<ExtendedFloatingActionButton>(R.id.fab_create_vm).setOnClickListener {
            showCreateDialog()
        }

        // Button in empty state
        findViewById<MaterialButton>(R.id.btn_create_first).setOnClickListener {
            showCreateDialog()
        }

        // Auto-seed a default VM if clean install
        if (vmManager.getAllInstances().isEmpty()) {
            val defaultVm = VirtualInstance(
                name = "Google Pixel 8 Pro",
                ramMb = 2048
            )
            vmManager.createInstance(defaultVm)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val vms = vmManager.getAllInstances()
        adapter.updateData(vms)

        val totalInstances = vms.size
        val runningCount = vms.count { it.state == VmState.RUNNING }
        val totalBytes = vms.sumOf { it.storageUsedBytes }
        val totalMb = totalBytes / (1024 * 1024)

        textTotalInstances.text = totalInstances.toString()
        textStorageUsed.text = if (totalMb > 1024) "${String.format("%.1f", totalMb / 1024.0)} GB" else "$totalMb MB"
        badgeActiveVms.text = "$runningCount Running"

        if (totalInstances == 0) {
            viewEmptyState.visibility = View.VISIBLE
            recyclerVms.visibility = View.GONE
        } else {
            viewEmptyState.visibility = View.GONE
            recyclerVms.visibility = View.VISIBLE
        }
    }

    private fun launchVirtualMachine(vm: VirtualInstance) {
        VirtualPhoneActivity.start(this, vm.id)
    }

    private fun cloneVirtualMachine(vm: VirtualInstance) {
        val newName = "${vm.name} (Clone)"
        val cloned = vmManager.cloneInstance(vm.id, newName)
        if (cloned != null) {
            Toast.makeText(this, "Cloned VM: ${cloned.name}", Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(this, "Failed to clone VM", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteVm(vm: VirtualInstance) {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.confirm_delete_vm, vm.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                vmManager.deleteInstance(vm.id)
                Toast.makeText(this, R.string.toast_vm_deleted, Toast.LENGTH_SHORT).show()
                refreshDashboard()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCreateDialog() {
        val dialog = CreateVmDialog { newVm ->
            refreshDashboard()
            launchVirtualMachine(newVm)
        }
        dialog.show(supportFragmentManager, CreateVmDialog.TAG)
    }
}
