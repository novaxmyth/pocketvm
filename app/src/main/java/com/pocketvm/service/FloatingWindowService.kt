package com.pocketvm.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/**
 * Floating window overlay service (V-Ball).
 * Creates a small draggable floating ball that provides quick VM controls.
 * Requires SYSTEM_ALERT_WINDOW permission.
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindow"
        const val EXTRA_VM_ID = "extra_vm_id"

        fun start(context: Context, vmId: String) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                putExtra(EXTRA_VM_ID, vmId)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var vmId: String = ""

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        vmId = intent?.getStringExtra(EXTRA_VM_ID) ?: ""

        if (floatingView != null) return START_NOT_STICKY

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val ballSize = (48 * resources.displayMetrics.density).toInt()

        // Create the floating ball view
        val ball = FrameLayout(this).apply {
            setBackgroundResource(android.R.drawable.btn_default)
            val imageView = ImageView(this@FloatingWindowService).apply {
                setImageResource(android.R.drawable.ic_dialog_info)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val padding = (8 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            addView(imageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            ballSize, ballSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        ball.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update layout: ${e.message}")
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) {
                        onBallClicked()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = ball

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: ${e.message}")
        }

        return START_NOT_STICKY
    }

    private fun onBallClicked() {
        // Open the main app when the ball is tapped
        val intent = Intent(this, com.pocketvm.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_VM_ID, vmId)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove floating view: ${e.message}")
            }
        }
        floatingView = null
    }
}
