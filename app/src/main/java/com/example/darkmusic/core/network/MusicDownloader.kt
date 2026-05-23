package com.example.darkmusic.core.network

import android.content.Context
import com.example.darkmusic.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val client: OkHttpClient
) {
    suspend fun downloadSong(song: Song, url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val musicDir = File(context.getExternalFilesDir(null), "music")
                if (!musicDir.exists()) musicDir.mkdirs()
                
                // Usamos el formato: "[Artista] Album - Titulo.m4a"
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeAlbum = (song.album ?: "Música local").replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                
                val fileName = "[$safeArtist] $safeAlbum - $safeTitle.m4a"
                val file = File(musicDir, fileName)
                
                val inputStream = response.body?.byteStream() ?: return@withContext null
                val outputStream = FileOutputStream(file)
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
