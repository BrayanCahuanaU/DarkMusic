package com.example.darkmusic.core.designsystem

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
import com.example.darkmusic.core.preferences.AccentColor
import com.example.darkmusic.core.preferences.ThemeMode

private fun accentPrimary(color: AccentColor): Color = when (color) {
    AccentColor.RED -> AppleMusicRed
    AccentColor.BLUE -> AccentBlue
    AccentColor.GREEN -> AccentGreen
    AccentColor.PURPLE -> AccentPurple
    AccentColor.ORANGE -> AccentOrange
}

@Composable
fun DarkMusicTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.RED,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primary = accentPrimary(accentColor)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = Surface1Dark,
            tertiary = SystemBlue,
            background = CanvasBlack,
            surface = Surface1Dark,
            onPrimary = LabelPrimaryDark,
            onSecondary = LabelPrimaryDark,
            onTertiary = LabelPrimaryDark,
            onBackground = LabelPrimaryDark,
            onSurface = LabelPrimaryDark,
            surfaceVariant = Surface2Dark,
            onSurfaceVariant = LabelSecondaryDark
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = Surface1Light,
            tertiary = SystemBlue,
            background = CanvasWhite,
            surface = Surface1Light,
            onPrimary = Color.White,
            onSecondary = LabelPrimaryLight,
            onTertiary = LabelPrimaryLight,
            onBackground = LabelPrimaryLight,
            onSurface = LabelPrimaryLight,
            surfaceVariant = Surface2Light,
            onSurfaceVariant = LabelSecondaryLight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
