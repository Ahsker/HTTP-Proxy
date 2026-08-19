package com.example.ui.theme

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
import com.example.model.ThemeMode

private val NaturalLightColorScheme = lightColorScheme(
    primary = NaturalOchrePrimary,
    onPrimary = NaturalSurface,
    primaryContainer = NaturalHeroContainer,
    onPrimaryContainer = NaturalTextPrimary,
    secondary = NaturalGreenSuccess,
    onSecondary = NaturalSurface,
    secondaryContainer = NaturalGreenTint,
    onSecondaryContainer = NaturalTextPrimary,
    tertiary = NaturalOrangeUpload,
    background = NaturalBackground,
    onBackground = NaturalTextPrimary,
    surface = NaturalSurface,
    onSurface = NaturalTextPrimary,
    surfaceVariant = NaturalSurfaceElevated,
    onSurfaceVariant = NaturalTextSecondary,
    outline = NaturalBorder
)

private val NaturalDarkColorScheme = darkColorScheme(
    primary = DarkNaturalOchre,
    onPrimary = DarkNaturalBackground,
    primaryContainer = DarkNaturalHeroContainer,
    onPrimaryContainer = DarkNaturalTextPrimary,
    secondary = NaturalGreenBright,
    onSecondary = DarkNaturalBackground,
    secondaryContainer = DarkNaturalGreenTint,
    onSecondaryContainer = DarkNaturalTextPrimary,
    tertiary = NaturalOrangeUpload,
    background = DarkNaturalBackground,
    onBackground = DarkNaturalTextPrimary,
    surface = DarkNaturalSurface,
    onSurface = DarkNaturalTextPrimary,
    surfaceVariant = DarkNaturalSurfaceElevated,
    onSurfaceVariant = DarkNaturalTextSecondary,
    outline = DarkNaturalBorder
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = if (isDark) NaturalDarkColorScheme else NaturalLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = (if (isDark) DarkNaturalSurfaceNav else NaturalSurfaceNav).toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
