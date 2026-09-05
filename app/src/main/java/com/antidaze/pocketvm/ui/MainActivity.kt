package com.antidaze.pocketvm.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmRepository
import com.antidaze.pocketvm.engine.RuntimeInstaller
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repo: VmRepository
    private lateinit var adapter: VmAdapter
    private var pollJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = VmRepository(this)
        adapter = VmAdapter(lifecycleScope) { cfg ->
            startActivity(
                Intent(this, ConsoleActivity::class.java)
                    .putExtra(ConsoleActivity.EXTRA_VM_ID, cfg.id)
                    .putExtra(ConsoleActivity.EXTRA_VM_NAME, cfg.name)
                    .putExtra(ConsoleActivity.EXTRA_VM_GUEST, cfg.guest)
            )
        }
        adapter.setRepo(repo)

        val list = findViewById<RecyclerView>(R.id.vm_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            CreateVmDialogFragment().show(supportFragmentManager, "create")
        }

        findViewById<TextView>(R.id.btn_diagnostics).setOnClickListener {
            startActivity(android.content.Intent(this, DiagnosticsActivity::class.java))
        }

        if (!RuntimeInstaller.isArm64()) {
            findViewById<TextView>(R.id.abi_warning).visibility = View.VISIBLE
        }

        // Downloads interrupted by an app update/restart resume automatically.
        com.antidaze.pocketvm.engine.DownloadService.recoverPending(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        startPolling()
    }

    override fun onPause() {
        pollJob?.cancel()
        pollJob = null
        super.onPause()
    }

    /**
     * Live state on the main page: boot in the background and the list turns
     * green once the guest is actually up (adbd answers for Android guests).
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                try {
                    val states = mutableMapOf<String, String>()
                    for (vm in repo.listVms()) {
                        val running = com.antidaze.pocketvm.engine.VmManager.get(vm.id)?.running == true
                        states[vm.id] = when {
                            vm.preparing -> "downloading"
                            !running -> "stopped"
                            vm.guest == "android" ->
                                if (com.antidaze.pocketvm.guest.GuestProbes.adbAlive()) "ready" else "booting"
                            else -> "running"
                        }
                    }
                    adapter.submitStates(states)
                } catch (e: Exception) { /* transient */ }
            }
        }
    }

    fun refresh() {
        lifecycleScope.launch(Dispatchers.Main) {
            val items = repo.listVms()
            adapter.submit(items)
            findViewById<TextView>(R.id.empty_state).visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
