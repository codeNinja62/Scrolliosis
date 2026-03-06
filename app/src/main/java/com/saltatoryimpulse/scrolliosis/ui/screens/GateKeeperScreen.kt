package com.saltatoryimpulse.scrolliosis.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saltatoryimpulse.scrolliosis.KnowledgeEntry
import com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository
import org.koin.androidx.compose.get
import com.saltatoryimpulse.scrolliosis.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GatekeeperScreen(
    targetPackage: String,
    onUnlockSuccess: (String) -> Unit,
    onCloseApp: () -> Unit
) {
    // BUG-08: state for shake animation + hint text (BackHandler registered after triggerHapticPulse)
    val shakeOffset = remember { Animatable(0f) }
    var lockedHintVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pm = context.packageManager
    val repository: IKnowledgeRepository = get()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState() // Essential for small screens/keyboard visibility

    /**
     * UNIVERSAL HAPTIC ENGINE
     * Provides tactile feedback for keystrokes and unlock readiness.
     */
    fun triggerHapticPulse(intensity: Int, duration: Long = 40) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity))
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) { /* Silent fail for hardware compatibility */ }
    }

    // SYSTEM TRAP: Disables back button/gestures on all Android versions.
    // BUG-08: no longer silent — shakes the lock icon + shows hint so users understand why nothing happened.
    BackHandler(enabled = true) {
        scope.launch {
            lockedHintVisible = true
            triggerHapticPulse(100, 30)
            repeat(3) {
                shakeOffset.animateTo(12f, tween(60))
                shakeOffset.animateTo(-12f, tween(60))
            }
            shakeOffset.animateTo(0f, tween(60))
            delay(2000)
            lockedHintVisible = false
        }
    }

    val appName = remember {
        try {
            val info = pm.getApplicationInfo(targetPackage, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { "Distraction" }
    }

    val defaultPrompts = remember {
        listOf(
            "What thought was in your head right before you tapped this app?",
            "What are you hoping to find in $appName right now?",
            "Are you opening this deliberately, or is it an automatic habit?",
            "How do you expect your mood to change after 5 minutes of this?"
        )
    }

    // BUG-04: prefer user-authored knowledge prompts; fall back to defaults if vault is empty
    var currentPrompt by remember { mutableStateOf(defaultPrompts.random()) }
    var promptLabel by remember { mutableStateOf("MINDFULNESS CHECK") }
    LaunchedEffect(Unit) {
        val customPrompt = withContext(Dispatchers.IO) { repository.getRandomCustomPrompt() }
        if (customPrompt != null) {
            currentPrompt = customPrompt.summary
            promptLabel = customPrompt.title
        }
    }

    var reflectionText by remember { mutableStateOf("") }
    val minCharacters = 80
    val isReadyToUnlock = reflectionText.trim().length >= minCharacters

    // HAPTIC FEEDBACK: Pulsing once when the user reaches the character goal
    LaunchedEffect(isReadyToUnlock) {
        if (isReadyToUnlock) triggerHapticPulse(200, 100)
    }

    // Scrollable column ensures UI isn't "broken" by the software keyboard on small devices
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))

    Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Locked",
                tint = PrimaryAccent,
                modifier = Modifier
                    .size(64.dp)
                    .offset(x = shakeOffset.value.dp)  // BUG-08: shake on back press
            )

            Spacer(modifier = Modifier.height(8.dp))

            // BUG-08: hint text that fades in when user tries to back out
            if (lockedHintVisible) {
                Text(
                    text = "Complete the reflection to proceed",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                    textAlign = TextAlign.Center
                )
            }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${appName.uppercase()} IS GATED",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextHighEmphasis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = promptLabel,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = PrimaryAccent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentPrompt,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    color = TextHighEmphasis
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = reflectionText,
            onValueChange = { newValue ->
                // BUG-11: reject clipboard pastes by capping single-event length increase.
                // Normal typing adds 1-2 chars; autocorrect replaces a word (~15 at most).
                // A paste of 80+ chars trivially bypasses the friction — discard it silently.
                val delta = newValue.length - reflectionText.length
                if (delta <= 15) {
                    reflectionText = newValue
                    // Light tactile feedback for "friction" writing
                    if (newValue.length % 5 == 0) triggerHapticPulse(50, 10)
                }
            },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            placeholder = {
                Text(
                    "Observe your intent. $minCharacters characters grants 5 minutes of access.",
                    color = TextMediumEmphasis.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                focusedContainerColor = SurfaceDark,
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = OutlineSubtle,
                focusedTextColor = TextHighEmphasis,
                unfocusedTextColor = TextHighEmphasis
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            val currentLength = reflectionText.trim().length
            Text(
                text = "$currentLength / $minCharacters",
                style = MaterialTheme.typography.labelMedium,
                color = if (isReadyToUnlock) PrimaryAccent else TextMediumEmphasis
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCloseApp,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("KEEP GATE CLOSED", fontWeight = FontWeight.ExtraBold, color = BackgroundDark)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    // UNIVERSAL PERSISTENCE:
                    // NonCancellable ensures the reflection is saved even if the
                    // system kills the process during the window transition.
                    withContext(Dispatchers.IO + NonCancellable) {
                        repository.insertEntry(
                            KnowledgeEntry(
                                title = "Trigger: $appName",
                                summary = reflectionText.trim()
                            )
                        )
                    }
                    onUnlockSuccess(targetPackage)
                }
            },
            enabled = isReadyToUnlock,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isReadyToUnlock) PrimaryAccent else OutlineSubtle
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isReadyToUnlock) "UNLOCK ACCESS" else "REFLECTION REQUIRED",
                fontWeight = FontWeight.Bold,
                color = if (isReadyToUnlock) TextHighEmphasis else TextMediumEmphasis
            )
        }

        Spacer(modifier = Modifier.height(48.dp)) // Keyboard breathing room
    }
}