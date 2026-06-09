package com.example.darkmusic.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel base que centraliza la lógica común para interactuar con la música.
 * Evita la duplicación de métodos como onSongClick, toggleFavorite y downloadSong.
 */
abstract class BaseMusicViewModel(
    protected val repository: MusicRepository,
    protected val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    /**
     * Lógica unificada para reproducir una canción.
     * Resuelve automáticamente si se debe usar el archivo local o el stream remoto.
     */
    open fun onSongClick(song: Song, queue: List<Song> = emptyList()) {
        viewModelScope.launch {
            try {
                val streamUrl = resolveStreamUrl(song)
                if (streamUrl != null) {
                    val finalQueue = if (queue.isNotEmpty()) queue else listOf(song)
                    musicServiceConnection.playSong(song, streamUrl, finalQueue)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Resuelve la URL de reproducción, priorizando la descarga local si existe.
     */
    protected suspend fun resolveStreamUrl(song: Song): String? {
        return if (song.isDownloaded && song.localPath != null) {
            val file = File(song.localPath)
            if (file.exists()) "file://${song.localPath}" else repository.getStreamUrl(song.id)
        } else {
            repository.getStreamUrl(song.id)
        }
    }

    /**
     * Alterna el estado de favorito de una canción.
     */
    open fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            // Si la canción no tiene género, intentamos obtener info completa para persistir mejor
            val fullInfo = if (song.genre == null) repository.getFullSongInfo(song.id) else song
            val updatedSong = (fullInfo ?: song).copy(isFavorite = !song.isFavorite)
            repository.insertSong(updatedSong)
        }
    }

    /**
     * Agrega una canción al final de la cola actual.
     */
    open fun addToQueue(song: Song) {
        musicServiceConnection.addSongsToQueue(listOf(song))
    }

    /**
     * Descarga una canción para reproducción offline.
     */
    open fun downloadSong(song: Song, onDownloadStatusChange: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onDownloadStatusChange(true)
            try {
                // Asegurar info completa antes de descargar para tener metadatos correctos
                val fullSong = if (song.genre == null) repository.getFullSongInfo(song.id) ?: song else song
                repository.downloadSong(fullSong)
            } finally {
                onDownloadStatusChange(false)
            }
        }
    }
}
