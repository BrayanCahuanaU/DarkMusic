package com.example.darkmusic.ui.player

import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import com.example.darkmusic.ui.common.BaseMusicViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel que gestiona el estado y las acciones del reproductor de música.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    musicServiceConnection: MusicServiceConnection,
    repository: MusicRepository,
    private val playlistRepository: com.example.darkmusic.domain.repository.PlaylistRepository
) : BaseMusicViewModel(repository, musicServiceConnection) {

    /** Información de la canción que se está reproduciendo actualmente. */
    val currentSong = musicServiceConnection.currentSong
    
    /** Estado de reproducción (true si está sonando, false si está pausado). */
    val isPlaying = musicServiceConnection.isPlaying

    // Agrega esta línea:
    val isLoading = musicServiceConnection.isLoading
    
    /** Posición actual del progreso de la canción en milisegundos. */
    val currentPosition = musicServiceConnection.currentPosition
    
    /** Duración total de la canción actual en milisegundos. */
    val duration = musicServiceConnection.duration

    /** Lista actual de canciones en la cola. */
    val currentQueue = musicServiceConnection.currentQueue

    val playlists = playlistRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Flujo para exponer errores globales del servicio a la UI. */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        // Escucha y propaga errores desde el servicio de música
        musicServiceConnection.error
            .filterNotNull()
            .onEach { _error.value = it }
            .launchIn(viewModelScope)
    }

    /** Limpia el error actual para que deje de mostrarse en la interfaz. */
    fun clearError() {
        _error.value = null
    }

    /** Alterna el estado de reproducción entre Play y Pause. */
    fun playPause() {
        musicServiceConnection.playPause()
    }

    /** Cambia el punto de reproducción actual a la posición indicada. */
    fun seekTo(position: Float) {
        musicServiceConnection.seekTo(position.toLong())
    }

    /** Salta a la siguiente canción en la lista de reproducción. */
    fun skipNext() {
        musicServiceConnection.skipToNext()
    }

    /** Regresa a la canción anterior en la lista de reproducción. */
    fun skipPrevious() {
        musicServiceConnection.skipToPrevious()
    }

    /** Elimina una canción de la cola. */
    fun removeFromQueue(songId: String) {
        musicServiceConnection.removeSongsFromQueue(listOf(songId))
    }

    /** Mueve una canción de una posición a otra en la cola. */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        musicServiceConnection.moveSongInQueue(fromIndex, toIndex)
    }

    /** Agrega una canción al final de la cola. */
    override fun addToQueue(song: Song) {
        musicServiceConnection.addSongsToQueue(listOf(song))
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }
}
