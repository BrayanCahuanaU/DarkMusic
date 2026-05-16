package com.example.darkmusic.domain.repository

import com.example.darkmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio que define las operaciones de música.
 * Incluye búsqueda remota y obtención de tendencias.
 */
interface MusicRepository {
    // Local (Room)
    fun getAllSongs(): Flow<List<Song>>
    fun getFavoriteSongs(): Flow<List<Song>>
    suspend fun insertSong(song: Song)
    suspend fun deleteSong(songId: String)
    suspend fun getSongById(songId: String): Song?

    // Remote (NewPipe)
    suspend fun searchSongs(query: String): List<Song>
    suspend fun getTrendingSongs(): List<Song>
    suspend fun getStreamUrl(videoId: String): String?
    suspend fun downloadSong(song: Song): Boolean
}
