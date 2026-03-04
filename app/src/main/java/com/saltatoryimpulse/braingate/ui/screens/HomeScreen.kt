package com.saltatoryimpulse.braingate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saltatoryimpulse.braingate.ui.components.BrainGatePrimaryButton
import com.saltatoryimpulse.braingate.ui.components.BrainGateSecondaryButton
import com.saltatoryimpulse.braingate.ui.theme.*

@Composable
fun HomeScreen(
    blockedAppCount: Int,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isGuarding = blockedAppCount > 0
    val pm = LocalContext.current.packageManager
    LaunchedEffect(Unit) { AppCache.getInstalledApps(pm) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- DYNAMIC HEADER ---
        Text(
            text = "BrainGate Core",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextHighEmphasis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- DYNAMIC STATUS INDICATOR ---
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(if (isGuarding) PrimaryAccent.copy(alpha = 0.1f) else SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (isGuarding) PrimaryAccent.copy(alpha = 0.3f) else OutlineSubtle,
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGuarding) PrimaryAccent else TextMediumEmphasis)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGuarding) "MONITORING $blockedAppCount APPS" else "SYSTEM IDLE",
                color = if (isGuarding) PrimaryAccent else TextMediumEmphasis,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (!isGuarding) {
            // --- EMPTY STATE UI ---
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Lock",
                tint = TextMediumEmphasis.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your focus is currently unguarded.",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = TextHighEmphasis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "BrainGate is installed, but you haven't selected any distracting apps to block yet.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = TextMediumEmphasis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            BrainGatePrimaryButton(
                text = "CONFIGURE BLOCKED APPS",
                onClick = onOpenSettings
            )
        } else {
            // --- ACTIVE STATE UI ---
            BrainGatePrimaryButton(
                text = "OPEN REFLECTION LOG",
                onClick = onOpenVault
            )

            Spacer(modifier = Modifier.height(16.dp))

            BrainGateSecondaryButton(
                text = "EDIT BLOCKED APPS",
                onClick = onOpenSettings
            )
        }
    }
}