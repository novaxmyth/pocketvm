package com.antidaze.pocketvm.ui

import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmRepository
import com.antidaze.pocketvm.engine.QemuEngine
import com.antidaze.pocketvm.engine.VmForegroundService
import com.antidaze.pocketvm.engine.VmManager
import com.antidaze.pocketvm.guest.AdbClient
import com.antidaze.pocketvm.guest.ScrcpyClient
import com.antidaze.pocketvm.vnc.RfbClient
import com.antidaze.pocketvm.vnc.VncView
import com.antidaze.pocketvm.vnc.Keysyms
import kotlin.concurrent.thread

/** Fullscreen VM console: renders the guest display, sends input. */
class ConsoleActivity : AppCompatActivity() {

    private lateinit var repo: VmRepository
    private lateinit var vmId: String
    private var vmName: String = "VM"
    private var guest: String = "linux"
    private var engine: QemuEngine? = null
    private var client: RfbClient? = null
    private var scrcpy: ScrcpyClient? = null
    private var adb: AdbClient? = null
    private var stopping = false
    @Volatile private var surfaceReady: Surface? = null

    private lateinit var vncView: VncView
    private lateinit var statusText: TextView

    private val exitListener = { id: String, code: Int ->
        if (id == vmId) {
            runOnUiThread {
                statusText.text = getString(R.string.console_exited, code)
                Toast.makeText(this, getString(R.string.console_exited, code), Toast.LENGTH_LONG).show()
            }
        }
        Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_console)

        repo = VmRepository(this)
        vmId = intent.getStringExtra(EXTRA_VM_ID) ?: finish().let { return }
        vmName = intent.getStringExtra(EXTRA_VM_NAME) ?: vmId
        guest = intent.getStringExtra(EXTRA_VM_GUEST) ?: "linux"
        title = vmName

        vncView = findViewById(R.id.vnc_view)
        statusText = findViewById(R.id.console_status)

        vncView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                surfaceReady = Surface(holder.surface)
            }
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) { }
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                surfaceReady = null
            }
        })

        val cfg = repo.listVms().firstOrNull { it.id == vmId }
        if (guest == "android" || cfg?.guest == "android") {
            findViewById<Button>(R.id.btn_esc).visibility = View.GONE
            findViewById<Button>(R.id.btn_tab).visibility = View.GONE
            findViewById<Button>(R.id.btn_ctrl).visibility = View.GONE
            findViewById<Button>(R.id.btn_alt).visibility = View.GONE
            findViewById<Button>(R.id.btn_keyboard).visibility = View.GONE
            findViewById<Button>(R.id.btn_back).visibility = View.VISIBLE
            findViewById<Button>(R.id.btn_home).visibility = View.VISIBLE
            findViewById<Button>(R.id.btn_back).setOnClickListener {
                scrcpy?.sendKeycode(4, true); scrcpy?.sendKeycode(4, false)
            }
            findViewById<Button>(R.id.btn_home).setOnClickListener {
                scrcpy?.sendKeycode(3, true); scrcpy?.sendKeycode(3, false)
            }
        } else {
            findViewById<Button>(R.id.btn_esc).setOnClickListener { vncView.sendKeySym(Keysyms.ESCAPE) }
            findViewById<Button>(R.id.btn_tab).setOnClickListener { vncView.sendKeySym(Keysyms.TAB) }
            findViewById<Button>(R.id.btn_ctrl).setOnClickListener { vncView.sendKeySym(Keysyms.CTRL_L) }
            findViewById<Button>(R.id.btn_alt).setOnClickListener { vncView.sendKeySym(Keysyms.ALT_L) }
            findViewById<Button>(R.id.btn_keyboard).setOnClickListener { vncView.toggleKeyboard() }
        }
        findViewById<Button>(R.id.btn_fullscreen).setOnClickListener { toggleImmersive() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { confirmStop() }

        VmManager.addExitListener(exitListener)
        ensureEngineRunning()
        if (guest == "android" || cfg?.guest == "android") {
            startAndroidConsole()
        } else {
            connectVnc()
        }
    }

    private fun ensureEngineRunning() {
        var e = VmManager.get(vmId)
        if (e == null) {
            val cfg = repo.listVms().firstOrNull { it.id == vmId }
            if (cfg == null) {
                Toast.makeText(this, R.string.console_vm_missing, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            e = QemuEngine(repo, cfg)
            VmManager.put(vmId, e)
        }
        engine = e
        if (!e.running) {
            statusText.text = getString(R.string.console_status_starting)
            try {
                e.start(applicationContext)
                VmForegroundService.start(this, vmId, vmName)
            } catch (ex: Exception) {
                statusText.text = getString(R.string.console_start_failed, ex.message ?: "")
                Toast.makeText(this, ex.message ?: "start failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun connectVnc() {
        val e = engine ?: return
        val c = RfbClient("127.0.0.1", e.vncPort, object : RfbClient.Listener {
            override fun onFramebufferSize(width: Int, height: Int) {
                runOnUiThread { vncView.setFramebufferSize(width, height) }
            }
            override fun onFrame(bitmap: android.graphics.Bitmap) {
                vncView.showFrame(bitmap)
            }
            override fun onStatus(status: String) {
                runOnUiThread { statusText.text = status }
            }
            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    if (!stopping) statusText.text = getString(R.string.console_disconnected, reason)
                }
            }
        })
        vncView.attach(c)
        client = c
        c.connect()
    }

    /** Android guest: wait for guest adb over slirp, then stream the UI via scrcpy. */
    private fun startAndroidConsole() {
        thread(name = "android-console") {
            val surface = waitForSurface() ?: return@thread
            val serverJar = try {
                assets.open("scrcpy-server.jar").use { it.readBytes() }
            } catch (e: Exception) {
                postStatus(getString(R.string.console_android_noserver))
                return@thread
            }
            val deadline = System.currentTimeMillis() + 8 * 60 * 1000L
            while (System.currentTimeMillis() < deadline && !stopping) {
                val e = engine ?: break
                if (e.vncPort <= 0) { Thread.sleep(1000); continue }
                postStatus(getString(R.string.console_android_booting))
                val adbClient = AdbClient("127.0.0.1", 5556)
                try {
                    adbClient.connect()
                } catch (ex: Exception) {
                    adbClient.close()
                    Thread.sleep(4000)
                    continue
                }
                adb = adbClient
                val sc = ScrcpyClient(adbClient, serverJar, surface, object : ScrcpyClient.Listener {
                    override fun onStatus(status: String) = postStatus(status)
                    override fun onVideoSize(width: Int, height: Int) {
                        runOnUiThread {
                            vncView.setFramebufferSize(width, height)
                            vncView.touchDelegate = { action, x, y ->
                                sc.sendTouch(action, x, y)
                            }
                        }
                    }
                    override fun onFatal(reason: String) = postStatus(reason)
                })
                scrcpy = sc
                sc.start()
                adbClient.close()
                if (!stopping) Thread.sleep(4000)
            }
        }
    }

    private fun waitForSurface(): Surface? {
        var waited = 0L
        while (surfaceReady == null && waited < 10_000) {
            Thread.sleep(100); waited += 100
        }
        return surfaceReady
    }

    private fun postStatus(s: String) = runOnUiThread { statusText.text = s }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle(R.string.stop_title)
            .setMessage(getString(R.string.stop_message, vmName))
            .setPositiveButton(R.string.stop_yes) { _, _ ->
                stopping = true
                statusText.text = getString(R.string.console_stopping)
                scrcpy?.stop()
                VmManager.get(vmId)?.stop()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleImmersive() {
        val decor = window.decorView
        val flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        decor.systemUiVisibility = if (decor.systemUiVisibility and flags == flags) 0 else flags
    }

    override fun onResume() {
        super.onResume()
        toggleImmersive()
    }

    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        if (guest == "android") {
            val down = e.action == android.view.KeyEvent.ACTION_DOWN
            val up = e.action == android.view.KeyEvent.ACTION_UP
            if (down || up) {
                scrcpy?.sendKeycode(e.keyCode, down)
                return true
            }
        } else {
            if (e.action == android.view.KeyEvent.ACTION_DOWN ||
                e.action == android.view.KeyEvent.ACTION_UP
            ) {
                if (vncView.handleKeyEvent(e)) return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onDestroy() {
        VmManager.removeExitListener(exitListener)
        client?.disconnect()
        client = null
        scrcpy?.stop()
        scrcpy = null
        adb?.close()
        adb = null
        // The VM itself keeps running in the foreground service.
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VM_ID = "vm_id"
        const val EXTRA_VM_NAME = "vm_name"
        const val EXTRA_VM_GUEST = "vm_guest"
    }
}
