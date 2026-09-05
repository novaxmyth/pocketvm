package com.antidaze.pocketvm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmConfig
import com.antidaze.pocketvm.engine.RuntimeInstaller
import com.antidaze.pocketvm.engine.VmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VmAdapter(
    private val scope: CoroutineScope,
    private val onStart: (VmConfig) -> Unit
) : RecyclerView.Adapter<VmAdapter.Holder>() {

    private val items = mutableListOf<VmConfig>()
    private var repo: com.antidaze.pocketvm.data.VmRepository? = null

    fun setRepo(r: com.antidaze.pocketvm.data.VmRepository) { repo = r }

    fun submit(list: List<VmConfig>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.vm_title)
        val subtitle: TextView = v.findViewById(R.id.vm_subtitle)
        val start: Button = v.findViewById(R.id.vm_start)
        val delete: Button = v.findViewById(R.id.vm_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_vm, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val cfg = items[position]
        val ctx = h.itemView.context
        val running = VmManager.get(cfg.id)?.running == true
        val memLabel = if (cfg.ramMb >= 1024 && cfg.ramMb % 1024 == 0) "${cfg.ramMb / 1024} GB" else "${cfg.ramMb} MB"
        val state = when {
            cfg.preparing -> ctx.getString(R.string.state_downloading)
            cfg.statusNote.isNotEmpty() -> cfg.statusNote
            running -> ctx.getString(R.string.state_running)
            else -> ctx.getString(R.string.state_stopped)
        }
        h.title.text = cfg.name
        h.subtitle.text = ctx.getString(
            R.string.vm_subtitle,
            memLabel,
            cfg.cpus,
            state
        )
        h.start.text = ctx.getString(if (running) R.string.vm_open else R.string.vm_start)

        h.start.setOnClickListener {
            if (!RuntimeInstaller.isArm64()) {
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.no_arm64_title)
                    .setMessage(R.string.no_arm64_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@setOnClickListener
            }
            val r = repo ?: return@setOnClickListener
            installEngineIfNeeded(h, r) { onStart(cfg) }
        }

        h.delete.setOnClickListener {
            if (running) {
                Toast.makeText(ctx, R.string.delete_running_block, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.delete_title)
                .setMessage(ctx.getString(R.string.delete_message, cfg.name))
                .setPositiveButton(R.string.delete_yes) { _, _ ->
                    r_delete(cfg)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun r_delete(cfg: VmConfig) {
        val r = repo ?: return
        scope.launch(Dispatchers.IO) {
            r.deleteVm(cfg.id)
            withContext(Dispatchers.Main) { submit(items.filterNot { it.id == cfg.id }) }
        }
    }

    private fun installEngineIfNeeded(h: Holder, r: com.antidaze.pocketvm.data.VmRepository, then: () -> Unit) {
        val ctx = h.itemView.context
        val version = RuntimeInstaller.bundledVersion(ctx)
        if (RuntimeInstaller.isInstalled(ctx, version)) {
            then()
            return
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.engine_install_title)
            .setMessage(R.string.engine_install_message)
            .setCancelable(false)
            .create()
        dialog.show()
        scope.launch(Dispatchers.IO) {
            try {
                RuntimeInstaller.install(ctx)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    then()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.engine_install_failed)
                        .setMessage(e.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }
}
