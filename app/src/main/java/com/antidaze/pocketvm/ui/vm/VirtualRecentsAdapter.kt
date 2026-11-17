package com.antidaze.pocketvm.ui.vm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.model.RecentTask

class VirtualRecentsAdapter(
    private var tasks: List<RecentTask>,
    private val onTaskClick: (RecentTask) -> Unit,
    private val onTaskClose: (RecentTask) -> Unit
) : RecyclerView.Adapter<VirtualRecentsAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.recent_icon)
        val label: TextView = view.findViewById(R.id.recent_label)
        val btnClose: ImageButton = view.findViewById(R.id.recent_btn_close)
        val preview: View = view.findViewById(R.id.recent_preview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.label.text = task.appLabel
        holder.icon.setImageResource(task.iconResId.takeIf { it != 0 } ?: R.drawable.ic_apps)

        holder.itemView.setOnClickListener { onTaskClick(task) }
        holder.btnClose.setOnClickListener { onTaskClose(task) }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<RecentTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}
