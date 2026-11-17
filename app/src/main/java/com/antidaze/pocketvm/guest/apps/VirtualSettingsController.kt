package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualEnvironment
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.model.DisplayConfig
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class VirtualSettingsController(
    private val context: Context,
    private val instance: VirtualInstance,
    private val onVmUpdated: () -> Unit
) {
    private val vmManager = VirtualInstanceManager.get(context)
    private val env = VirtualEnvironment(context, instance)

    fun createView(): View {
        val root = ScrollView(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            isFillViewport = true
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(layout)

        // Title
        val title = TextView(context).apply {
            text = "Virtual System Settings"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        // Section: About Virtual Phone
        addHeader(layout, "ABOUT VIRTUAL PHONE")
        val aboutCard = createCard(layout)
        addProperty(aboutCard, "Model", instance.deviceProfile.model)
        addProperty(aboutCard, "Manufacturer", instance.deviceProfile.manufacturer)
        addProperty(aboutCard, "Android Version", instance.osProfile.versionName)
        addProperty(aboutCard, "API Level", instance.osProfile.apiLevel.toString())
        addProperty(aboutCard, "Build ID", instance.osProfile.buildId)
        addProperty(aboutCard, "Virtual IMEI", instance.deviceProfile.imei)
        addProperty(aboutCard, "Android ID", instance.deviceProfile.androidId)
        addProperty(aboutCard, "Virtual MAC", instance.deviceProfile.macAddress)

        // Section: Display & Resolution
        addHeader(layout, "DISPLAY & RESOLUTION")
        val dispCard = createCard(layout)
        addProperty(dispCard, "Current Resolution", "${instance.displayConfig.width}x${instance.displayConfig.height}")
        addProperty(dispCard, "Screen Density", "${instance.displayConfig.dpi} DPI")
        addProperty(dispCard, "Refresh Rate", "${instance.displayConfig.refreshRateHz} Hz")

        val btnFhd = MaterialButton(context).apply {
            text = "Switch to 1080x2400 (420 DPI)"
            setOnClickListener {
                instance.displayConfig = DisplayConfig.fhdPlus420()
                vmManager.saveInstance(instance)
                Toast.makeText(context, "Resolution set to 1080x2400", Toast.LENGTH_SHORT).show()
                onVmUpdated()
            }
        }
        dispCard.addView(btnFhd)

        val btnHd = MaterialButton(context).apply {
            text = "Switch to 720x1280 (320 DPI)"
            setOnClickListener {
                instance.displayConfig = DisplayConfig.hd320()
                vmManager.saveInstance(instance)
                Toast.makeText(context, "Resolution set to 720x1280", Toast.LENGTH_SHORT).show()
                onVmUpdated()
            }
        }
        dispCard.addView(btnHd)

        // Section: Root & Superuser
        addHeader(layout, "ROOT & SUPERUSER PRIVILEGES")
        val rootCard = createCard(layout)
        val rootRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        val rootText = TextView(context).apply {
            text = "Enable Virtual Root (SU)"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rootSwitch = SwitchMaterial(context).apply {
            isChecked = instance.rootConfig.isRootEnabled
            setOnCheckedChangeListener { _, checked ->
                env.setRootEnabled(checked)
                val status = if (checked) "enabled" else "disabled"
                Toast.makeText(context, "Virtual Root $status", Toast.LENGTH_SHORT).show()
                onVmUpdated()
            }
        }
        rootRow.addView(rootText)
        rootRow.addView(rootSwitch)
        rootCard.addView(rootRow)

        // Section: Storage & Maintenance
        addHeader(layout, "STORAGE & SYSTEM MAINTENANCE")
        val storageCard = createCard(layout)
        val usedMb = instance.storageUsedBytes / (1024 * 1024)
        addProperty(storageCard, "Storage Used", "$usedMb MB of ${instance.storageQuotaMb} MB")

        val btnCleanCache = MaterialButton(context).apply {
            text = "Clean Virtual Instance Cache"
            setOnClickListener {
                val freed = vmManager.cleanInstanceCache(instance.id)
                val freedMb = freed / (1024 * 1024)
                Toast.makeText(context, "Cleaned $freedMb MB cache", Toast.LENGTH_SHORT).show()
                onVmUpdated()
            }
        }
        storageCard.addView(btnCleanCache)

        return root
    }

    private fun addHeader(parent: LinearLayout, title: String) {
        val tv = TextView(context).apply {
            text = title
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8, 24, 8, 8)
        }
        parent.addView(tv)
    }

    private fun createCard(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_cyber)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        parent.addView(card)
        return card
    }

    private fun addProperty(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val l = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val v = TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(l)
        row.addView(v)
        parent.addView(row)
    }
}
