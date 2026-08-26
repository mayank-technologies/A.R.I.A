package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF040711),
    primaryContainer = Color(0xFF0284C7),
    onPrimaryContainer = Color.White,
    secondary = HologramBlue,
    onSecondary = Color(0xFF040711),
    secondaryContainer = Color(0x3300E5FF),
    tertiary = ReactorGold,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderCyan
)

@Composable
fun AriaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}


