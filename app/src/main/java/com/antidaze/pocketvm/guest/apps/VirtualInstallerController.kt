package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualPackageManager
import com.antidaze.pocketvm.model.VirtualAppInfo
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton

class VirtualInstallerController(
    private val context: Context,
    private val instance: VirtualInstance,
    private val onAppListChanged: () -> Unit
) {
    private val pm = VirtualPackageManager.get(context, instance.id)

    fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            setPadding(24, 24, 24, 24)
        }

        // Title
        val title = TextView(context).apply {
            text = "App Center & Host Cloner"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        // Segment Switcher (Clone Host Apps / Installed in VM)
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }

        val btnTabClone = MaterialButton(context).apply {
            text = "Clone from Host"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
        }

        val btnTabInstalled = MaterialButton(context).apply {
            text = "Installed Apps"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabLayout.addView(btnTabClone)
        tabLayout.addView(btnTabInstalled)
        root.addView(tabLayout)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        fun showCloneTab() {
            btnTabClone.setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
            btnTabClone.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            btnTabInstalled.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            btnTabInstalled.setTextColor(Color.WHITE)
            populateHostApps(contentContainer)
        }

        fun showInstalledTab() {
            btnTabInstalled.setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
            btnTabInstalled.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            btnTabClone.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            btnTabClone.setTextColor(Color.WHITE)
            populateInstalledApps(contentContainer)
        }

        btnTabClone.setOnClickListener { showCloneTab() }
        btnTabInstalled.setOnClickListener { showInstalledTab() }

        showCloneTab()
        return root
    }

    private fun populateHostApps(container: LinearLayout) {
        container.removeAllViews()
        val hostPm = context.packageManager
        val installed = hostPm.getInstalledApplications(0).filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName == "com.android.chrome"
        }.sortedBy { it.loadLabel(hostPm).toString().lowercase() }

        val infoTv = TextView(context).apply {
            text = "Tap 'Clone' to run an isolated instance of any app in this VM:"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 0, 0, 16)
        }
        container.addView(infoTv)

        for (app in installed) {
            val label = app.loadLabel(hostPm).toString()
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { rightMargin = 16 }
                setImageDrawable(app.loadIcon(hostPm))
            }
            row.addView(icon)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameTv = TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            val pkgTv = TextView(context).apply {
                text = app.packageName
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            info.addView(nameTv)
            info.addView(pkgTv)
            row.addView(info)

            val isAlreadyInstalled = pm.getApp(app.packageName) != null
            val btnClone = MaterialButton(context).apply {
                text = if (isAlreadyInstalled) "Cloned" else "Clone"
                isEnabled = !isAlreadyInstalled
                textSize = 11f
                setOnClickListener {
                    isEnabled = false
                    text = "Cloning…"
                    val res = pm.cloneFromHost(app.packageName)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Cloned '$label' into VM!", Toast.LENGTH_SHORT).show()
                        text = "Cloned"
                        onAppListChanged()
                    } else {
                        Toast.makeText(context, "Clone failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        isEnabled = true
                        text = "Clone"
                    }
                }
            }
            row.addView(btnClone)
            container.addView(row)
        }
    }

    private fun populateInstalledApps(container: LinearLayout) {
        container.removeAllViews()
        val installed = pm.getInstalledApps()

        for (app in installed) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { rightMargin = 16 }
                if (app.iconResId != 0) {
                    setImageResource(app.iconResId)
                } else {
                    setImageResource(R.drawable.ic_apps)
                }
                setColorFilter(ContextCompat.getColor(context, R.color.primary))
            }
            row.addView(icon)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameTv = TextView(context).apply {
                text = app.appLabel
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            val verTv = TextView(context).apply {
                text = "${app.packageName} • v${app.versionName}"
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            info.addView(nameTv)
            info.addView(verTv)
            row.addView(info)

            if (!app.isSystemApp) {
                val btnUninstall = ImageButton(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(ContextCompat.getColor(context, R.color.status_error))
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        pm.uninstallApp(app.packageName)
                        Toast.makeText(context, "Uninstalled ${app.appLabel}", Toast.LENGTH_SHORT).show()
                        populateInstalledApps(container)
                        onAppListChanged()
                    }
                }
                row.addView(btnUninstall)
            }
            container.addView(row)
        }
    }
}
