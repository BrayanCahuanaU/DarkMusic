package com.example.darkmusic.core.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class DownloadFormat { M4A, WEBM, MP3 }
enum class AccentColor { RED, BLUE, GREEN, PURPLE, ORANGE }

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_DOWNLOAD_TREE_URI = "download_tree_uri"
        private const val KEY_DOWNLOAD_FORMAT = "download_format"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_AUTO_PLAY_RELATED = "auto_play_related"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
    }

    // ── Default path ────────────────────────────────────────────
    private val defaultPath: String
        get() = File(context.getExternalFilesDir(null), "music").absolutePath

    // ── Download Tree URI (SAF) ─────────────────────────────────
    fun getDownloadTreeUri(): Uri? {
        val uriString = prefs.getString(KEY_DOWNLOAD_TREE_URI, null) ?: return null
        return Uri.parse(uriString)
    }

    fun setDownloadTreeUri(uri: Uri?) {
        prefs.edit().putString(KEY_DOWNLOAD_TREE_URI, uri?.toString()).apply()
    }

    // ── Download path (legacy, for display) ─────────────────────
    fun getDownloadPath(): String {
        return prefs.getString(KEY_DOWNLOAD_PATH, defaultPath) ?: defaultPath
    }

    fun setDownloadPath(path: String) {
        prefs.edit().putString(KEY_DOWNLOAD_PATH, path).apply()
    }

    // ── Download format ─────────────────────────────────────────
    fun getDownloadFormat(): DownloadFormat {
        val name = prefs.getString(KEY_DOWNLOAD_FORMAT, DownloadFormat.M4A.name) ?: DownloadFormat.M4A.name
        return try { DownloadFormat.valueOf(name) } catch (_: Exception) { DownloadFormat.M4A }
    }

    fun setDownloadFormat(format: DownloadFormat) {
        prefs.edit().putString(KEY_DOWNLOAD_FORMAT, format.name).apply()
    }

    // ── Skip silence ────────────────────────────────────────────
    fun getSkipSilence(): Boolean = prefs.getBoolean(KEY_SKIP_SILENCE, false)

    fun setSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    // ── Auto-play related songs ─────────────────────────────────
    fun getAutoPlayRelated(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY_RELATED, true)

    fun setAutoPlayRelated(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_RELATED, enabled).apply()
    }

    // ── Theme mode ──────────────────────────────────────────────
    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.DARK }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    // ── Accent color ────────────────────────────────────────────
    fun getAccentColor(): AccentColor {
        val name = prefs.getString(KEY_ACCENT_COLOR, AccentColor.RED.name) ?: AccentColor.RED.name
        return try { AccentColor.valueOf(name) } catch (_: Exception) { AccentColor.RED }
    }

    fun setAccentColor(color: AccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color.name).apply()
    }
}
