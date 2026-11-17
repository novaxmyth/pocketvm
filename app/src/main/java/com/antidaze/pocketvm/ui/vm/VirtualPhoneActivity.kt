package com.antidaze.pocketvm.ui.vm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.core.VirtualPackageManager
import com.antidaze.pocketvm.guest.apps.*
import com.antidaze.pocketvm.model.RecentTask
import com.antidaze.pocketvm.model.VirtualAppInfo
import com.antidaze.pocketvm.model.VirtualInstance
import com.antidaze.pocketvm.model.VmState
import com.antidaze.pocketvm.service.VirtualFloatingService
import com.antidaze.pocketvm.service.VmBackgroundService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualPhoneActivity : AppCompatActivity() {

    private lateinit var vmManager: VirtualInstanceManager
    private lateinit var instance: VirtualInstance
    private lateinit var pm: VirtualPackageManager

    private val recentTasks = mutableListOf<RecentTask>()
    private var activeAppPackage: String? = null

    // Views
    private lateinit var statusClock: TextView
    private lateinit var statusRootIcon: TextView
    private lateinit var statusBatteryText: TextView
    private lateinit var statusWifiIcon: ImageView
    private lateinit var desktopBigClock: TextView
    private lateinit var desktopDateText: TextView
    private lateinit var editSearchApps: EditText
    private lateinit var recyclerDesktopApps: RecyclerView
    private lateinit var launcherAdapter: VirtualLauncherAdapter

    private lateinit var vmLauncherDesktop: View
    private lateinit var vmRunningAppContainer: FrameLayout
    private lateinit var vmRecentsView: View
    private lateinit var vmQuickSettingsShade: View
    private lateinit var recyclerRecents: RecyclerView
    private lateinit var recentsAdapter: VirtualRecentsAdapter
    private lateinit var vBall: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_virtual_phone)

        val vmId = intent.getStringExtra(EXTRA_VM_ID) ?: finish().let { return }
        vmManager = VirtualInstanceManager.get(this)
        val found = vmManager.getInstance(vmId)
        if (found == null) {
            Toast.makeText(this, "Virtual machine not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        instance = found
        pm = VirtualPackageManager.get(this, instance.id)

        // Mark as running & start background service
        instance.state = VmState.RUNNING
        instance.lastLaunchedAt = System.currentTimeMillis()
        vmManager.saveInstance(instance)
        VmBackgroundService.start(this, instance.name)

        initViews()
        setupClockAndBattery()
        setupLauncherApps()
        setupNavigationAndGestures()
        setupQuickSettings()
        setupDraggableVBall()
    }

    private fun initViews() {
        statusClock = findViewById(R.id.status_clock)
        statusRootIcon = findViewById(R.id.status_root_icon)
        statusBatteryText = findViewById(R.id.status_battery_text)
        statusWifiIcon = findViewById(R.id.status_wifi_icon)
        desktopBigClock = findViewById(R.id.desktop_big_clock)
        desktopDateText = findViewById(R.id.desktop_date_text)
        editSearchApps = findViewById(R.id.edit_search_apps)
        recyclerDesktopApps = findViewById(R.id.recycler_desktop_apps)

        vmLauncherDesktop = findViewById(R.id.vm_launcher_desktop)
        vmRunningAppContainer = findViewById(R.id.vm_running_app_container)
        vmRecentsView = findViewById(R.id.vm_recents_view)
        vmQuickSettingsShade = findViewById(R.id.vm_quick_settings_shade)
        recyclerRecents = findViewById(R.id.recycler_recents)
        vBall = findViewById(R.id.v_ball)

        updateRootAndBatteryStatus()
    }

    private fun setupClockAndBattery() {
        lifecycleScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            while (isActive) {
                val now = Date()
                val timeStr = timeFormat.format(now)
                val dateStr = dateFormat.format(now)

                statusClock.text = timeStr
                desktopBigClock.text = timeStr
                desktopDateText.text = dateStr
                delay(1000)
            }
        }
    }

    fun updateRootAndBatteryStatus() {
        statusRootIcon.visibility = if (instance.rootConfig.isRootEnabled) View.VISIBLE else View.GONE
        statusBatteryText.text = "${instance.sensorConfig.mockBatteryLevel}%"
        if (instance.sensorConfig.mockIsCharging) {
            statusBatteryText.text = "${instance.sensorConfig.mockBatteryLevel}% ⚡"
        }
    }

    private fun setupLauncherApps() {
        recyclerDesktopApps.layoutManager = GridLayoutManager(this, 4)
        launcherAdapter = VirtualLauncherAdapter(pm.getInstalledApps()) { app ->
            launchApp(app)
        }
        recyclerDesktopApps.adapter = launcherAdapter

        editSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) {
                    pm.getInstalledApps()
                } else {
                    pm.getInstalledApps().filter { it.appLabel.contains(query, ignoreCase = true) }
                }
                launcherAdapter.updateApps(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Dock favorites
        findViewById<View>(R.id.dock_app_files).setOnClickListener {
            pm.getApp("com.pocketvm.files")?.let { launchApp(it) }
        }
        findViewById<View>(R.id.dock_app_browser).setOnClickListener {
            pm.getApp("com.pocketvm.browser")?.let { launchApp(it) }
        }
        findViewById<View>(R.id.dock_app_terminal).setOnClickListener {
            pm.getApp("com.pocketvm.terminal")?.let { launchApp(it) }
        }
        findViewById<View>(R.id.dock_app_settings).setOnClickListener {
            pm.getApp("com.pocketvm.settings")?.let { launchApp(it) }
        }
    }

    private fun launchApp(app: VirtualAppInfo) {
        activeAppPackage = app.packageName
        vmRunningAppContainer.removeAllViews()

        val appView: View = when (app.packageName) {
            "com.pocketvm.settings" -> VirtualSettingsController(this, instance) {
                updateRootAndBatteryStatus()
            }.createView()

            "com.pocketvm.files" -> VirtualFilesController(this, instance) {
                launcherAdapter.updateApps(pm.getInstalledApps())
            }.createView()

            "com.pocketvm.browser" -> VirtualBrowserController(this).createView()

            "com.pocketvm.terminal" -> VirtualTerminalController(this, instance).createView()

            "com.pocketvm.installer" -> VirtualInstallerController(this, instance) {
                launcherAdapter.updateApps(pm.getInstalledApps())
            }.createView()

            "com.pocketvm.sensor" -> VirtualSensorsController(this, instance) {
                updateRootAndBatteryStatus()
            }.createView()

            "com.pocketvm.su" -> VirtualSuperuserController(this, instance) {
                updateRootAndBatteryStatus()
            }.createView()

            "com.pocketvm.camera" -> VirtualCameraController(this).createView()

            "com.pocketvm.deviceinfo" -> VirtualDeviceInfoController(this, instance).createView()

            else -> {
                // Cloned or Installed User Application View
                createGenericAppView(app)
            }
        }

        vmRunningAppContainer.addView(appView)
        vmRunningAppContainer.visibility = View.VISIBLE
        vmLauncherDesktop.visibility = View.GONE
        vmRecentsView.visibility = View.GONE
        vmQuickSettingsShade.visibility = View.GONE

        // Track in Recents
        recentTasks.removeAll { it.packageName == app.packageName }
        recentTasks.add(0, RecentTask(
            taskId = app.packageName,
            packageName = app.packageName,
            appLabel = app.appLabel,
            iconResId = app.iconResId
        ))
    }

    private fun createGenericAppView(app: VirtualAppInfo): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@VirtualPhoneActivity, R.color.background))
            setPadding(32, 32, 32, 32)
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(96, 96).apply { bottomMargin = 24 }
            if (app.iconPath != null) {
                val bmp = android.graphics.BitmapFactory.decodeFile(app.iconPath)
                if (bmp != null) setImageBitmap(bmp) else setImageResource(R.drawable.ic_apps)
            } else {
                setImageResource(R.drawable.ic_apps)
            }
        }
        root.addView(icon)

        val title = TextView(this).apply {
            text = app.appLabel
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val pkgTv = TextView(this).apply {
            text = "${app.packageName} (Sandboxed)"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@VirtualPhoneActivity, R.color.primary))
            setPadding(0, 8, 0, 24)
        }
        root.addView(pkgTv)

        val desc = TextView(this).apply {
            text = "This application is running inside PocketVM's isolated sandbox.\nPrivate data is mounted at /data/data/${app.packageName}/."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@VirtualPhoneActivity, R.color.text_secondary))
            setLineSpacing(6f, 1f)
            setPadding(0, 0, 0, 32)
        }
        root.addView(desc)

        val btnClose = MaterialButton(this).apply {
            text = "Close Application"
            setOnClickListener { showDesktop() }
        }
        root.addView(btnClose)

        return root
    }

    private fun showDesktop() {
        activeAppPackage = null
        vmRunningAppContainer.visibility = View.GONE
        vmRunningAppContainer.removeAllViews()
        vmRecentsView.visibility = View.GONE
        vmQuickSettingsShade.visibility = View.GONE
        vmLauncherDesktop.visibility = View.VISIBLE
    }

    private fun setupNavigationAndGestures() {
        // Back Button
        findViewById<View>(R.id.nav_btn_back).setOnClickListener {
            handleBackPressed()
        }

        // Home Button
        findViewById<View>(R.id.nav_btn_home).setOnClickListener {
            showDesktop()
        }

        // Recents Button
        findViewById<View>(R.id.nav_btn_recents).setOnClickListener {
            toggleRecents()
        }

        // Tap Status Bar to toggle Quick Settings
        findViewById<View>(R.id.vm_status_bar).setOnClickListener {
            toggleQuickSettings()
        }

        // Recents view
        recyclerRecents.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentsAdapter = VirtualRecentsAdapter(
            tasks = recentTasks,
            onTaskClick = { task ->
                pm.getApp(task.packageName)?.let { launchApp(it) }
            },
            onTaskClose = { task ->
                recentTasks.remove(task)
                recentsAdapter.updateTasks(recentTasks)
                if (recentTasks.isEmpty()) showDesktop()
            }
        )
        recyclerRecents.adapter = recentsAdapter

        findViewById<View>(R.id.btn_clear_all_tasks).setOnClickListener {
            recentTasks.clear()
            recentsAdapter.updateTasks(recentTasks)
            showDesktop()
        }
    }

    private fun toggleRecents() {
        if (vmRecentsView.visibility == View.VISIBLE) {
            vmRecentsView.visibility = View.GONE
        } else {
            recentsAdapter.updateTasks(recentTasks)
            vmRecentsView.visibility = View.VISIBLE
            vmQuickSettingsShade.visibility = View.GONE
        }
    }

    private fun toggleQuickSettings() {
        if (vmQuickSettingsShade.visibility == View.VISIBLE) {
            vmQuickSettingsShade.visibility = View.GONE
        } else {
            vmQuickSettingsShade.visibility = View.VISIBLE
            vmRecentsView.visibility = View.GONE
        }
    }

    private fun setupQuickSettings() {
        findViewById<View>(R.id.qs_btn_close).setOnClickListener {
            vmQuickSettingsShade.visibility = View.GONE
        }

        // Quick Root toggle
        findViewById<View>(R.id.qs_toggle_root).setOnClickListener {
            instance.rootConfig.isRootEnabled = !instance.rootConfig.isRootEnabled
            vmManager.saveInstance(instance)
            updateRootAndBatteryStatus()
            val state = if (instance.rootConfig.isRootEnabled) "enabled" else "disabled"
            Toast.makeText(this, "Virtual Root $state", Toast.LENGTH_SHORT).show()
        }

        // Rotate toggle
        findViewById<View>(R.id.qs_toggle_rotate).setOnClickListener {
            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        // WiFi toggle
        findViewById<View>(R.id.qs_toggle_wifi).setOnClickListener {
            Toast.makeText(this, "Virtual WiFi toggled", Toast.LENGTH_SHORT).show()
        }

        // Location toggle
        findViewById<View>(R.id.qs_toggle_location).setOnClickListener {
            instance.sensorConfig.mockGpsEnabled = !instance.sensorConfig.mockGpsEnabled
            vmManager.saveInstance(instance)
            val state = if (instance.sensorConfig.mockGpsEnabled) "enabled" else "disabled"
            Toast.makeText(this, "Mock GPS $state", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableVBall() {
        var initialX = 0f
        var initialY = 0f
        var dX = 0f
        var dY = 0f
        var hasMoved = false

        vBall.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    if (Math.abs(newX - initialX) > 10 || Math.abs(newY - initialY) > 10) {
                        hasMoved = true
                        v.animate().x(newX).y(newY).setDuration(0).start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        showVBallMenu(v)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showVBallMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, 1, 0, "🏠 Return to Host Dashboard")
            add(0, 2, 1, "🪟 Minimize to Floating Window (PiP)")
            add(0, 3, 2, "📸 Take VM Screenshot")
            add(0, 4, 3, "🔄 Rotate Display")
            add(0, 5, 4, "⚡ Soft Reboot VM")
            add(0, 6, 5, "⏹️ Power Off VM")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> finish()
                2 -> requestAndEnterFloatingMode()
                3 -> {
                    Toast.makeText(this, "Captured VM screenshot to /sdcard/DCIM/Screenshots/", Toast.LENGTH_SHORT).show()
                }
                4 -> {
                    requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
                5 -> {
                    Toast.makeText(this, "Rebooting Virtual Instance…", Toast.LENGTH_SHORT).show()
                    showDesktop()
                }
                6 -> powerOffVm()
            }
            true
        }
        popup.show()
    }

    private fun requestAndEnterFloatingMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Floating Window Mode")
                .setMessage(R.string.toast_overlay_permission_needed)
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            VirtualFloatingService.start(this, instance.id, instance.name)
            finish()
        }
    }

    private fun powerOffVm() {
        AlertDialog.Builder(this)
            .setTitle("Power Off VM")
            .setMessage(getString(R.string.confirm_stop_vm, instance.name))
            .setPositiveButton("Power Off") { _, _ ->
                instance.state = VmState.STOPPED
                vmManager.saveInstance(instance)
                VmBackgroundService.stop(this)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleBackPressed() {
        if (vmQuickSettingsShade.visibility == View.VISIBLE) {
            vmQuickSettingsShade.visibility = View.GONE
        } else if (vmRecentsView.visibility == View.VISIBLE) {
            vmRecentsView.visibility = View.GONE
        } else if (vmRunningAppContainer.visibility == View.VISIBLE) {
            showDesktop()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPressed()
    }

    companion object {
        const val EXTRA_VM_ID = "extra_vm_id"

        fun start(context: Context, vmId: String) {
            val intent = Intent(context, VirtualPhoneActivity::class.java).apply {
                putExtra(EXTRA_VM_ID, vmId)
            }
            context.startActivity(intent)
        }
    }
}
