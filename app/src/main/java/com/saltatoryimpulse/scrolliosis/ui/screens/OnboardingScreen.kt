package com.saltatoryimpulse.scrolliosis.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saltatoryimpulse.scrolliosis.ManufacturerUtils
import com.saltatoryimpulse.scrolliosis.PermissionUtils
import com.saltatoryimpulse.scrolliosis.data.InstalledAppCatalog
import com.saltatoryimpulse.scrolliosis.ui.components.ScrolliosisPrimaryButton
import com.saltatoryimpulse.scrolliosis.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.get

// DEFINITIVE SETUP STATES
enum class SetupStep { NONE, OVERLAY, ACCESSIBILITY, MANUFACTURER }

@Composable
fun OnboardingScreen(
    needsAccessibility: Boolean,
    needsOverlay: Boolean,
    needsNotifications: Boolean,
    onCheckPermissions: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val installedAppCatalog: InstalledAppCatalog = get()
    val scope = rememberCoroutineScope()

    var hasStartedSetup by remember { mutableStateOf(false) }
    var currentDialog by remember { mutableStateOf(SetupStep.NONE) }
    var isPrimingInstalledApps by remember { mutableStateOf(false) }

    // PERSISTENCE TRACKER: Prevents looping on OEMs that don't report status back to the OS
    var manufacturerStepAttempted by remember { mutableStateOf(false) }
    // BUG-06: track whether notification permission was already attempted (granted OR denied)
    // so the waterfall doesn't stall if the user refuses the permission dialog
    var notifAttempted by remember { mutableStateOf(false) }
    var lastExecutionTime by remember { mutableLongStateOf(0L) }

    val batteryNeeded = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val notifsNeeded = needsNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val oemNeeded = ManufacturerUtils.isAggressiveManufacturer() && !manufacturerStepAttempted

    val allGranted = !needsAccessibility && !needsOverlay && !batteryNeeded && !notifsNeeded && !oemNeeded

    fun finalizeSetup() {
        if (isPrimingInstalledApps) return

        scope.launch {
            isPrimingInstalledApps = true
            try {
                installedAppCatalog.primeInstalledApps()
            } finally {
                isPrimingInstalledApps = false
                hasStartedSetup = false
                onCheckPermissions()
            }
        }
    }

    /**
     * THE UNIVERSAL WATERFALL:
     * This logic is prioritized by 'Friction Level'. We handle the most disruptive
     * permissions (Accessibility) last to ensure the user is already committed to the process.
     */
    fun executeNextStep() {
        val now = System.currentTimeMillis()
        if (now - lastExecutionTime < 1000L) return
        lastExecutionTime = now

        val isNotifsNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val isBatteryNeeded = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        val isOverlayNeeded = !Settings.canDrawOverlays(context)
        val isAccessNeeded = !PermissionUtils.isAccessibilityServiceEnabled(context)

        when {
            // STEP 1: NOTIFICATIONS — only request if not yet attempted; skip gracefully if denied
            isNotifsNeeded && !notifAttempted -> { currentDialog = SetupStep.NONE }
            // STEP 2: STANDARD BATTERY (Whitelist)
            isBatteryNeeded -> {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }

            // STEP 3: OVERLAY (The Visual Hook)
            isOverlayNeeded -> currentDialog = SetupStep.OVERLAY

            // STEP 4: ACCESSIBILITY (The Core Engine)
            isAccessNeeded -> currentDialog = SetupStep.ACCESSIBILITY

            // STEP 5: OEM HARDENING (Auto-Start / No Restrictions)
            // This only triggers for Samsung, Xiaomi, etc., to seal the persistence chain.
            ManufacturerUtils.isAggressiveManufacturer() && !manufacturerStepAttempted -> {
                currentDialog = SetupStep.MANUFACTURER
            }

            // FINAL: CALIBRATION COMPLETE
            else -> {
                finalizeSetup()
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            notifAttempted = true  // BUG-06: advance waterfall regardless of granted/denied
            onCheckPermissions()
            if (hasStartedSetup) {
                scope.launch {
                    delay(300)
                    executeNextStep()
                }
            }
        }
    )

    // LIFECYCLE SYNC: Automatically triggers the next step when returning from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onCheckPermissions()
                if (hasStartedSetup) {
                    scope.launch {
                        delay(500) // Essential for transition stability on slower CPUs
                        executeNextStep()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SYSTEM SETUP",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextHighEmphasis
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (allGranted) "Alignment confirmed. Activating Gate..."
            else "Scrolliosis requires deep system synchronization to prevent neural bypass during focus sessions.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = TextMediumEmphasis
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (!allGranted) {
            ScrolliosisPrimaryButton(
                text = when {
                    isPrimingInstalledApps -> "SYNCING APPS..."
                    hasStartedSetup -> "CALIBRATING..."
                    else -> "START SETUP"
                },
                onClick = {
                    hasStartedSetup = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                        lastExecutionTime = System.currentTimeMillis()
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        executeNextStep()
                    }
                },
                enabled = !hasStartedSetup && !isPrimingInstalledApps
            )
        } else {
            CircularProgressIndicator(color = PrimaryAccent, modifier = Modifier.size(48.dp))
            LaunchedEffect(Unit) {
                delay(300)
                finalizeSetup()
            }
        }
    }

    // --- DIALOG OVERLAYS ---

    if (currentDialog == SetupStep.OVERLAY) {
        ScrolliosisDialog(
            title = "Overlay Authority",
            description = "Enables the reflection gate to physically occupy the screen space of distracting apps.",
            confirmText = "GRANT AUTHORITY",
            onConfirm = {
                currentDialog = SetupStep.NONE
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            },
            onDismiss = {
                currentDialog = SetupStep.NONE
                hasStartedSetup = false
            }
        )
    }

    if (currentDialog == SetupStep.ACCESSIBILITY) {
        ScrolliosisDialog(
            title = "Enforcement Core",
            description = "Find 'Scrolliosis' in 'Installed Apps' and toggle it ON. This is the hardware sensor that detects distraction launches.",
            confirmText = "OPEN SETTINGS",
            onConfirm = {
                currentDialog = SetupStep.NONE
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = {
                currentDialog = SetupStep.NONE
                hasStartedSetup = false
            }
        )
    }

    if (currentDialog == SetupStep.MANUFACTURER) {
        ScrolliosisDialog(
            title = "OEM Restriction Found",
            description = "Your device (Samsung/Xiaomi/Oppo) has aggressive task killers. To stay protected, please set Scrolliosis to 'No Restrictions' or enable 'Auto-Start' in the next menu.",
            confirmText = "HARDEN PERSISTENCE",
            onConfirm = {
                currentDialog = SetupStep.NONE
                manufacturerStepAttempted = true
                ManufacturerUtils.openPowerManagementSettings(context)
            },
            onDismiss = {
                currentDialog = SetupStep.NONE
                manufacturerStepAttempted = true // Allow progression even if user refuses
            }
        )
    }
}

@Composable
fun ScrolliosisDialog(
    title: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextHighEmphasis) },
        text = { Text(description, color = TextMediumEmphasis) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = PrimaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMediumEmphasis)
            }
        }
    )
}