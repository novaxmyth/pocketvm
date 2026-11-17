package com.antidaze.pocketvm.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antidaze.pocketvm.R
import com.google.android.material.appbar.MaterialToolbar

class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_diagnostics)
        toolbar.setNavigationOnClickListener { finish() }

        val textHostSpecs = findViewById<TextView>(R.id.diag_host_specs)
        textHostSpecs.text = buildHostSpecs()

        val textEngineDetails = findViewById<TextView>(R.id.diag_engine_details)
        textEngineDetails.text = """
            Engine: PocketVM Native Virtual Instance Engine 2.0
            Architecture: Zero-Overhead Sandboxed Container
            Execution Speed: 100% Native ARM64/x86_64 CPU Speed
            Graphics Pipeline: Direct EGL / SurfaceView Hardware Accelerated (60-120Hz)
            Storage Virtualization: Sandboxed App & User Profiles
            System Services: Emulated Package Manager, Shell & System Properties
        """.trimIndent()
    }

    private fun buildHostSpecs(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

        val abis = Build.SUPPORTED_ABIS.joinToString(", ")

        return """
            Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (${Build.PRODUCT})
            Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            CPU ABIs: $abis
            System RAM: $availRamMb MB free / $totalRamMb MB total
            Free Storage: ${String.format("%.1f", freeStorageMb / 1024.0)} GB available
            Hardware Board: ${Build.BOARD} (${Build.HARDWARE})
        """.trimIndent()
    }
}
