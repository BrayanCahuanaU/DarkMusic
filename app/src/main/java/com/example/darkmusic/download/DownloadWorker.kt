package com.example.darkmusic.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.darkmusic.domain.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Worker para descargar música en segundo plano de forma eficiente.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MusicRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString("song_id") ?: return Result.failure()
        val songTitle = inputData.getString("song_title") ?: "Unknown"

        return try {
            // 1. Obtenemos el URL del flujo nativo (.webm / .m4a)
            val streamUrl = repository.getStreamUrl(songId) ?: return Result.failure()

            // 2. Realizamos la descarga física del archivo
            val client = OkHttpClient()
            val request = Request.Builder().url(streamUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Result.failure()

            // 3. Guardamos en el almacenamiento interno privado de la app
            val file = File(applicationContext.filesDir, "$songId.webm")
            val outputStream = FileOutputStream(file)
            response.body?.byteStream()?.copyTo(outputStream)
            outputStream.close()

            // 4. Actualizamos la base de datos para marcar como descargado
            val song = repository.getSongById(songId)
            song?.let {
                repository.insertSong(it.copy(isDownloaded = true, localPath = file.absolutePath))
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
