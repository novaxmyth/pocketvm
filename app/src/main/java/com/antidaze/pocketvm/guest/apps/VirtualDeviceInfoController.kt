package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.model.VirtualInstance

class VirtualDeviceInfoController(
    private val context: Context,
    private val instance: VirtualInstance
) {
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

        val title = TextView(context).apply {
            text = "Virtual Device Specifications"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        addHeader(layout, "PROCESSOR & MEMORY")
        val cpuCard = createCard(layout)
        addProperty(cpuCard, "Architecture", "ARM64-v8a (64-bit)")
        addProperty(cpuCard, "CPU Cores", "${instance.cpuCores} Cores Allocated")
        addProperty(cpuCard, "RAM Allocated", "${instance.ramMb} MB")
        addProperty(cpuCard, "Execution Engine", "Native Bionic / ART Runtime (Zero Emulation)")

        addHeader(layout, "DISPLAY & GRAPHICS")
        val gpuCard = createCard(layout)
        addProperty(gpuCard, "Resolution", "${instance.displayConfig.width} x ${instance.displayConfig.height}")
        addProperty(gpuCard, "Pixel Density", "${instance.displayConfig.dpi} DPI")
        addProperty(gpuCard, "Refresh Rate", "${instance.displayConfig.refreshRateHz} Hz")
        addProperty(gpuCard, "GPU Renderer", "Hardware Accelerated Vulkan / OpenGL ES 3.2")

        addHeader(layout, "DEVICE IDENTITY")
        val idCard = createCard(layout)
        addProperty(idCard, "Brand & Model", "${instance.deviceProfile.manufacturer} ${instance.deviceProfile.model}")
        addProperty(idCard, "Hardware Board", instance.deviceProfile.board)
        addProperty(idCard, "Virtual IMEI", instance.deviceProfile.imei)
        addProperty(idCard, "Android ID", instance.deviceProfile.androidId)
        addProperty(idCard, "MAC Address", instance.deviceProfile.macAddress)

        return root
    }

    private fun addHeader(parent: LinearLayout, title: String) {
        val tv = TextView(context).apply {
            text = title
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8, 20, 8, 8)
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
            ).apply { bottomMargin = 16 }
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
