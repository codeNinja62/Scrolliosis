package com.saltatoryimpulse.braingate

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat

/**
 * BRAINGATE PERMISSION UTILS
 * Engineered for 100% accuracy across AOSP and OEM-modified Android skins.
 */
object PermissionUtils {

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
}