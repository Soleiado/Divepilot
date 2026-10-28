package com.divepilot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DiveColorScheme = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    secondary = Color(0xFF80DEEA),
    background = Color(0xFF0D1B2A),
    surface = Color(0xFF12263A),
)

@Composable
fun DivePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DiveColorScheme, content = content)
}
