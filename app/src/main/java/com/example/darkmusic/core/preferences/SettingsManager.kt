package com.example.darkmusic.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOWNLOAD_PATH = "download_path"
    }

    fun getDownloadPath(): String {
        val defaultPath = File(context.getExternalFilesDir(null), "music").absolutePath
        return prefs.getString(KEY_DOWNLOAD_PATH, defaultPath) ?: defaultPath
    }

    fun setDownloadPath(path: String) {
        prefs.edit().putString(KEY_DOWNLOAD_PATH, path).apply()
    }
}
