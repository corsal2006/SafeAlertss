package com.siddesh.smsalerts.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = Color.White,
    primaryContainer = EmergencyRedDark,
    onPrimaryContainer = Color.White,
    secondary = StatusInfo,
    onSecondary = Color.White,
    error = StatusError,
    onError = Color.White,
    background = RawBackgroundDark,
    onBackground = RawTextPrimaryDark,
    surface = RawSurfaceDark,
    onSurface = RawTextPrimaryDark,
    surfaceVariant = RawSurfaceCardDark,
    onSurfaceVariant = RawTextSecondaryDark,
    outline = RawCardBorderDark,
    outlineVariant = RawCardBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmergencyRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = Color(0xFF9F1239),
    secondary = StatusInfo,
    onSecondary = Color.White,
    error = StatusError,
    onError = Color.White,
    background = RawBackgroundLight,
    onBackground = RawTextPrimaryLight,
    surface = RawSurfaceLight,
    onSurface = RawTextPrimaryLight,
    surfaceVariant = RawSurfaceCardLight,
    onSurfaceVariant = RawTextSecondaryLight,
    outline = RawCardBorderLight,
    outlineVariant = RawCardBorderLight
)

@Composable
fun SafeAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgColor = if (darkTheme) RawBackgroundDark.toArgb() else RawBackgroundLight.toArgb()
            val navColor = if (darkTheme) RawSurfaceDark.toArgb() else RawSurfaceLight.toArgb()
            window.statusBarColor = bgColor
            window.navigationBarColor = navColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
