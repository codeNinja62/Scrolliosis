package com.saltatoryimpulse.braingate.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saltatoryimpulse.braingate.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SystemPurgeScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // BACK BUTTON BLOCKADE: Traps the user at the system level.
    // This prevents gesture navigation or physical back buttons from bypassing the timer.
    BackHandler(enabled = true) { /* Intentional No-Op */ }

    var secondsLeft by remember { mutableIntStateOf(120) }
    val totalSeconds = 120f

    // CBT GROUNDING PALETTE: Specifically chosen to reduce ocular strain and cognitive arousal.
    val CalibrationAmber = Color(0xFFFFB300)
    val DeepStabilityBlue = Color(0xFF42A5F5)

    // Hardware-safe animation: Prevents jank on low-end Mediatek/Exynos processors.
    val animatedProgress by animateFloatAsState(
        targetValue = secondsLeft / totalSeconds,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "PurgeProgress"
    )

    /**
     * UNIVERSAL HAPTIC ENGINE:
     * This handles the API 31+ VibratorManager and falls back gracefully to
     * legacy Vibrator services for older Android versions.
     */
    fun triggerHapticPulse(intensity: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, intensity))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Silently fail if device lacks hardware or permission to prevent crashes
        }
    }

    // THE INESCAPABLE LOOP
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            // Tactile Anchoring
            when {
                secondsLeft <= 5 -> triggerHapticPulse(255) // Max intensity for final countdown
                secondsLeft % 15 == 0 -> triggerHapticPulse(80) // Rhythmic "heartbeat" pulse
            }

            delay(1000)
            secondsLeft--
        }

        // Final "Release" vibration (Double Pulse)
        triggerHapticPulse(200)
        delay(100)
        triggerHapticPulse(200)

        onComplete()
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
            text = "SYSTEM CALIBRATION ACTIVE",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            ),
            color = CalibrationAmber
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            // Background Track
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(240.dp),
                color = SurfaceDark,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round,
            )
            // Active Progress
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(240.dp),
                color = DeepStabilityBlue,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = TextHighEmphasis
                )
                Text(
                    text = "STABILIZING",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMediumEmphasis
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "BrainGate is currently recalibrating focus-sensitive pathways. This cooldown is mandatory to prevent dopamine spiking after a system settings breach.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
            color = TextMediumEmphasis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Visual heartbeat bar
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = DeepStabilityBlue.copy(alpha = 0.4f),
            trackColor = Color.Transparent
        )
    }
}