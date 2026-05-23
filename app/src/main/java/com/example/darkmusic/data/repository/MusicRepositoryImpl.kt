package com.example.darkmusic.data.repository

import android.util.Log
import com.example.darkmusic.core.network.MusicDownloader
import com.example.darkmusic.data.local.dao.SongDao
import com.example.darkmusic.data.mapper.toDomain
import com.example.darkmusic.data.mapper.toEntity
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val musicDownloader: MusicDownloader
) : MusicRepository {

    private val youtube = ServiceList.YouTube

    override fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs().map { it.map { e -> e.toDomain() } }
    override fun getFavoriteSongs(): Flow<List<Song>> = songDao.getFavoriteSongs().map { it.map { e -> e.toDomain() } }
    override suspend fun insertSong(song: Song) = songDao.insertSong(song.toEntity())
    override suspend fun deleteSong(songId: String) = songDao.deleteSong(songId)
    override suspend fun getSongById(songId: String): Song? = songDao.getSongById(songId)?.toDomain()

    override suspend fun getTrendingSongs(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val kioskList = youtube.kioskList
            val availableKiosks = kioskList.availableKiosks
            Log.d("MusicRepository", "Available Kiosks: $availableKiosks")

            // Intentamos buscar un kiosk de música, si no, el primero disponible, si no, "Trending"
            // Algunos IDs comunes: "Music", "MUSIC", "Trending", "trending"
            val kioskId = availableKiosks.firstOrNull { it.equals("Music", ignoreCase = true) }
                ?: availableKiosks.firstOrNull { it.equals("Trending", ignoreCase = true) }
                ?: availableKiosks.firstOrNull()
                ?: "Trending"
            
            Log.d("MusicRepository", "Using Kiosk ID: $kioskId")

            val kioskExtractor = kioskList.getExtractorById(kioskId, null)
            val kioskInfo = KioskInfo.getInfo(kioskExtractor)
            val songs = mapInfoItems(kioskInfo.relatedItems)
            Log.d("MusicRepository", "Mapped ${songs.size} songs from kiosk")
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error loading trending songs, falling back to search", e)
            // Fallback: Si las tendencias fallan, buscamos "Top hits" para no dejar la pantalla vacía
            searchSongs("Top hits")
        }
    }

    override suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {

        try {

            Log.d("MusicRepository", "Searching: $query")

            val extractor = youtube.getSearchExtractor(query)

            extractor.fetchPage()

            val page = extractor.initialPage

            Log.d(
                "MusicRepository",
                "Initial page items: ${page.items.size}"
            )

            mapInfoItems(page.items)

        } catch (e: Exception) {

            Log.e("MusicRepository", "Search error", e)

            emptyList()
        }
    }

    override suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            // NewPipe espera una URL completa. Manejamos IDs, rutas relativas y URLs completas.
            val url = when {
                videoId.startsWith("http") -> videoId
                videoId.startsWith("/") -> "https://www.youtube.com$videoId"
                videoId.contains("watch?v=") -> "https://www.youtube.com/$videoId"
                else -> "https://www.youtube.com/watch?v=$videoId"
            }
            
            Log.d("MusicRepository", "Extrayendo stream de: $url")
            val streamExtractor = youtube.getStreamExtractor(url)
            streamExtractor.fetchPage() // Importante: Algunos extractores necesitan fetchPage
            val streamInfo = StreamInfo.getInfo(streamExtractor)
            
            val audioStream = streamInfo.audioStreams
                .filter { stream ->
                    stream.format?.name?.contains("webm", true) == true
                }
                .maxByOrNull { it.averageBitrate ?: it.bitrate }
                ?: streamInfo.audioStreams.maxByOrNull {
                    it.averageBitrate ?: it.bitrate
                }
            
            Log.d("MusicRepository", "Stream URL obtenida: ${audioStream?.url != null}")
            audioStream?.url
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error obteniendo stream URL para $videoId", e)
            null
        }
    }

    override suspend fun downloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val streamUrl = getStreamUrl(song.id) ?: return@withContext false
            val localPath = musicDownloader.downloadSong(song, streamUrl)
            if (localPath != null) {
                val updatedSong = song.copy(
                    isDownloaded = true,
                    localPath = localPath
                )
                insertSong(updatedSong)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun mapInfoItems(items: List<InfoItem>): List<Song> {
        Log.d("MusicRepository", "Received ${items.size} items to map")
        // Log types for debugging
        items.take(5).forEach { 
            Log.d("MusicRepository", "Item type: ${it.javaClass.simpleName}, Name: ${it.name}")
        }

        return items.filterIsInstance<StreamInfoItem>().map { item ->
            // Buscamos la mejor calidad de miniatura disponible
            val bestThumbnail = item.thumbnails
                .maxByOrNull { it.width * it.height }
                ?.url

            Song(
                id = item.url,
                title = item.name,
                artist = item.uploaderName ?: "Desconocido",
                album = null,
                durationMs = item.duration * 1000L,
                coverUrl = bestThumbnail,
                mediaUrl = item.url
            )
        }
    }
}
