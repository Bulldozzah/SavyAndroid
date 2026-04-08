package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WiseUpColorScheme = lightColorScheme(
    primary = Green600,
    onPrimary = Color.White,
    primaryContainer = Green100,
    secondary = Blue500,
    onSecondary = Color.White,
    background = Color(0xFFEDE8EA),
    surface = Color.White,
    onBackground = Color(0xFF1F2937),
    onSurface = Color(0xFF1F2937),
    error = Color(0xFFEF4444)
)

@Composable
fun WiseUpTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WiseUpColorScheme,
        typography = Typography,
        content = content
    )
}