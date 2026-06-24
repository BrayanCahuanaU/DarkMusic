package com.example.darkmusic.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.ImageLoader
import com.example.darkmusic.core.preferences.AccentColor
import com.example.darkmusic.core.preferences.DownloadFormat
import com.example.darkmusic.core.preferences.SettingsManager
import com.example.darkmusic.core.preferences.ThemeMode
import com.example.darkmusic.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val downloadPath: String = "",
    val downloadTreeUri: Uri? = null,
    val downloadFormat: DownloadFormat = DownloadFormat.M4A,
    val skipSilence: Boolean = false,
    val autoPlayRelated: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.RED,
    val storageStatus: String = "",
    val cacheSize: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val repository: MusicRepository
) : ViewModel() {

    var state by mutableStateOf(SettingsUiState())
        private set

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val downloadPath = settingsManager.getDownloadPath()
        val treeUri = settingsManager.getDownloadTreeUri()
        val displayPath = if (treeUri != null) {
            extractDisplayPath(treeUri) ?: downloadPath
        } else {
            downloadPath
        }

        state = state.copy(
            downloadPath = displayPath,
            downloadTreeUri = treeUri,
            downloadFormat = settingsManager.getDownloadFormat(),
            skipSilence = settingsManager.getSkipSilence(),
            autoPlayRelated = settingsManager.getAutoPlayRelated(),
            themeMode = settingsManager.getThemeMode(),
            accentColor = settingsManager.getAccentColor(),
            storageStatus = getStorageStatus(),
            cacheSize = getCacheSize()
        )
    }

    fun onFolderPicked(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }

        settingsManager.setDownloadTreeUri(uri)
        val displayPath = extractDisplayPath(uri) ?: uri.toString()
        settingsManager.setDownloadPath(displayPath)
        state = state.copy(
            downloadPath = displayPath,
            downloadTreeUri = uri
        )
    }

    fun onDownloadFormatChanged(format: DownloadFormat) {
        settingsManager.setDownloadFormat(format)
        state = state.copy(downloadFormat = format)
    }

    fun onSkipSilenceChanged(enabled: Boolean) {
        settingsManager.setSkipSilence(enabled)
        state = state.copy(skipSilence = enabled)
    }

    fun onAutoPlayRelatedChanged(enabled: Boolean) {
        settingsManager.setAutoPlayRelated(enabled)
        state = state.copy(autoPlayRelated = enabled)
    }

    fun onThemeModeChanged(mode: ThemeMode) {
        settingsManager.setThemeMode(mode)
        state = state.copy(themeMode = mode)
    }

    fun onAccentColorChanged(color: AccentColor) {
        settingsManager.setAccentColor(color)
        state = state.copy(accentColor = color)
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Coil.imageLoader(context).diskCache?.clear()
                Coil.imageLoader(context).memoryCache?.clear()
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            state = state.copy(cacheSize = getCacheSize())
        }
    }

    private fun extractDisplayPath(uri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            val base = if (parts[0] == "primary") {
                android.os.Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/${parts[0]}"
            }
            if (parts.size > 1 && parts[1].isNotEmpty()) "$base/${parts[1]}" else base
        } catch (_: Exception) { null }
    }

    private fun getStorageStatus(): String {
        return when (android.os.Environment.getExternalStorageState()) {
            android.os.Environment.MEDIA_MOUNTED -> "Acceso concedido"
            android.os.Environment.MEDIA_MOUNTED_READ_ONLY -> "Solo lectura"
            else -> "Sin acceso"
        }
    }

    private fun getCacheSize(): String {
        val cacheDir = context.cacheDir
        val sizeBytes = getDirSize(cacheDir)
        return formatSize(sizeBytes)
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } else {
            size = dir.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}
