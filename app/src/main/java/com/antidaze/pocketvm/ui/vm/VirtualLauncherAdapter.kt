package com.antidaze.pocketvm.ui.vm

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.model.VirtualAppInfo
import java.io.File

class VirtualLauncherAdapter(
    private var apps: List<VirtualAppInfo>,
    private val onAppClick: (VirtualAppInfo) -> Unit
) : RecyclerView.Adapter<VirtualLauncherAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desktop_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.label.text = app.appLabel

        if (app.iconResId != 0) {
            holder.icon.setImageResource(app.iconResId)
        } else if (app.iconPath != null && File(app.iconPath).isFile) {
            val bmp = BitmapFactory.decodeFile(app.iconPath)
            holder.icon.setImageBitmap(bmp)
        } else {
            holder.icon.setImageResource(R.drawable.ic_apps)
        }

        holder.itemView.setOnClickListener { onAppClick(app) }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<VirtualAppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
