package com.movies.premium.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = ChqPrimary,
    onPrimary = ChqOnPrimary,
    primaryContainer = ChqPrimaryContainer,
    onPrimaryContainer = ChqOnPrimaryContainer,
    secondary = ChqSecondary,
    onSecondary = ChqOnSecondary,
    secondaryContainer = ChqSecondaryContainer,
    onSecondaryContainer = ChqOnSecondaryContainer,
    tertiary = ChqTertiary,
    onTertiary = ChqOnTertiary,
    tertiaryContainer = ChqTertiaryContainer,
    onTertiaryContainer = ChqOnTertiaryContainer,
    error = ChqError,
    onError = ChqOnError,
    errorContainer = ChqErrorContainer,
    onErrorContainer = ChqOnErrorContainer,
    background = ChqBackground,
    onBackground = ChqOnBackground,
    surface = ChqSurface,
    onSurface = ChqOnSurface,
    surfaceVariant = ChqSurfaceVariant,
    onSurfaceVariant = ChqOnSurfaceVariant,
    surfaceContainer = ChqSurfaceContainer,
    surfaceContainerHigh = ChqSurfaceContainerHigh,
    surfaceContainerHighest = ChqSurfaceContainerHighest,
    surfaceContainerLow = ChqSurfaceContainerLow,
    surfaceContainerLowest = ChqSurfaceContainerLowest,
    outline = ChqOutline,
    outlineVariant = ChqOutlineVariant,
    inverseSurface = ChqInverseSurface,
    inverseOnSurface = ChqInverseOnSurface,
    inversePrimary = ChqInversePrimary,
    scrim = ChqScrim,
)

@Composable
fun CinemaHQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** User-controlled UI scale from Settings; 1.0 is the default. */
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = DarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = tvTypography(textScale),
        content = content,
    )
}
