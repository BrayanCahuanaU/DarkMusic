package com.example.darkmusic.core.network

import android.content.Context
import android.util.Log
import com.example.darkmusic.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.darkmusic.core.preferences.SettingsManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MusicDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settingsManager: SettingsManager
) {
    suspend fun downloadSong(song: Song, url: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("MusicDownloader", "Iniciando descarga de: ${song.title} desde $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("MusicDownloader", "Fallo en descarga: Código ${response.code}")
                    return@withContext null
                }
                
                val musicDir = File(settingsManager.getDownloadPath())
                if (!musicDir.exists()) musicDir.mkdirs()
                
                // Sanitizar nombre de archivo
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val extension = if (url.contains("webm")) ".webm" else ".m4a"
                
                val fileName = "[$safeArtist] - $safeTitle$extension"
                val file = File(musicDir, fileName)
                
                Log.d("MusicDownloader", "Guardando en: ${file.absolutePath}")
                
                val inputStream = response.body?.byteStream() ?: return@withContext null
                val outputStream = FileOutputStream(file)
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                Log.d("MusicDownloader", "Descarga completada: ${file.length()} bytes")
                file.absolutePath
            }
        } catch (e: Exception) {
            Log.e("MusicDownloader", "Error durante la descarga", e)
            null
        }
    }
}
