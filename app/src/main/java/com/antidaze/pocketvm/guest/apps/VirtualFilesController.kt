package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.core.VirtualPackageManager
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton
import java.io.File

class VirtualFilesController(
    private val context: Context,
    private val instance: VirtualInstance,
    private val onAppInstalled: () -> Unit
) {
    private val vmManager = VirtualInstanceManager.get(context)
    private val pm = VirtualPackageManager.get(context, instance.id)
    private var currentDir: File = vmManager.getVmSubdir(instance.id, "sdcard")

    fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            setPadding(24, 24, 24, 24)
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val btnUp = ImageButton(context).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(R.drawable.bg_chip)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                val parent = currentDir.parentFile
                val vmRoot = vmManager.getVmDir(instance.id)
                if (parent != null && parent.absolutePath.startsWith(vmRoot.absolutePath)) {
                    currentDir = parent
                    refresh(root)
                }
            }
        }
        header.addView(btnUp)

        val pathText = TextView(context).apply {
            id = View.generateViewId()
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(16, 0, 16, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(pathText)

        val btnNewFolder = MaterialButton(context).apply {
            text = "+ Folder"
            textSize = 11f
            setOnClickListener { showNewFolderDialog(root) }
        }
        header.addView(btnNewFolder)

        root.addView(header)

        // File list scroll view
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val fileListContainer = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(fileListContainer)
        root.addView(scrollView)

        populateFiles(root, pathText, fileListContainer)
        return root
    }

    private fun refresh(root: LinearLayout) {
        val pathText = root.getChildAt(0) as? LinearLayout
        val tv = pathText?.getChildAt(1) as? TextView
        val sv = root.getChildAt(1) as? ScrollView
        val container = sv?.getChildAt(0) as? LinearLayout
        if (tv != null && container != null) {
            populateFiles(root, tv, container)
        }
    }

    private fun populateFiles(root: LinearLayout, pathText: TextView, container: LinearLayout) {
        val vmRoot = vmManager.getVmDir(instance.id)
        val rel = currentDir.relativeToOrNull(vmRoot)?.path ?: currentDir.name
        pathText.text = "/$rel"

        container.removeAllViews()

        val files = currentDir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

        if (files.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Folder is empty"
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(16, 32, 16, 32)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(emptyTv)
            return
        }

        for (file in files) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(20, 16, 20, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { rightMargin = 20 }
                if (file.isDirectory) {
                    setImageResource(R.drawable.ic_folder)
                    setColorFilter(ContextCompat.getColor(context, R.color.primary))
                } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                    setImageResource(R.drawable.ic_apps)
                    setColorFilter(ContextCompat.getColor(context, R.color.primary))
                } else {
                    setImageResource(R.drawable.ic_phone_android)
                    setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }
            item.addView(icon)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameTv = TextView(context).apply {
                text = file.name
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            val sizeTv = TextView(context).apply {
                text = if (file.isDirectory) "Directory" else "${(file.length() / 1024).coerceAtLeast(1)} KB"
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            info.addView(nameTv)
            info.addView(sizeTv)
            item.addView(info)

            item.setOnClickListener {
                if (file.isDirectory) {
                    currentDir = file
                    refresh(root)
                } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                    confirmInstallApk(file)
                } else {
                    Toast.makeText(context, "File: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }

            container.addView(item)
        }
    }

    private fun confirmInstallApk(file: File) {
        AlertDialog.Builder(context)
            .setTitle("Install Application")
            .setMessage("Do you want to install '${file.name}' into this virtual machine?")
            .setPositiveButton("Install") { _, _ ->
                val res = pm.installApk(file)
                if (res.isSuccess) {
                    Toast.makeText(context, "Installed ${res.getOrNull()?.appLabel} successfully!", Toast.LENGTH_SHORT).show()
                    onAppInstalled()
                } else {
                    Toast.makeText(context, "Installation failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewFolderDialog(root: LinearLayout) {
        val input = EditText(context).apply {
            hint = "Folder Name"
            setSingleLine()
        }
        AlertDialog.Builder(context)
            .setTitle("Create Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    File(currentDir, name).mkdirs()
                    refresh(root)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
