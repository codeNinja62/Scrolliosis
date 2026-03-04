package com.saltatoryimpulse.braingate.overlay

import com.saltatoryimpulse.braingate.Constants

object OverlayConfig {
    // Toast styling
    const val TOAST_PADDING_HORIZONTAL = 56
    const val TOAST_PADDING_VERTICAL = 32
    const val TOAST_BG_HEX = "#333333"
    const val TOAST_CORNER_RADIUS = 60f
    const val TOAST_Y_OFFSET = 250

    // Timer styling
    const val TIMER_PADDING_HORIZONTAL = 35
    const val TIMER_PADDING_VERTICAL = 15
    const val TIMER_TEXT_SIZE = 15f
    const val TIMER_BG_NORMAL = "#CC0F1115"
    const val TIMER_BG_WARNING = "#CCEF4444"
    const val TIMER_CORNER_RADIUS = 50f
    const val TIMER_POS_X = 50
    const val TIMER_POS_Y = 150

    // Timing: use project-wide constants where possible
    val TOAST_DURATION_MS: Long get() = Constants.TOAST_DURATION_MS
    val TIMER_REFRESH_MS: Long get() = Constants.TIMER_REFRESH_MS
}
