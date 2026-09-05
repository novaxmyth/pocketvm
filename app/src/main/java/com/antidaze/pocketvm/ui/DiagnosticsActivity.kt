package com.antidaze.pocketvm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Build
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmRepository
import com.antidaze.pocketvm.engine.RuntimeInstaller
import com.antidaze.pocketvm.engine.VmManager

/** Collects everything needed to debug a "VM doesn't work" report. */
class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        title = getString(R.string.diag_title)

        val text = findViewById<TextView>(R.id.diag_text)
        text.text = buildString {
            appendLine("== PocketVM diagnostics ==")
            appendLine("device abis: " + Build.SUPPORTED_ABIS.joinToString(","))
            appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            val mm = getSystemService(android.app.ActivityManager::class.java)
            val mem = android.app.ActivityManager.MemoryInfo().also { mm.getMemoryInfo(it) }
            appendLine("ram: total ${(mem.totalMem / 1048576)} MB, avail ${(mem.availMem / 1048576)} MB")
            appendLine("engine version: ${RuntimeInstaller.bundledVersion(this@DiagnosticsActivity) ?: "<none bundled>"}")
            appendLine("engine installed: ${RuntimeInstaller.isInstalled(this@DiagnosticsActivity)}")
            appendLine("running VMs: " + VmManager.let { m ->
                val any = m.anyRunning()
                if (any) "yes" else "no"
            })
            appendLine()
            val repo = VmRepository(this@DiagnosticsActivity)
            appendLine("== engine.log (last 60 lines) ==")
            for (vm in repo.listVms()) {
                appendLine("-- VM ${vm.name} (${vm.id}) guest=${vm.guest} --")
                tail(repo.engineLogFile(vm.id), 60).forEach { appendLine(it) }
                appendLine("-- serial.log (last 40 lines) --")
                tail(repo.serialLogFile(vm.id), 40).forEach { appendLine(it) }
            }
        }

        findViewById<Button>(R.id.diag_copy).setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("pocketvm-diagnostics", text.text))
            Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tail(f: java.io.File, lines: Int): List<String> {
        if (!f.isFile) return listOf("<no log file>")
        return try {
            f.readLines().takeLast(lines)
        } catch (e: Exception) {
            listOf("<unreadable: ${e.message}>")
        }
    }
}
