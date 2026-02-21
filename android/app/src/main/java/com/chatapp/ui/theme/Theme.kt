package com.chatapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors: ColorScheme = lightColorScheme(
    primary = AppPrimary,
    secondary = AppSecondary,
    tertiary = AppTertiary,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
    onPrimary = AppOnPrimary,
    onSurface = AppOnSurface,
    onBackground = AppOnBackground,
    outline = AppOutline
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = AppPrimary,
    secondary = AppSecondary
)

@Composable
fun ChatAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
