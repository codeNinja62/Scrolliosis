package com.saltatoryimpulse.braingate.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
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
import com.saltatoryimpulse.braingate.KnowledgeEntry
import com.saltatoryimpulse.braingate.data.IKnowledgeRepository
import org.koin.androidx.compose.get
import com.saltatoryimpulse.braingate.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GatekeeperScreen(
    targetPackage: String,
    onUnlockSuccess: (String) -> Unit,
    onCloseApp: () -> Unit
) {
    // SYSTEM TRAP: Disables back button/gestures on all Android versions
    BackHandler(enabled = true) { /* Locked by design */ }

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

    val appName = remember {
        try {
            val info = pm.getApplicationInfo(targetPackage, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { "Distraction" }
    }

    val prompts = remember {
        listOf(
            "What thought was in your head right before you tapped this app?",
            "What are you hoping to find in $appName right now?",
            "Are you opening this deliberately, or is it an automatic habit?",
            "How do you expect your mood to change after 5 minutes of this?"
        )
    }
    val currentPrompt = remember { prompts.random() }

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
            modifier = Modifier.size(64.dp)
        )

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
                    text = "MINDFULNESS CHECK",
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
            onValueChange = {
                reflectionText = it
                // Light tactile feedback for "friction" writing
                if (it.length % 5 == 0) triggerHapticPulse(50, 10)
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