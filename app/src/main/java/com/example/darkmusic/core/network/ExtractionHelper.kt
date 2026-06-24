package com.example.darkmusic.core.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject

/**
 * Helper sencillo para realizar búsquedas con NewPipeExtractor y obtener una URL de audio.
 * Se añade como dependencia inyectable para poder usarlo desde viewmodels o coroutines.
 */
class ExtractionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "ExtractionHelper"

    init {
        // NewPipe ya se inicializa en DarkMusicApp, pero por seguridad comprobamos
        try {
            if (NewPipe.getDownloader() == null) {
                Log.d(TAG, "NewPipe downloader no está inicializado")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error comprobando NewPipe: ${e.message}")
        }
    }

    suspend fun searchAndGetAudioUrl(query: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for: $query")
            val youtube = ServiceList.YouTube
            val searchExtractor = youtube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            val searchInfo = SearchInfo.getInfo(searchExtractor)
            val items = searchInfo.relatedItems
            if (items.isEmpty()) {
                Log.e(TAG, "No results for query: $query")
                return@withContext null
            }
            val firstItem = items[0]
            val videoUrl = firstItem.url
            Log.d(TAG, "Extracting streams for: $videoUrl")
            val streamInfo = StreamInfo.getInfo(youtube, videoUrl)

            val audioStreams = streamInfo.audioStreams
            val videoStreams = streamInfo.videoStreams

            if (audioStreams.isEmpty() && videoStreams.isEmpty()) {
                Log.e(TAG, "No streams found for: $videoUrl")
                return@withContext null
            }

            val streamUrl = if (audioStreams.isNotEmpty()) {
                // algunos builds exponen .url, otros .content; intentamos ambas
                audioStreams.firstOrNull()?.let { s -> (s.url ?: s.content) }
                    ?: audioStreams.first().url
            } else {
                Log.d(TAG, "Falling back to video stream")
                videoStreams.firstOrNull()?.let { s -> (s.url ?: s.content) } ?: videoStreams.first().url
            }

            Log.d(TAG, "Found stream: $streamUrl")
            return@withContext streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio url: ${e.message}", e)
            null
        }
    }
}

