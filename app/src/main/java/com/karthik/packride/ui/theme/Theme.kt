package com.karthik.packride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Aug 30, 2026 — rewritten to use the real values from ui/theme/DesignSystem.kt
// (ported straight from iOS's DesignSystem.swift + LoginView.swift's Color
// extension) instead of 5 made-up tokens, including a coral that didn't even
// match iOS's real accent (#E85A4F here vs iOS's actual #FF6A00). This
// ColorScheme is a fallback base for any stock Material3 component still in
// use (SnackBar, dialogs, etc.) — new/updated screens should prefer the
// `Pr`/`PrFont`/`Pr*` composables in DesignSystem.kt directly over
// MaterialTheme.colorScheme, since PackRide's design language doesn't map
// cleanly onto Material3's semantic color roles.
val PrCoral = Color(0xFFFF6A00)
val PrTeal = Color(0xFF2B6E85)

private val LightColors = lightColorScheme(
    primary = PrCoral,
    secondary = PrTeal,
    background = Color(0xFFF8F8F8),
    onBackground = Color(0xFF1A1A1C),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1C),
    outline = Color(0xFFE6E6E8)
)

private val DarkColors = darkColorScheme(
    primary = PrCoral,
    secondary = PrTeal,
    background = Color(0xFF121213),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1F1F21),
    onSurface = Color(0xFFF5F5F5),
    outline = Color(0xFF38383D)
)

@Composable
fun PackRideTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
