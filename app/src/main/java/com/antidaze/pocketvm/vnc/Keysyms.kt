package com.antidaze.pocketvm.vnc

import android.view.KeyEvent

/** X keysym constants + mapping from Android key events / characters. */
object Keysyms {
    const val BACKSPACE = 0xFF08
    const val TAB = 0xFF09
    const val RETURN = 0xFF0D
    const val ESCAPE = 0xFF1B
    const val HOME = 0xFF50
    const val LEFT = 0xFF51
    const val UP = 0xFF52
    const val RIGHT = 0xFF53
    const val DOWN = 0xFF54
    const val PAGE_UP = 0xFF55
    const val PAGE_DOWN = 0xFF56
    const val END = 0xFF57
    const val INSERT = 0xFF63
    const val DELETE = 0xFFFF
    const val SHIFT_L = 0xFFE1
    const val SHIFT_R = 0xFFE2
    const val CTRL_L = 0xFFE3
    const val CTRL_R = 0xFFE4
    const val META_L = 0xFFE7
    const val ALT_L = 0xFFE9
    const val ALT_R = 0xFFEA
    const val SPACE = 0x20

    private val byKeyCode = mapOf(
        KeyEvent.KEYCODE_ENTER to RETURN,
        KeyEvent.KEYCODE_NUMPAD_ENTER to RETURN,
        KeyEvent.KEYCODE_DEL to BACKSPACE,
        KeyEvent.KEYCODE_FORWARD_DEL to DELETE,
        KeyEvent.KEYCODE_INSERT to INSERT,
        KeyEvent.KEYCODE_TAB to TAB,
        KeyEvent.KEYCODE_ESCAPE to ESCAPE,
        KeyEvent.KEYCODE_DPAD_LEFT to LEFT,
        KeyEvent.KEYCODE_DPAD_UP to UP,
        KeyEvent.KEYCODE_DPAD_RIGHT to RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN to DOWN,
        KeyEvent.KEYCODE_PAGE_UP to PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN to PAGE_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME to HOME,
        KeyEvent.KEYCODE_MOVE_END to END,
        KeyEvent.KEYCODE_SHIFT_LEFT to SHIFT_L,
        KeyEvent.KEYCODE_SHIFT_RIGHT to SHIFT_R,
        KeyEvent.KEYCODE_CTRL_LEFT to CTRL_L,
        KeyEvent.KEYCODE_CTRL_RIGHT to CTRL_R,
        KeyEvent.KEYCODE_META_LEFT to META_L,
        KeyEvent.KEYCODE_ALT_LEFT to ALT_L,
        KeyEvent.KEYCODE_ALT_RIGHT to ALT_R,
        KeyEvent.KEYCODE_SPACE to SPACE
    ).let { base ->
        base + (1..12).associate { i ->
            (KeyEvent.KEYCODE_F1 + (i - 1)) to (0xFFBE + (i - 1))
        }
    }

    /** Map a hardware/soft key event to a keysym, or null when unknown. */
    fun fromKeyEvent(e: KeyEvent): Int? {
        byKeyCode[e.keyCode]?.let { return it }
        val c = e.unicodeChar
        if (c in 0x20..0x7E) return c
        if (c == '\n'.code) return RETURN
        if (c == '\t'.code) return TAB
        return null
    }

    fun fromChar(c: Char): Int = when (c) {
        '\n', '\r' -> RETURN
        '\t' -> TAB
        '\b' -> BACKSPACE
        else -> c.code
    }
}
