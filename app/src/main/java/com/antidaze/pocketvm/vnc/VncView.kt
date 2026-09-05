package com.antidaze.pocketvm.vnc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.BaseInputConnection
import android.view.InputConnection
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.inputmethod.EditorInfo

/**
 * SurfaceView that renders the remote framebuffer and forwards touch and key
 * input to the RFB client. Scales the framebuffer to fit (letterboxed).
 */
class VncView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var client: RfbClient? = null
    private var bitmap: Bitmap? = null
    private var fbWidth = 0
    private var fbHeight = 0
    private var scale = 1f
    private val dstRect = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var pressed = false
    private var ctrlLatched = false
    private var altLatched = false

    init {
        holder.addCallback(this)
        setFocusable(true)
        setFocusableInTouchMode(true)
    }

    fun attach(c: RfbClient) { client = c }

    override fun surfaceCreated(holder: android.view.SurfaceHolder) { redraw() }
    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
        computeDstRect(width, height)
        redraw()
    }
    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) { }

    fun setFramebufferSize(w: Int, h: Int) {
        fbWidth = w; fbHeight = h
        post { computeDstRect(width, height) }
    }

    /** Called from the RFB reader thread with the latest full framebuffer. */
    fun showFrame(bm: Bitmap) {
        bitmap = bm
        redraw()
    }

    private fun redraw() {
        val bm = bitmap ?: return
        if (holder.surface == null || !holder.surface.isValid) return
        val canvas = try { holder.lockCanvas() } catch (e: Exception) { return } ?: return
        try {
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bm, null, dstRect, paint)
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
        }
    }

    private fun computeDstRect(vw: Int, vh: Int) {
        if (fbWidth == 0 || fbHeight == 0 || vw == 0 || vh == 0) {
            dstRect.set(0f, 0f, vw.toFloat(), vh.toFloat())
            return
        }
        val s = minOf(vw.toFloat() / fbWidth, vh.toFloat() / fbHeight)
        scale = s
        val w = fbWidth * s
        val h = fbHeight * s
        dstRect.set((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = client ?: return false
        if (bitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pressed = true
                sendPointerAt(event, 1)
            }
            MotionEvent.ACTION_MOVE -> if (pressed) sendPointerAt(event, 1)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                pressed = false
                sendPointerAt(event, 0)
            }
        }
        return true
    }

    private fun sendPointerAt(event: MotionEvent, buttons: Int) {
        val x = ((event.x - dstRect.left) / scale).toInt()
        val y = ((event.y - dstRect.top) / scale).toInt()
        client?.sendPointer(x, y, buttons)
    }

    fun sendKeySym(keysym: Int) {
        val c = client ?: return
        if (keysym == Keysyms.CTRL_L) {
            ctrlLatched = !ctrlLatched
            alpha = if (ctrlLatched) 0.5f else 1f
            return
        }
        if (keysym == Keysyms.ALT_L) {
            altLatched = !altLatched
            alpha = if (altLatched) 0.5f else 1f
            return
        }
        if (ctrlLatched) c.sendKey(Keysyms.CTRL_L, true)
        if (altLatched) c.sendKey(Keysyms.ALT_L, true)
        c.sendKey(keysym, true)
        c.sendKey(keysym, false)
        if (altLatched) c.sendKey(Keysyms.ALT_L, false)
        if (ctrlLatched) c.sendKey(Keysyms.CTRL_L, false)
    }

    /** Handle a hardware key event. Returns true when consumed. */
    fun handleKeyEvent(e: KeyEvent): Boolean {
        val ks = Keysyms.fromKeyEvent(e) ?: return false
        client?.sendKey(ks, e.action == KeyEvent.ACTION_DOWN)
        return true
    }

    fun toggleKeyboard(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.showSoftInput(this, 0) || imm.toggleSoftInput(0, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return handleKeyEvent(event)
            }
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                for (ch in text) {
                    val ks = Keysyms.fromChar(ch)
                    client?.sendKey(ks, true)
                    client?.sendKey(ks, false)
                }
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    client?.sendKey(Keysyms.BACKSPACE, true)
                    client?.sendKey(Keysyms.BACKSPACE, false)
                }
                return true
            }
        }
    }
}
