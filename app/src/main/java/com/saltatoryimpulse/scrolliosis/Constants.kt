package com.saltatoryimpulse.scrolliosis

object Constants {
    const val CHANNEL_ID = "scrolliosis_enforcement"
    const val ACTION_ENSURE_SERVICE = "com.saltatoryimpulse.scrolliosis.ENSURE_SERVICE"

    const val ACTION_UNLOCK = "com.saltatoryimpulse.scrolliosis.UNLOCK_APP"
    const val ACTION_SHOW_TOAST = "com.saltatoryimpulse.scrolliosis.SHOW_TOAST"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_MESSAGE = "message"

    const val EXTRA_BLOCKED_PACKAGE = "BLOCKED_PACKAGE"
    const val EXTRA_START_ROUTE = "START_ROUTE"

    const val UNLOCK_DURATION_MS = 5 * 60 * 1000L
    const val SERVICE_START_GRACE_MS = 8_000L
    const val FOREGROUND_POLL_INTERVAL_MS = 150L
    const val BLOCK_INTERCEPT_DEBOUNCE_MS = 350L
    const val SERVICE_REVIVE_DELAY_MS = 1_000L
    const val TOAST_DURATION_MS = 2500L
    const val TIMER_REFRESH_MS = 1000L
    const val COOLDOWN_TOAST_INTERVAL_MS = 2000L

    const val TAG = "Scrolliosis"
}
