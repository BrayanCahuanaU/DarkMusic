package com.example.darkmusic

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.darkmusic.core.designsystem.DarkMusicTheme
import com.example.darkmusic.core.preferences.AccentColor
import com.example.darkmusic.core.preferences.SettingsManager
import com.example.darkmusic.core.preferences.ThemeMode
import com.example.darkmusic.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = LocalContext.current.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            var themeMode by remember { mutableStateOf(settingsManager.getThemeMode()) }
            var accentColor by remember { mutableStateOf(settingsManager.getAccentColor()) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    themeMode = settingsManager.getThemeMode()
                    accentColor = settingsManager.getAccentColor()
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            DarkMusicTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                MainScreen()
            }
        }
    }
}
