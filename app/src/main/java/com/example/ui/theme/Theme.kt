package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SpaceDarkColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = SpaceBlack,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldBright,
    secondary = OrbitalCyan,
    onSecondary = SpaceBlack,
    secondaryContainer = SpaceSurfaceElevated,
    onSecondaryContainer = SpaceWhite,
    tertiary = SolarAmber,
    onTertiary = SpaceBlack,
    background = SpaceBlack,
    onBackground = SpaceWhite,
    surface = SpaceSurface,
    onSurface = SpaceWhite,
    surfaceVariant = SpaceSurfaceElevated,
    onSurfaceVariant = SpaceMutedText,
    outline = SpaceBorder,
    error = AlertRed,
    onError = SpaceWhite
)

@Composable
fun CosmosSimulatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SpaceDarkColorScheme,
        typography = SpaceTypography,
        content = content
    )
}
