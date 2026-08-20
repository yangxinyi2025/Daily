package com.daily.life.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DailyColorScheme = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = SkySurface,
    primaryContainer = SkySurfaceMuted,
    onPrimaryContainer = SkyInk,
    secondary = SkySecondary,
    onSecondary = SkySurface,
    secondaryContainer = SkySurfaceMuted,
    onSecondaryContainer = SkyInk,
    tertiary = SkyAccent,
    onTertiary = SkySurface,
    tertiaryContainer = SkySurfaceMuted,
    onTertiaryContainer = SkyInk,
    background = SkyBackground,
    onBackground = SkyInk,
    surface = SkySurface,
    onSurface = SkyInk,
    surfaceVariant = SkySurfaceMuted,
    onSurfaceVariant = SkyInk.copy(alpha = 0.72f),
    error = SkyError,
    onError = SkySurface,
    errorContainer = SkyWarm.copy(alpha = 0.20f),
    onErrorContainer = SkyInk,
    outline = SkyOutline,
    outlineVariant = SkyOutline.copy(alpha = 0.55f),
    scrim = SkyInk.copy(alpha = 0.45f)
)

@Composable
fun DailyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DailyColorScheme,
        typography = DailyTypography,
        shapes = DailyShapes,
        content = content
    )
}
