package com.lagu.songfy.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentSilver,
    onPrimary = BackgroundNearBlack,
    secondary = AccentSilverDim,
    onSecondary = BackgroundNearBlack,
    background = BackgroundNearBlack,
    onBackground = TextPrimaryWhite,
    surface = SurfaceDarkNavy,
    onSurface = TextPrimaryWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondaryGray
)

private val LightColorScheme = darkColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = BackgroundNearBlack,
    onBackground = TextPrimaryWhite,
    surface = SurfaceDarkNavy,
    onSurface = TextPrimaryWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondaryGray
)

@Composable
fun SongfyTheme(
    darkTheme: Boolean = AppSettings.currentTheme == AppThemeMode.Dark,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
