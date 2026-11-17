package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class VirtualSensorsController(
    private val context: Context,
    private val instance: VirtualInstance,
    private val onSensorsChanged: () -> Unit
) {
    private val vmManager = VirtualInstanceManager.get(context)

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
            text = "Virtual Sensors & Mock GPS Lab"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        // Mock GPS Section
        addHeader(layout, "MOCK GPS LOCATION INJECTION")
        val gpsCard = createCard(layout)

        val gpsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val gpsTv = TextView(context).apply {
            text = "Enable Mock Location"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val gpsSwitch = SwitchMaterial(context).apply {
            isChecked = instance.sensorConfig.mockGpsEnabled
            setOnCheckedChangeListener { _, checked ->
                instance.sensorConfig.mockGpsEnabled = checked
                vmManager.saveInstance(instance)
                Toast.makeText(context, "Mock Location: $checked", Toast.LENGTH_SHORT).show()
                onSensorsChanged()
            }
        }
        gpsRow.addView(gpsTv)
        gpsRow.addView(gpsSwitch)
        gpsCard.addView(gpsRow)

        val editLat = EditText(context).apply {
            hint = "Latitude"
            setText(instance.sensorConfig.latitude.toString())
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
        }
        val editLng = EditText(context).apply {
            hint = "Longitude"
            setText(instance.sensorConfig.longitude.toString())
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
        }
        gpsCard.addView(editLat)
        gpsCard.addView(editLng)

        // Presets row
        val presetsBar = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
        }
        val presetsLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        presetsBar.addView(presetsLayout)

        val cities = listOf(
            Pair("San Francisco", Pair(37.7749, -122.4194)),
            Pair("Tokyo", Pair(35.6762, 139.6503)),
            Pair("London", Pair(51.5074, -0.1278)),
            Pair("Paris", Pair(48.8566, 2.3522)),
            Pair("New York", Pair(40.7128, -74.0060))
        )
        for (city in cities) {
            val btn = MaterialButton(context).apply {
                text = city.first
                textSize = 10f
                setPadding(16, 0, 16, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 64).apply { rightMargin = 8 }
                setOnClickListener {
                    editLat.setText(city.second.first.toString())
                    editLng.setText(city.second.second.toString())
                    instance.sensorConfig.latitude = city.second.first
                    instance.sensorConfig.longitude = city.second.second
                    vmManager.saveInstance(instance)
                    Toast.makeText(context, "Location set to ${city.first}", Toast.LENGTH_SHORT).show()
                    onSensorsChanged()
                }
            }
            presetsLayout.addView(btn)
        }
        gpsCard.addView(presetsBar)

        // Battery Simulator Section
        addHeader(layout, "MOCK BATTERY SIMULATOR")
        val battCard = createCard(layout)

        val battText = TextView(context).apply {
            text = "Battery Level: ${instance.sensorConfig.mockBatteryLevel}%"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        battCard.addView(battText)

        val slider = Slider(context).apply {
            valueFrom = 1f
            valueTo = 100f
            value = instance.sensorConfig.mockBatteryLevel.toFloat()
            addOnChangeListener { _, value, _ ->
                val level = value.toInt()
                instance.sensorConfig.mockBatteryLevel = level
                battText.text = "Battery Level: $level%"
                vmManager.saveInstance(instance)
                onSensorsChanged()
            }
        }
        battCard.addView(slider)

        val chargeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val chargeTv = TextView(context).apply {
            text = "Simulate Charging (AC/USB)"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val chargeSwitch = SwitchMaterial(context).apply {
            isChecked = instance.sensorConfig.mockIsCharging
            setOnCheckedChangeListener { _, checked ->
                instance.sensorConfig.mockIsCharging = checked
                vmManager.saveInstance(instance)
                onSensorsChanged()
            }
        }
        chargeRow.addView(chargeTv)
        chargeRow.addView(chargeSwitch)
        battCard.addView(chargeRow)

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
}
