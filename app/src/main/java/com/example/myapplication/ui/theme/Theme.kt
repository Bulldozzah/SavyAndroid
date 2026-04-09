package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WiseUpColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue600,
    secondary = Orange500,
    onSecondary = Color.White,
    secondaryContainer = Orange100,
    onSecondaryContainer = Color(0xFF5D3A00),
    tertiary = Green600,
    onTertiary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1D26),
    surface = Color.White,
    onSurface = Color(0xFF1A1D26),
    surfaceVariant = Color(0xFFF5F6F8),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFEF4444),
    onError = Color.White
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