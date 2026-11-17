package com.antidaze.pocketvm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.model.VirtualInstance
import com.antidaze.pocketvm.model.VmState
import com.google.android.material.button.MaterialButton

class VmCardAdapter(
    private var vms: List<VirtualInstance>,
    private val onLaunch: (VirtualInstance) -> Unit,
    private val onClone: (VirtualInstance) -> Unit,
    private val onDelete: (VirtualInstance) -> Unit
) : RecyclerView.Adapter<VmCardAdapter.VmViewHolder>() {

    class VmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indicatorStatus: View = view.findViewById(R.id.indicator_status)
        val textStatus: TextView = view.findViewById(R.id.text_status)
        val textRootBadge: TextView = view.findViewById(R.id.text_root_badge)
        val textVmName: TextView = view.findViewById(R.id.text_vm_name)
        val textVmDeviceModel: TextView = view.findViewById(R.id.text_vm_device_model)
        val chipRam: TextView = view.findViewById(R.id.chip_ram)
        val chipDisplay: TextView = view.findViewById(R.id.chip_display)
        val chipStorage: TextView = view.findViewById(R.id.chip_storage)
        val btnActionLaunch: MaterialButton = view.findViewById(R.id.btn_action_launch)
        val btnActionClone: ImageButton = view.findViewById(R.id.btn_action_clone)
        val btnActionDelete: ImageButton = view.findViewById(R.id.btn_action_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vm_card, parent, false)
        return VmViewHolder(view)
    }

    override fun onBindViewHolder(holder: VmViewHolder, position: Int) {
        val vm = vms[position]
        val context = holder.itemView.context

        holder.textVmName.text = vm.name
        holder.textVmDeviceModel.text = "${vm.deviceProfile.manufacturer} ${vm.deviceProfile.model} • ${vm.osProfile.versionName}"

        // State indicator
        if (vm.state == VmState.RUNNING) {
            holder.indicatorStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_running)
            holder.textStatus.text = context.getString(R.string.status_running)
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.status_running))
            holder.btnActionLaunch.text = context.getString(R.string.action_open)
            holder.btnActionLaunch.setIconResource(R.drawable.ic_phone_android)
        } else {
            holder.indicatorStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_stopped)
            holder.textStatus.text = context.getString(R.string.status_stopped)
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            holder.btnActionLaunch.text = context.getString(R.string.action_start)
            holder.btnActionLaunch.setIconResource(R.drawable.ic_play)
        }

        // Root badge
        if (vm.rootConfig.isRootEnabled) {
            holder.textRootBadge.visibility = View.VISIBLE
        } else {
            holder.textRootBadge.visibility = View.GONE
        }

        // Specs chips
        val ramGb = String.format("%.1f", vm.ramMb / 1024.0).removeSuffix(".0")
        holder.chipRam.text = "$ramGb GB RAM"
        holder.chipDisplay.text = "${vm.displayConfig.width}x${vm.displayConfig.height} (${vm.displayConfig.dpi} DPI)"

        val usedMb = vm.storageUsedBytes / (1024 * 1024)
        holder.chipStorage.text = if (usedMb > 1024) "${String.format("%.1f", usedMb / 1024.0)} GB" else "$usedMb MB"

        // Button clicks
        holder.btnActionLaunch.setOnClickListener { onLaunch(vm) }
        holder.btnActionClone.setOnClickListener { onClone(vm) }
        holder.btnActionDelete.setOnClickListener { onDelete(vm) }
    }

    override fun getItemCount(): Int = vms.size

    fun updateData(newVms: List<VirtualInstance>) {
        vms = newVms
        notifyDataSetChanged()
    }
}
