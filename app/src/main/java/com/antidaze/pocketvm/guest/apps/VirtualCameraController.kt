package com.antidaze.pocketvm.guest.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualCameraController(
    private val context: Context
) {
    fun createView(): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Camera Preview Pattern View
        val previewView = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val handler = Handler(Looper.getMainLooper())
            private var frameCount = 0

            private val runnable = object : Runnable {
                override fun run() {
                    frameCount++
                    invalidate()
                    handler.postDelayed(this, 33) // ~30 fps
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                handler.post(runnable)
            }

            override fun onDetachedFromWindow() {
                handler.removeCallbacks(runnable)
                super.onDetachedFromWindow()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0 || h <= 0) return

                // Draw SMPTE color bars
                val colors = intArrayOf(
                    Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN,
                    Color.MAGENTA, Color.RED, Color.BLUE, Color.BLACK
                )
                val barW = w / colors.size
                for (i in colors.indices) {
                    paint.color = colors[i]
                    canvas.drawRect(i * barW, 0f, (i + 1) * barW, h * 0.7f, paint)
                }

                // Bottom stripe
                paint.color = Color.DKGRAY
                canvas.drawRect(0f, h * 0.7f, w, h, paint)

                // Reticle / Crosshair
                paint.color = Color.WHITE
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                val cx = w / 2f
                val cy = h / 2f
                canvas.drawCircle(cx, cy, 80f, paint)
                canvas.drawLine(cx - 100f, cy, cx + 100f, cy, paint)
                canvas.drawLine(cx, cy - 100f, cx, cy + 100f, paint)

                // Digital Timecode & Stats
                paint.style = Paint.Style.FILL
                paint.textSize = 36f
                val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                val timeStr = sdf.format(Date())
                canvas.drawText("POCKETVM VIRTUAL CAMERA [TEST FEED]", 40f, h * 0.75f, paint)
                canvas.drawText("REC [●]  $timeStr   FRAME: $frameCount", 40f, h * 0.82f, paint)
            }
        }
        root.addView(previewView)

        // Shutter Button Overlay at bottom
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 48)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        }

        val btnShutter = MaterialButton(context).apply {
            text = "Capture Virtual Photo"
            setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
            setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            setOnClickListener {
                Toast.makeText(context, "Captured virtual snapshot to /sdcard/DCIM/", Toast.LENGTH_SHORT).show()
            }
        }
        controls.addView(btnShutter)
        root.addView(controls)

        return root
    }
}
