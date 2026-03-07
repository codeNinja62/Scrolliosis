package com.saltatoryimpulse.scrolliosis.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import android.graphics.Color as AndroidColor
import android.util.Log
import com.saltatoryimpulse.scrolliosis.overlay.OverlayConfig
import com.saltatoryimpulse.scrolliosis.Constants

/**
 * Manages floating overlays (custom toasts and timer bubble) for `GateService`.
 * Runs UI operations on the Main dispatcher but uses the provided scope for
 * lifecycle management.
 */
class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope
) {
    private var blockingShieldView: View? = null
    private var blockingShieldAttached = false
    private var blockingShieldVisible = false
    private var customToastView: View? = null
    private var toastJob: Job? = null

    private var timerView: TextView? = null
    private var timerJob: Job? = null

    fun prepareBlockingShield() {
        scope.launch(Dispatchers.Main) {
            ensureBlockingShieldAttached()
            setBlockingShieldVisible(false)
        }
    }

    fun showBlockingShield() {
        scope.launch(Dispatchers.Main) {
            ensureBlockingShieldAttached()
            setBlockingShieldVisible(true)
        }
    }

    fun removeBlockingShield() {
        scope.launch(Dispatchers.Main) {
            setBlockingShieldVisible(false)
        }
    }

    private fun setBlockingShieldVisible(visible: Boolean) {
        if (blockingShieldVisible == visible) return

        blockingShieldView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        blockingShieldView?.alpha = if (visible) 1f else 0f
        blockingShieldVisible = visible
    }

    private fun ensureBlockingShieldAttached() {
        if (blockingShieldAttached && blockingShieldView != null) return

        val shieldView = blockingShieldView ?: View(context).apply {
            setBackgroundColor(AndroidColor.parseColor(OverlayConfig.SHIELD_BG_HEX))
            alpha = 0f
            visibility = View.INVISIBLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(shieldView, params)
            blockingShieldView = shieldView
            blockingShieldAttached = true
        } catch (e: Exception) {
            blockingShieldView = null
            blockingShieldAttached = false
            Log.w("OverlayController", "Failed to attach blocking shield", e)
        }
    }

    private fun destroyBlockingShield() {
        blockingShieldView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        blockingShieldView = null
        blockingShieldAttached = false
        blockingShieldVisible = false
    }

    fun showCustomToast(message: String) {
        scope.launch(Dispatchers.Main) {
            toastJob?.cancel()
            removeCustomToast()

            val textView = TextView(context).apply {
                text = message
                setTextColor(AndroidColor.WHITE)
                setPadding(OverlayConfig.TOAST_PADDING_HORIZONTAL, OverlayConfig.TOAST_PADDING_VERTICAL,
                    OverlayConfig.TOAST_PADDING_HORIZONTAL, OverlayConfig.TOAST_PADDING_VERTICAL)
                background = GradientDrawable().apply {
                    setColor(AndroidColor.parseColor(OverlayConfig.TOAST_BG_HEX))
                    cornerRadius = OverlayConfig.TOAST_CORNER_RADIUS
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                // BUG-07: convert dp → px so position is correct on all screen densities
                y = (OverlayConfig.TOAST_Y_OFFSET_DP * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(textView, params)
                customToastView = textView
                toastJob = launch { delay(OverlayConfig.TOAST_DURATION_MS); removeCustomToast() }
            } catch (e: Exception) { Log.w("OverlayController", "Failed to show custom toast", e) }
        }
    }

    fun mountTimerForPackage(pkg: String, expirationMs: Long, onExpire: () -> Unit) {
        if (timerView != null) {
            // View is already shown. If the update loop died unexpectedly (e.g. the coroutine
            // scope was momentarily cancelled), restart it without recreating the view.
            if (timerJob?.isActive != true) startTimerLoop(expirationMs, onExpire)
            return
        }

        scope.launch(Dispatchers.Main) {
            timerView = TextView(context).apply {
                // BUG-14: show "BG" label on first line so users know what this overlay is
                text = "BG\n--:--"
                setTextColor(AndroidColor.WHITE)
                textSize = OverlayConfig.TIMER_TEXT_SIZE
                setPadding(OverlayConfig.TIMER_PADDING_HORIZONTAL, OverlayConfig.TIMER_PADDING_VERTICAL,
                    OverlayConfig.TIMER_PADDING_HORIZONTAL, OverlayConfig.TIMER_PADDING_VERTICAL)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(AndroidColor.parseColor(OverlayConfig.TIMER_BG_NORMAL))
                    cornerRadius = OverlayConfig.TIMER_CORNER_RADIUS
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                // BUG-07: convert dp → px
                val density = context.resources.displayMetrics.density
                x = (OverlayConfig.TIMER_POS_X_DP * density).toInt()
                y = (OverlayConfig.TIMER_POS_Y_DP * density).toInt()
            }

            try {
                windowManager.addView(timerView, params)
            } catch (e: Exception) {
                timerView = null
                return@launch
            }

            startTimerLoop(expirationMs, onExpire)
        }
    }

    private fun startTimerLoop(expirationMs: Long, onExpire: () -> Unit) {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now >= expirationMs) {
                    removeTimer()
                    onExpire()
                    break
                }

                val secondsLeft = ((expirationMs - now) / 1000).toInt()
                timerView?.apply {
                    // BUG-14: prefix with "BG" label so the bubble is identifiable
                    text = "BG\n${String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60)}"
                    (background as? GradientDrawable)?.setColor(
                        if (secondsLeft <= 30) AndroidColor.parseColor(OverlayConfig.TIMER_BG_WARNING)
                        else AndroidColor.parseColor(OverlayConfig.TIMER_BG_NORMAL)
                    )
                }
                delay(Constants.TIMER_REFRESH_MS)
            }
        }
    }

    fun removeTimer() {
        timerJob?.cancel()
        timerView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        timerView = null
    }

    fun removeCustomToast() {
        customToastView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        customToastView = null
    }

    fun release() {
        toastJob?.cancel()
        timerJob?.cancel()
        destroyBlockingShield()
        removeCustomToast()
        removeTimer()
    }
}
