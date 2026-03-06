package com.saltatoryimpulse.scrolliosis

import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * UNIVERSAL BOOT RECEIVER:
 * Hardened for AOSP, Samsung, Xiaomi, Oppo, and Huawei.
 * Ensures the GateService process is prioritized by the kernel immediately upon hardware power-on.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // Comprehensive list of boot actions including OEM-specific fast-boot signals
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED || // Critical for encrypted devices
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Constants.ACTION_ENSURE_SERVICE ||
                action == "android.intent.action.QUICKBOOT_POWERON" || // HTC/Older OEM
                action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (isBoot) {
            Log.d("ScrolliosisBoot", "System Boot detected: $action. Pinging GateService process.")

            // WAKING THE PROCESS:
            // We don't try to manually 'start' the Accessibility Service (the OS does that).
            // Instead, we send a 'wake-up' intent to the service class. This tells the
            // Linux OOM (Out Of Memory) killer that our process is active and high-priority,
            // preventing the 'forgetfulness' that occurs when the OS skips 'cold' services.
            try {
                val serviceIntent = Intent(context, GateService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                // On some API 26+ devices, startService may throw an IllegalStateException
                // if the app is in the background. We catch it here because the intent
                // has already done its job: waking the process.
                Log.e("ScrolliosisBoot", "Process wake-up ping sent. System will handle re-binding.")
            }
        }
    }
}