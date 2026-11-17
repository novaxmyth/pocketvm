package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualShellExecutor
import com.antidaze.pocketvm.model.VirtualInstance
import com.google.android.material.button.MaterialButton

class VirtualTerminalController(
    private val context: Context,
    private val instance: VirtualInstance
) {
    private val shell = VirtualShellExecutor(context, instance)

    fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#090C10"))
            setPadding(16, 16, 16, 16)
        }

        // Terminal Output Screen
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val outputText = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Color.parseColor("#E6EDF3"))
            setPadding(8, 8, 8, 8)
        }
        scrollView.addView(outputText)
        root.addView(scrollView)

        // Banner text
        val banner = SpannableStringBuilder()
        banner.append("PocketVM Terminal Emulator [Version 2.0.0]\n", ForegroundColorSpan(Color.parseColor("#00E5FF")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        banner.append("Linux 5.15.148-android-pocketvm+ aarch64\n", ForegroundColorSpan(Color.parseColor("#8B949E")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        banner.append("Type 'help' for built-in toybox commands, or 'su' for root.\n\n", ForegroundColorSpan(Color.parseColor("#8B949E")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        banner.append(shell.prompt, ForegroundColorSpan(Color.parseColor("#3FB950")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        outputText.text = banner

        // Shortcut Button Bar
        val shortcutBar = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8; bottomMargin = 8 }
        }
        val shortcutLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        shortcutBar.addView(shortcutLayout)

        val shortcuts = listOf("ls -la", "cd ~", "pwd", "whoami", "su", "getprop", "pm list", "df", "free", "clear")
        var inputField: EditText? = null

        for (sc in shortcuts) {
            val btn = MaterialButton(context).apply {
                text = sc
                textSize = 10f
                setPadding(16, 0, 16, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    64
                ).apply { rightMargin = 8 }
                setOnClickListener {
                    if (sc == "clear") {
                        outputText.text = shell.prompt
                    } else {
                        executeCommand(sc, outputText, scrollView)
                    }
                }
            }
            shortcutLayout.addView(btn)
        }
        root.addView(shortcutBar)

        // Input Line
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val promptTv = TextView(context).apply {
            text = "$"
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(Color.parseColor("#3FB950"))
            setPadding(4, 0, 8, 0)
        }
        inputRow.addView(promptTv)

        val editInput = EditText(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(Color.WHITE)
            hint = "enter command…"
            setSingleLine()
            background = null
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputField = editInput
        inputRow.addView(editInput)

        val btnSend = ImageButton(context).apply {
            setImageResource(R.drawable.ic_play)
            setColorFilter(ContextCompat.getColor(context, R.color.primary))
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(40, 40)
            setOnClickListener {
                val cmd = editInput.text.toString()
                if (cmd.isNotBlank()) {
                    executeCommand(cmd, outputText, scrollView)
                    editInput.setText("")
                }
            }
        }
        inputRow.addView(btnSend)
        root.addView(inputRow)

        editInput.setOnEditorActionListener { _, _, _ ->
            val cmd = editInput.text.toString()
            if (cmd.isNotBlank()) {
                executeCommand(cmd, outputText, scrollView)
                editInput.setText("")
            }
            true
        }

        return root
    }

    private fun executeCommand(cmd: String, outputText: TextView, scrollView: ScrollView) {
        if (cmd.trim() == "clear") {
            outputText.text = shell.prompt
            return
        }

        val result = shell.execute(cmd)
        val sb = SpannableStringBuilder(outputText.text)
        sb.append(cmd).append("\n")
        if (result.isNotEmpty()) {
            val color = if (result.contains("error") || result.contains("failed") || result.contains("denied")) {
                Color.parseColor("#F85149")
            } else {
                Color.parseColor("#E6EDF3")
            }
            sb.append(result, ForegroundColorSpan(color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
        }
        sb.append(shell.prompt, ForegroundColorSpan(if (shell.isRootSession) Color.parseColor("#FF3366") else Color.parseColor("#3FB950")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        outputText.text = sb
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
