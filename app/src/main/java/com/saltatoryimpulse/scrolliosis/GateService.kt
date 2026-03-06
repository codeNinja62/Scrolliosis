package com.saltatoryimpulse.scrolliosis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.*
import com.saltatoryimpulse.scrolliosis.overlay.OverlayController
import java.util.concurrent.ConcurrentHashMap

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
    private var lastForegroundPackage: String? = null
    private var usageStatsMonitorJob: Job? = null

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
        overlayController.prepareBlockingShield()
        startUsageStatsMonitor()

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
        val eventType = event?.eventType ?: return
        val isForegroundSignal = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        if (!isForegroundSignal) return

        val packageName = event.packageName?.toString() ?: return
        handleForegroundPackage(packageName, event)
    }

    private fun handleForegroundPackage(packageName: String, event: AccessibilityEvent? = null) {
        currentForegroundPackage = packageName
        lastForegroundPackage = packageName

        if (packageName == packageName()) {
            overlayController.removeBlockingShield()
        }

        if (event != null && isProtectedSettingsAttempt(packageName, event)) {
            handleSettingsInterception()
            return
        }

        val now = System.currentTimeMillis()
        unlockedApps.entries.removeIf { it.value < now }

        if (unlockedApps.containsKey(packageName)) {
            updateTimerVisibility()
        } else {
            removeFloatingTimer()
            checkBlockingLogic(packageName)
        }
    }

    private fun isProtectedSettingsAttempt(packageName: String, event: AccessibilityEvent): Boolean {
        val pkg = packageName.lowercase()
        val isSystemApp = pkg.contains("settings") ||
                pkg.contains("packageinstaller") ||
                pkg.contains("securitycenter") || // Xiaomi
                pkg.contains("safecenter") ||     // Oppo
                pkg.contains("systemmanager") ||  // Huawei
                pkg.contains("seccontainer") ||   // Vivo
                pkg.contains("permissioncontroller")

        if (!isSystemApp) return false

        // Ignore shield for the initial grace period after service start to allow onboarding to complete.
        if (System.currentTimeMillis() - serviceStartTime < Constants.SERVICE_START_GRACE_MS) {
            return false
        }

        if (pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) {
            return true
        }

        if (pkg.contains("settings") || pkg.contains("securitycenter") || pkg.contains("safecenter") ||
            pkg.contains("systemmanager") || pkg.contains("seccontainer")) {
            return true
        }

        val fastText = event.text.toString().lowercase()
        val targetsScrolliosis = fastText.contains("scrolliosis") ||
            fastText.contains(packageName.lowercase()) ||
            fastText.contains(packageName())

        if (targetsScrolliosis) return true

        val rootNode = rootInActiveWindow ?: return false
        return try {
            scanNodeForAnyText(rootNode, listOf("scrolliosis", packageName(), packageName.lowercase()))
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Error scanning accessibility node tree", e)
            false
        } finally {
            safeReleaseNode(rootNode)
        }
    }

    private fun packageName(): String = applicationContext.packageName

    private fun scanNodeForAnyText(node: AccessibilityNodeInfo?, targetTexts: List<String>): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (targetTexts.any { target -> text.contains(target) || desc.contains(target) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = scanNodeForAnyText(child, targetTexts)
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
            triggerHardBlock(packageName(), "system_purge_screen")
        }
    }

    private fun checkBlockingLogic(packageName: String) {
        serviceScope.launch {
            if (repository.isAppBlocked(packageName)) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    if (now < cooldownEndTime) {
                        overlayController.showBlockingShield()
                        silentKill()
                                    if (now - lastToastTime > Constants.COOLDOWN_TOAST_INTERVAL_MS) {
                                        lastToastTime = now
                                        showCustomToast("Gate locked. Cooldown active.")
                                    }
                    } else if (now - lastInterceptTime > Constants.BLOCK_INTERCEPT_DEBOUNCE_MS) {
                        lastInterceptTime = now
                        triggerHardBlock(packageName)
                    }
                }
            }
        }
    }

    private fun triggerHardBlock(packageName: String, targetRoute: String = "gatekeeper_screen") {
        overlayController.showBlockingShield()
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }

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
        overlayController.showBlockingShield()
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun startUsageStatsMonitor() {
        usageStatsMonitorJob?.cancel()
        usageStatsMonitorJob = serviceScope.launch {
            while (isActive) {
                if (PermissionUtils.hasUsageAccess(this@GateService)) {
                    val foregroundPackage = PermissionUtils.getForegroundPackageFromUsageStats(this@GateService)
                    if (!foregroundPackage.isNullOrBlank() && foregroundPackage != lastForegroundPackage) {
                        withContext(Dispatchers.Main) {
                            handleForegroundPackage(foregroundPackage)
                        }
                    }
                }

                delay(Constants.FOREGROUND_POLL_INTERVAL_MS)
            }
        }
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
        overlayController.removeBlockingShield()
        usageStatsMonitorJob?.cancel()
        try { overlayController.release() } catch (e: Exception) { /* ignore */ }
        serviceScope.cancel()
        try { unregisterReceiver(communicationReceiver) } catch (e: Exception) { Log.w(Constants.TAG, "Failed to unregister receiver", e) }
        scheduleServiceRevive()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleServiceRevive()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Safety check to ensure the service info is reachable
        val info = serviceInfo ?: return

        // Request flags to allow window scanning and include non-important views.
        info.eventTypes = info.eventTypes or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        // Apply updated service info.
        this.serviceInfo = info
    }

    private fun scheduleServiceRevive() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reviveIntent = Intent(this, BootReceiver::class.java).apply {
            action = Constants.ACTION_ENSURE_SERVICE
            `package` = packageName()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            404,
            reviveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + Constants.SERVICE_REVIVE_DELAY_MS
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}