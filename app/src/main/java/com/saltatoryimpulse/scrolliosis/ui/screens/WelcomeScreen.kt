package com.saltatoryimpulse.scrolliosis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saltatoryimpulse.scrolliosis.ui.components.ScrolliosisPrimaryButton
import com.saltatoryimpulse.scrolliosis.ui.theme.*

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon / Logo
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Scrolliosis Logo",
            tint = PrimaryAccent,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // PRIMARY HEADLINE
        // Matches "SYSTEM SETUP" hierarchy: headlineMedium + ExtraBold + 1.sp spacing
        Text(
            text = "Welcome to Scrolliosis",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextHighEmphasis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // BODY DESCRIPTION
        // Matches Onboarding secondary text hierarchy: bodyMedium + 22.sp lineHeight
        Text(
            text = "Hard-block your distractions. Reclaim your focus. Scrolliosis physically stops you from opening the apps that steal your time.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp // Exactly matches OnboardingScreen line height
            ),
            color = TextMediumEmphasis
        )

        Spacer(modifier = Modifier.height(64.dp))

        // PRIMARY ACTION
        ScrolliosisPrimaryButton(
            text = "GET STARTED",
            onClick = onGetStartedClick
        )
    }
}