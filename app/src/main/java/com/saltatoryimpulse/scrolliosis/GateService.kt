package com.saltatoryimpulse.scrolliosis

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.*
import com.saltatoryimpulse.scrolliosis.overlay.OverlayController
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Color as AndroidColor

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GateService : AccessibilityService(), KoinComponent {

    private val repository: com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository by inject()
    private val overlayController: OverlayController by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cooldownEndTime = 0L
    private var lastInterceptTime = 0L
    private var lastToastTime = 0L

    // Grace period after service start to avoid blocking during onboarding/permission grant
    private var serviceStartTime = 0L

    private val unlockedApps = ConcurrentHashMap<String, Long>()
    private var currentForegroundPackage: String? = null

    private val CHANNEL_ID = Constants.CHANNEL_ID

    private val communicationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_UNLOCK -> {
                    val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE_NAME) ?: return
                    // Grant temporary unlock window for `packageName`.
                    unlockedApps[packageName] = System.currentTimeMillis() + Constants.UNLOCK_DURATION_MS
                    currentForegroundPackage = packageName

                    // Ensure timer UI updates on the main thread.
                    serviceScope.launch(Dispatchers.Main) {
                        updateTimerVisibility()
                    }
                }
                Constants.ACTION_SHOW_TOAST -> {
                    val message = intent.getStringExtra(Constants.EXTRA_MESSAGE) ?: "Focus preserved."
                    cooldownEndTime = System.currentTimeMillis() + Constants.UNLOCK_DURATION_MS
                    overlayController.showCustomToast(message)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startEnforcementForeground()

        // repository and overlayController are provided by Koin injection
        // (OverlayController is created as a singleton in the Koin module using the application context).
        serviceStartTime = System.currentTimeMillis()

        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_UNLOCK)
            addAction(Constants.ACTION_SHOW_TOAST)
        }

        // IPC security using ContextCompat
        try {
            ContextCompat.registerReceiver(
                this,
                communicationReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Failed to register communication receiver", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strict focus on WINDOW_STATE_CHANGED to maximize performance and battery
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentForegroundPackage = packageName

            // Detect settings/uninstall attempts across OEMs and handle interception.
            if (isUninstallOrSettingsAttempt(packageName, event)) {
                handleSettingsInterception()
                return
            }

            val now = System.currentTimeMillis()
            unlockedApps.entries.removeIf { it.value < now }

            // Logic gate ensures visibility state remains accurate
            if (unlockedApps.containsKey(packageName)) {
                updateTimerVisibility()
            } else {
                removeFloatingTimer() // Proactive unmount
                checkBlockingLogic(packageName)
            }
        }
    }

    private fun isUninstallOrSettingsAttempt(packageName: String, event: AccessibilityEvent): Boolean {
        val pkg = packageName.lowercase()
        val isSystemApp = pkg.contains("settings") ||
                pkg.contains("packageinstaller") ||
                pkg.contains("securitycenter") || // Xiaomi
                pkg.contains("safecenter") ||     // Oppo
                pkg.contains("systemmanager") ||  // Huawei
                pkg.contains("seccontainer")      // Vivo

        if (!isSystemApp) return false

        // Ignore shield for the initial grace period after service start to allow onboarding to complete.
        if (System.currentTimeMillis() - serviceStartTime < Constants.SERVICE_START_GRACE_MS) {
            return false
        }

        // BUG-10: only intercept if the screen mentions BOTH "scrolliosis" AND "uninstall".
        // Previously, any Settings page that showed the app name (e.g. App Info, Permissions)
        // triggered a 120-second lockout. Now we require clear uninstall intent.
        val fastText = event.text.toString().lowercase()
        val hasScrolliosis = fastText.contains("scrolliosis")
        val hasUninstall = fastText.contains("uninstall")

        // Package installer is always an explicit uninstall flow
        if (pkg.contains("packageinstaller") && hasScrolliosis) return true
        // For other system settings, require BOTH keywords to avoid false positives
        if (hasScrolliosis && hasUninstall) return true

        // If not found in the quick scan, do a deep node scan for both keywords together.
        val rootNode = rootInActiveWindow ?: return false
        return try {
            scanNodeForText(rootNode, "scrolliosis") && scanNodeForText(rootNode, "uninstall")
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Error scanning accessibility node tree", e)
            false
        } finally {
            safeReleaseNode(rootNode)
        }
    }

    private fun scanNodeForText(node: AccessibilityNodeInfo?, targetText: String): Boolean {
        if (node == null) return false

        // Standard stable API calls for cross-OEM compatibility
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text.contains(targetText) || desc.contains(targetText)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = scanNodeForText(child, targetText)
                if (found) return true
            } finally {
                safeReleaseNode(child)
            }
        }
        return false
    }

    // AccessibilityNodeInfo.recycle() is deprecated on newer SDKs. Try to call close() when available,
    // otherwise fall back to recycle(). This helper centralizes that logic and logs failures.
    private fun safeReleaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            val closeMethod = node::class.java.getMethod("close")
            closeMethod.invoke(node)
        } catch (e: NoSuchMethodException) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (ex: Exception) {
                Log.w(Constants.TAG, "Failed to recycle node", ex)
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (ex: Exception) { Log.w(Constants.TAG, "Failed to release node", ex) }
        }
    }

    private fun handleSettingsInterception() {
        val now = System.currentTimeMillis()
        if (now < cooldownEndTime) {
            silentKill()
            showCustomToast("System modifications restricted during active focus.")
        } else {
            // Trigger hard block and navigate to the system purge screen.
            triggerHardBlock("com.android.settings", "system_purge_screen")
        }
    }

    private fun checkBlockingLogic(packageName: String) {
        serviceScope.launch {
            if (repository.isAppBlocked(packageName)) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    if (now < cooldownEndTime) {
                        silentKill()
                                    if (now - lastToastTime > Constants.COOLDOWN_TOAST_INTERVAL_MS) {
                                        lastToastTime = now
                                        showCustomToast("Gate locked. Cooldown active.")
                                    }
                    } else if (now - lastInterceptTime > 2000) {
                        lastInterceptTime = now
                        triggerHardBlock(packageName)
                    }
                }
            }
        }
    }

    private fun triggerHardBlock(packageName: String, targetRoute: String = "gatekeeper_screen") {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        val blockIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(Constants.EXTRA_BLOCKED_PACKAGE, packageName)
            putExtra(Constants.EXTRA_START_ROUTE, targetRoute)
        }
        startActivity(blockIntent)
    }

    private fun silentKill() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun updateTimerVisibility() {
        val foregroundPkg = currentForegroundPackage ?: return
        if (unlockedApps.containsKey(foregroundPkg)) {
            val expiration = unlockedApps[foregroundPkg] ?: return
            overlayController.mountTimerForPackage(foregroundPkg, expiration) {
                // onExpire callback
                unlockedApps.remove(foregroundPkg)
                triggerHardBlock(foregroundPkg)
            }
        } else {
            overlayController.removeTimer()
        }
    }

    private fun removeFloatingTimer() {
        overlayController.removeTimer()
    }

    private fun showCustomToast(message: String) {
        overlayController.showCustomToast(message)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingTimer()
        try { overlayController.release() } catch (e: Exception) { /* ignore */ }
        serviceScope.cancel()
        try { unregisterReceiver(communicationReceiver) } catch (e: Exception) { Log.w(Constants.TAG, "Failed to unregister receiver", e) }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Gate Protection",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Ensures Scrolliosis stays active." }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startEnforcementForeground() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gate Active")
            .setContentText("Focus protection is enabled.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This ensures the service process is flagged as "Sticky" in the OS scheduler
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Safety check to ensure the service info is reachable
        val info = serviceInfo ?: return

        // Request flags to allow window scanning and include non-important views.
        info.flags = info.flags or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        // Apply updated service info.
        this.serviceInfo = info
    }
}