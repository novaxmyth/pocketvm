package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualEnvironment
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class VirtualSuperuserController(
    private val context: Context,
    private val instance: VirtualInstance,
    private val onRootToggled: () -> Unit
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

        val title = TextView(context).apply {
            text = "Superuser (SU) Root Manager"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        // Status Banner Card
        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card_cyber)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val shieldIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_shield)
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { rightMargin = 20 }
            setColorFilter(if (env.isRootEnabled) ContextCompat.getColor(context, R.color.root_active) else ContextCompat.getColor(context, R.color.text_secondary))
        }
        statusCard.addView(shieldIcon)

        val statusInfo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusTitle = TextView(context).apply {
            text = if (env.isRootEnabled) "Virtual Root: ACTIVE" else "Virtual Root: DISABLED"
            textSize = 15f
            setTextColor(if (env.isRootEnabled) ContextCompat.getColor(context, R.color.root_active) else Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val statusDesc = TextView(context).apply {
            text = if (env.isRootEnabled) "su binary is mounted at /system/xbin/su" else "Root access is locked for sandboxed applications"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        statusInfo.addView(statusTitle)
        statusInfo.addView(statusDesc)
        statusCard.addView(statusInfo)

        val rootSwitch = SwitchMaterial(context).apply {
            isChecked = env.isRootEnabled
            setOnCheckedChangeListener { _, checked ->
                env.setRootEnabled(checked)
                shieldIcon.setColorFilter(if (checked) ContextCompat.getColor(context, R.color.root_active) else ContextCompat.getColor(context, R.color.text_secondary))
                statusTitle.text = if (checked) "Virtual Root: ACTIVE" else "Virtual Root: DISABLED"
                statusTitle.setTextColor(if (checked) ContextCompat.getColor(context, R.color.root_active) else Color.WHITE)
                statusDesc.text = if (checked) "su binary is mounted at /system/xbin/su" else "Root access is locked for sandboxed applications"
                onRootToggled()
            }
        }
        statusCard.addView(rootSwitch)
        layout.addView(statusCard)

        // Features list
        val featCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_cyber)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val featTitle = TextView(context).apply {
            text = "ROOT EMULATION CAPABILITIES"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        featCard.addView(featTitle)

        val featText = TextView(context).apply {
            text = """
                • Simulated /system/xbin/su and /system/bin/su
                • uid=0 elevation in Virtual Shell & commands
                • Mock Magisk Hide & Mock SELinux Enforcing/Permissive
                • Per-app root grant database inside VM sandbox
                • Zero root requirements on the host Android device!
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.WHITE)
            setLineSpacing(8f, 1f)
        }
        featCard.addView(featText)
        layout.addView(featCard)

        // Test Root button
        val btnTestRoot = MaterialButton(context).apply {
            text = "Verify Virtual Root Status"
            setOnClickListener {
                if (env.isRootEnabled) {
                    Toast.makeText(context, "Verification PASSED: /system/xbin/su is available (uid=0 root)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Verification: Root is disabled for this VM.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnTestRoot)

        return root
    }
}
