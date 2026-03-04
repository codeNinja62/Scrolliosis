package com.saltatoryimpulse.braingate.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Mapping our palette to the Material 3 system
private val BrainGateColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextHighEmphasis,
    surface = SurfaceDark,
    onSurface = TextHighEmphasis,
    onSurfaceVariant = TextMediumEmphasis,
    outline = OutlineSubtle,
    error = ErrorRed
)

@Composable
fun BrainGateTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            // Safely find the Activity. If we are in a Service, this returns null.
            val activity = view.context.findActivity()

            // Only try to paint the status bar if we actually found a window!
            activity?.window?.let { window ->
                @Suppress("DEPRECATION")
                run {
                    window.statusBarColor = BackgroundDark.toArgb()
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = BrainGateColorScheme,
        typography = AppTypography,
        content = content
    )
}

// Helper function to safely unwrap Context (handles generic ContextWrappers)
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}