package com.drivesafe.kenya.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SafeGreen,
    onPrimary = CardLight,
    secondary = WarningAmber,
    onSecondary = Ink,
    tertiary = CameraPurple,
    onTertiary = CardLight,
    background = SurfaceLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = CardLight,
    onSurfaceVariant = MutedInk,
    error = DangerRed,
    onError = CardLight,
    outline = ColorOutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = SafeGreenLight,
    onPrimary = SurfaceDark,
    secondary = WarningAmber,
    onSecondary = SurfaceDark,
    tertiary = RoadBlue,
    onTertiary = CardLight,
    background = SurfaceDark,
    onBackground = CardLight,
    surface = SurfaceDark,
    onSurface = CardLight,
    surfaceVariant = CardDark,
    onSurfaceVariant = ColorTextMutedDark,
    error = DangerRed,
    onError = CardLight,
    outline = ColorOutlineDark
)

@Composable
fun DriveSafeKenyaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
