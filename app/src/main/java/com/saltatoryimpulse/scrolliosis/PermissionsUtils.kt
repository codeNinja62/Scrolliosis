package com.saltatoryimpulse.scrolliosis

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import androidx.core.content.ContextCompat

/**
 * SCROLLIOSIS PERMISSION UTILS
 * Engineered for 100% accuracy across AOSP and OEM-modified Android skins.
 */
object PermissionUtils {
    private const val PREFS_NAME = "scrolliosis_runtime"
    private const val KEY_SETUP_GRACE_UNTIL = "setup_grace_until"

    /**
     * ACCESSIBILITY HEALTH CHECK:
     * Directly queries the Secure Settings string. This is the only way to bypass
     * OEM caching issues. The standard AccessibilityManager.isEnabled() check
     * often returns stale data on Samsung and Xiaomi devices.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            // Setting doesn't exist yet, assume disabled
            return false
        }

        if (accessibilityEnabled == 1) {
            val expectedComponentName = ComponentName(context, GateService::class.java)

            // This string contains a colon-separated list of all active accessibility services
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (enabledServicesSetting != null) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)

                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledService = ComponentName.unflattenFromString(componentNameString)

                    // Verification against our specific service component
                    if (enabledService != null && enabledService == expectedComponentName) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * OVERLAY CHECK:
     * Verifies if the app can draw over other apps (System Alert Window).
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * NOTIFICATION CHECK (Android 13+):
     * Ensures we have the right to show the foreground service notification.
     * On pre-Tiramisu devices, this always returns true as permission is granted at install.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * USAGE ACCESS CHECK:
     * Gives us a second foreground-app signal when accessibility callbacks are delayed,
     * suppressed by OEMs, or lost during fast app transitions.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun extendSetupGrace(context: Context, durationMs: Long = Constants.SETUP_GRACE_DURATION_MS) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentValue = prefs.getLong(KEY_SETUP_GRACE_UNTIL, 0L)
        val extendedUntil = maxOf(currentValue, System.currentTimeMillis() + durationMs)
        prefs.edit().putLong(KEY_SETUP_GRACE_UNTIL, extendedUntil).apply()
    }

    fun clearSetupGrace(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SETUP_GRACE_UNTIL)
            .apply()
    }

    fun isSetupGraceActive(context: Context): Boolean {
        val graceUntil = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SETUP_GRACE_UNTIL, 0L)
        return graceUntil > System.currentTimeMillis()
    }

    /**
     * Returns the most recent foreground package we can infer from UsageStats.
     * This acts as a fallback path when Accessibility events are delayed or skipped.
     */
    fun getForegroundPackageFromUsageStats(
        context: Context,
        lookBackWindowMs: Long = 15_000L
    ): String? {
        if (!hasUsageAccess(context)) return null

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - lookBackWindowMs

        @Suppress("DEPRECATION")
        val eventPackage = runCatching {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var packageName: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isForegroundEvent && !event.packageName.isNullOrBlank()) {
                    packageName = event.packageName
                }
            }

            packageName
        }.getOrNull()

        if (!eventPackage.isNullOrBlank()) {
            return eventPackage
        }

        return usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}