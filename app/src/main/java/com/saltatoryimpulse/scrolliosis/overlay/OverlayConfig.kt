package com.saltatoryimpulse.scrolliosis.overlay

import com.saltatoryimpulse.scrolliosis.Constants

object OverlayConfig {
    const val SHIELD_BG_HEX = "#FF000000"

    // Toast styling
    const val TOAST_PADDING_HORIZONTAL = 56
    const val TOAST_PADDING_VERTICAL = 32
    const val TOAST_BG_HEX = "#333333"
    const val TOAST_CORNER_RADIUS = 60f
    // BUG-07: was raw px (250) — now in dp so it scales correctly on all screen densities
    const val TOAST_Y_OFFSET_DP = 100

    // Timer styling
    const val TIMER_PADDING_HORIZONTAL = 35
    const val TIMER_PADDING_VERTICAL = 15
    const val TIMER_TEXT_SIZE = 15f
    const val TIMER_BG_NORMAL = "#CC0F1115"
    const val TIMER_BG_WARNING = "#CCEF4444"
    const val TIMER_CORNER_RADIUS = 50f
    // BUG-07: was raw px (50, 150) — now in dp
    const val TIMER_POS_X_DP = 16
    const val TIMER_POS_Y_DP = 60

    // Timing: use project-wide constants where possible
    val TOAST_DURATION_MS: Long get() = Constants.TOAST_DURATION_MS
    val TIMER_REFRESH_MS: Long get() = Constants.TIMER_REFRESH_MS
}
