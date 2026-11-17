package com.antidaze.pocketvm.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.ui.vm.VirtualPhoneActivity

class VirtualFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var vmId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        vmId = intent?.getStringExtra(EXTRA_VM_ID) ?: ""
        val vmName = intent?.getStringExtra(EXTRA_VM_NAME) ?: "VM"

        if (floatingView == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            val v = inflater.inflate(R.layout.activity_virtual_phone, null)
            floatingView = v

            // Setup simplified floating window layout params
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val width = (280 * density).toInt()
            val height = (500 * density).toInt()

            params = WindowManager.LayoutParams(
                width,
                height,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }

            // Hide the V-ball inside floating mode
            v.findViewById<View>(R.id.v_ball)?.visibility = View.GONE

            // Tap top status bar to return to fullscreen
            v.findViewById<View>(R.id.vm_status_bar)?.setOnClickListener {
                restoreToFullscreen()
            }

            // Drag touch listener for moving the window
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            v.findViewById<View>(R.id.vm_status_bar)?.setOnTouchListener { _, event ->
                val p = params ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = initialX + (event.rawX - initialTouchX).toInt()
                        p.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(v, p)
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager?.addView(v, params)
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun restoreToFullscreen() {
        val intent = Intent(this, VirtualPhoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(VirtualPhoneActivity.EXTRA_VM_ID, vmId)
        }
        startActivity(intent)
        stopSelf()
    }

    override fun onDestroy() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VM_ID = "extra_vm_id"
        const val EXTRA_VM_NAME = "extra_vm_name"

        fun start(context: Context, vmId: String, vmName: String) {
            val intent = Intent(context, VirtualFloatingService::class.java).apply {
                putExtra(EXTRA_VM_ID, vmId)
                putExtra(EXTRA_VM_NAME, vmName)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VirtualFloatingService::class.java))
        }
    }
}
