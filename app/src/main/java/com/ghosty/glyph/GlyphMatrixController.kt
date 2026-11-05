package com.ghosty.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Foreground-only controller for the Nothing Phone (4a) Pro 13x13 Glyph Matrix.
 * Uses Nothing's official Glyph Matrix SDK and the app-matrix channel.
 */
class GlyphMatrixController(context: Context) {
    private val appContext = context.applicationContext
    private var manager: GlyphMatrixManager? = null
    private var connected = false
    private var foreground = false
    private var currentMood = "Curious"

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            val m = manager ?: return
            try {
                m.register(Glyph.DEVICE_25111p)
                connected = true
                if (foreground) renderMood(currentMood)
            } catch (e: Exception) {
                Log.w("GhostyGlyph", "Glyph registration failed", e)
                connected = false
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            connected = false
        }
    }

    fun init() {
        try {
            manager = GlyphMatrixManager.getInstance(appContext)
            manager?.init(callback)
        } catch (e: Exception) {
            Log.w("GhostyGlyph", "Glyph SDK unavailable on this device", e)
        }
    }

    fun onForeground() {
        foreground = true
        if (connected) renderMood(currentMood)
    }

    fun onBackground() {
        foreground = false
        try {
            if (connected) manager?.closeAppMatrix()
        } catch (e: Exception) {
            Log.w("GhostyGlyph", "Could not close app matrix", e)
        }
    }

    fun showMood(mood: String) {
        currentMood = mood
        if (foreground && connected) renderMood(mood)
    }

    private fun renderMood(mood: String) {
        val frame = IntArray(13 * 13)
        fun dot(x: Int, y: Int, value: Int = 4095) {
            if (x in 0..12 && y in 0..12) frame[y * 13 + x] = value
        }

        // Rounded little ghost silhouette.
        val rows = arrayOf(
            intArrayOf(4, 8), intArrayOf(2, 10), intArrayOf(1, 11),
            intArrayOf(0, 12), intArrayOf(0, 12), intArrayOf(0, 12),
            intArrayOf(0, 12), intArrayOf(0, 12), intArrayOf(0, 12),
            intArrayOf(0, 12), intArrayOf(1, 11), intArrayOf(2, 10),
            intArrayOf(4, 8)
        )
        rows.forEachIndexed { y, range -> for (x in range[0]..range[1]) dot(x, y) }

        // Eyes and mood-specific expression.
        for (y in 4..5) {
            dot(4, y, 0)
            dot(8, y, 0)
        }
        when (mood.lowercase()) {
            "sleepy" -> {
                dot(3, 5); dot(4, 5); dot(8, 5); dot(9, 5)
                dot(6, 8); dot(7, 8)
            }
            "dizzy!!", "whoa!", "surprised" -> {
                dot(3, 4); dot(5, 4); dot(8, 4); dot(10, 4)
                dot(6, 8); dot(7, 8); dot(6, 9); dot(7, 9)
            }
            else -> {
                dot(5, 8); dot(6, 9); dot(7, 9); dot(8, 8)
            }
        }

        try {
            manager?.setAppMatrixFrame(frame)
        } catch (e: Exception) {
            Log.w("GhostyGlyph", "Could not update Glyph Matrix", e)
        }
    }

    fun release() {
        onBackground()
        try {
            manager?.unInit()
        } catch (e: Exception) {
            Log.w("GhostyGlyph", "Could not unbind Glyph SDK", e)
        }
        manager = null
        connected = false
    }
}
