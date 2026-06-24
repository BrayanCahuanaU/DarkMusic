package com.example.darkmusic.core.network

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.darkmusic.core.preferences.DownloadFormat
import com.example.darkmusic.core.preferences.SettingsManager
import com.example.darkmusic.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
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

                    val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val format = settingsManager.getDownloadFormat()
                val extension = when (format) {
                    DownloadFormat.WEBM -> ".webm"
                    DownloadFormat.MP3 -> ".mp3"
                    DownloadFormat.M4A -> ".m4a"
                }
                val fileName = "[$safeArtist] - $safeTitle$extension"

                val body = response.body ?: return@withContext null

                val treeUri = settingsManager.getDownloadTreeUri()
                if (treeUri != null) {
                    saveViaSaf(treeUri, fileName, body) ?: saveViaFile(fileName, body)
                } else {
                    saveViaFile(fileName, body)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicDownloader", "Error durante la descarga", e)
            null
        }
    }

    private fun saveViaSaf(treeUri: Uri, fileName: String, body: ResponseBody): String? {
        return try {
            val mimeType = when {
                fileName.endsWith(".webm") -> "audio/webm"
                fileName.endsWith(".mp3") -> "audio/mpeg"
                else -> "audio/mp4"
            }
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                treeUri,
                mimeType,
                fileName
            ) ?: return null

            context.contentResolver.openOutputStream(docUri)?.use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            Log.d("MusicDownloader", "Descarga completada via SAF: $docUri")
            docUri.toString()
        } catch (e: Exception) {
            Log.e("MusicDownloader", "Error guardando via SAF", e)
            null
        }
    }

    private fun saveViaFile(fileName: String, body: ResponseBody): String? {
        return try {
            val musicDir = File(settingsManager.getDownloadPath())
            if (!musicDir.exists()) musicDir.mkdirs()
            val file = File(musicDir, fileName)
            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            Log.d("MusicDownloader", "Descarga completada via File: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MusicDownloader", "Error guardando via File", e)
            null
        }
    }
}
