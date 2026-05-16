package com.example.darkmusic.data.repository

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
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao
) : MusicRepository {

    private val youtube = ServiceList.YouTube

    override fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs().map { it.map { e -> e.toDomain() } }
    override fun getFavoriteSongs(): Flow<List<Song>> = songDao.getFavoriteSongs().map { it.map { e -> e.toDomain() } }
    override suspend fun insertSong(song: Song) = songDao.insertSong(song.toEntity())
    override suspend fun deleteSong(songId: String) = songDao.deleteSong(songId)
    override suspend fun getSongById(songId: String): Song? = songDao.getSongById(songId)?.toDomain()

    override suspend fun getTrendingSongs(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val kiosk = youtube.kioskList.getExtractorById("Trending", null)
            kiosk.fetchPage()
            val items = kiosk.initialPage.items
            mapInfoItems(items)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val search = youtube.getSearchExtractor(query)
            search.fetchPage()
            val items = search.initialPage.items
            mapInfoItems(items)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val extractor = youtube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val audioStream = extractor.audioStreams
                .filter { it.format?.name == "webm" || it.format?.name == "m4a" }
                .maxByOrNull { it.bitrate }
            audioStream?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mapInfoItems(items: List<InfoItem>): List<Song> {
        // YouTube puede devolver VideoInfoItem o StreamInfoItem.
        // Ambos contienen la información que necesitamos.
        return items.filterIsInstance<StreamInfoItem>().map { item ->
            Song(
                id = item.url.substringAfter("v=", item.url),
                title = item.name,
                artist = item.uploaderName ?: "Desconocido",
                album = null,
                durationMs = item.duration * 1000L,
                coverUrl = item.thumbnails.firstOrNull()?.url,
                mediaUrl = item.url
            )
        }
    }
}
