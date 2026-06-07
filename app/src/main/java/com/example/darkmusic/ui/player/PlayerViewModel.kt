package com.example.darkmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel que gestiona el estado y las acciones del reproductor de música.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection,
    private val repository: MusicRepository
) : ViewModel() {

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

    /** Marca o desmarca una canción como favorita en la base de datos local. */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val updatedSong = song.copy(isFavorite = !song.isFavorite)
            repository.insertSong(updatedSong)
        }
    }

    /** Solicita la descarga de una canción para escucharla sin conexión. */
    fun downloadSong(song: Song) {
        viewModelScope.launch {
            repository.downloadSong(song)
        }
    }
}
