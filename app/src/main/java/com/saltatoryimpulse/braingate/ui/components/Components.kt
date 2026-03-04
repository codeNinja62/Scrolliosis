package com.saltatoryimpulse.braingate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saltatoryimpulse.braingate.ui.theme.*

// --- 1. PRIMARY ACTION BUTTON ---
@Composable
fun BrainGatePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryAccent,
            contentColor = BackgroundDark,
            disabledContainerColor = SurfaceDark,
            disabledContentColor = TextMediumEmphasis
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        )
    }
}

// --- 2. SECONDARY / ESCAPE BUTTON ---
@Composable
fun BrainGateSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = TextMediumEmphasis
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

// --- 3. PREMIUM BACK BUTTON (REUSABLE) ---
@Composable
fun BrainGateBackButton(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Light tick
            onClick()
        },
        modifier = Modifier
            .size(48.dp) // Professional standard touch target
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = 0.4f))
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = TextHighEmphasis,
            modifier = Modifier.size(24.dp)
        )
    }
}

// --- 4. SENIOR TOP NAVIGATION BAR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainGateTopBar(
    onBack: () -> Unit,
    title: String = ""
) {
    CenterAlignedTopAppBar(
        title = {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextHighEmphasis
                )
            }
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                BrainGateBackButton(onClick = onBack)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = TextHighEmphasis
        ),
        modifier = Modifier.statusBarsPadding() // Ensures it doesn't hide under the clock/battery
    )
}

// --- 5. CENTERED LOADING SPINNER ---
@Composable
fun CenteredLoader(text: String = "") {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = PrimaryAccent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        if (text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = text,
                color = TextMediumEmphasis,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.8.sp
            )
        }
    }
}