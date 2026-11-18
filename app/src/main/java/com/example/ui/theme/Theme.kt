package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = CoralContainer,
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = AmberSecondary,
    onSecondary = Color(0xFF281600),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = MintTertiary,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = MintContainer,
    onTertiaryContainer = Color(0xFFA7FFE0),
    background = ObsidianBg,
    onBackground = OnDarkText,
    surface = ObsidianSurface,
    onSurface = OnDarkText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = OnDarkMuted,
    outline = ObsidianOutline
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0E8),
    onPrimaryContainer = Color(0xFF3E0014),
    secondary = AmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBD6),
    onSecondaryContainer = Color(0xFF331B00),
    tertiary = Color(0xFF00875A),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5A6075),
    outline = LightOutline
)

@Composable
fun YourInstaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = YourInstaTypography,
        content = content
    )
}
