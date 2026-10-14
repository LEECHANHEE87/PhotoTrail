package com.example.phototrail.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PhotoTrailPrimary,
    onPrimary = PhotoTrailOnPrimary,
    primaryContainer = PhotoTrailPrimaryContainer,
    onPrimaryContainer = PhotoTrailOnPrimaryContainer,
    secondary = PhotoTrailSecondary,
    onSecondary = PhotoTrailOnSecondary,
    secondaryContainer = PhotoTrailSecondaryContainer,
    onSecondaryContainer = PhotoTrailOnSecondaryContainer,
    tertiary = PhotoTrailAccent,
    onTertiary = PhotoTrailOnAccent,
    tertiaryContainer = PhotoTrailAccentContainer,
    onTertiaryContainer = PhotoTrailOnAccentContainer,
    background = PhotoTrailBackground,
    surface = PhotoTrailSurface,
    onBackground = PhotoTrailTextPrimary,
    onSurface = PhotoTrailTextPrimary,
    surfaceVariant = PhotoTrailSurfaceMuted,
    onSurfaceVariant = PhotoTrailTextSecondary,
    outline = PhotoTrailOutline,
    outlineVariant = PhotoTrailOutline.copy(alpha = 0.65f)
)

private val DarkColorScheme = darkColorScheme(
    primary = PhotoTrailPrimaryDark,
    onPrimary = PhotoTrailOnPrimaryDark,
    primaryContainer = PhotoTrailPrimaryContainerDark,
    onPrimaryContainer = PhotoTrailOnPrimaryContainerDark,
    secondary = PhotoTrailSecondaryDark,
    onSecondary = PhotoTrailOnSecondaryDark,
    secondaryContainer = PhotoTrailSecondaryContainerDark,
    onSecondaryContainer = PhotoTrailOnSecondaryContainerDark,
    tertiary = PhotoTrailAccentDark,
    onTertiary = PhotoTrailOnAccentDark,
    tertiaryContainer = PhotoTrailAccentContainerDark,
    onTertiaryContainer = PhotoTrailOnAccentContainerDark,
    background = PhotoTrailBackgroundDark,
    surface = PhotoTrailSurfaceDark,
    onBackground = PhotoTrailTextPrimaryDark,
    onSurface = PhotoTrailTextPrimaryDark,
    surfaceVariant = PhotoTrailSurfaceMutedDark,
    onSurfaceVariant = PhotoTrailTextSecondaryDark,
    outline = PhotoTrailOutlineDark,
    outlineVariant = PhotoTrailOutlineDark.copy(alpha = 0.75f)
)

@Composable
fun PhotoTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
