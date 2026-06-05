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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection,
    private val repository: MusicRepository
) : ViewModel() {

    val currentSong = musicServiceConnection.currentSong
    val isPlaying = musicServiceConnection.isPlaying
    val currentPosition = musicServiceConnection.currentPosition
    val duration = musicServiceConnection.duration

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            musicServiceConnection.error.collect { err ->
                if (err != null) _error.value = err
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun playPause() {
        musicServiceConnection.playPause()
    }

    fun seekTo(position: Float) {
        musicServiceConnection.seekTo(position.toLong())
    }

    fun skipNext() {
        musicServiceConnection.skipToNext()
    }

    fun skipPrevious() {
        musicServiceConnection.skipToPrevious()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val updatedSong = song.copy(isFavorite = !song.isFavorite)
            repository.insertSong(updatedSong)
        }
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            repository.downloadSong(song)
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {

            _isLoading.value = true
            _error.value = null

            try {

                val streamUrl = if (song.isDownloaded && song.localPath != null) {
                    song.localPath
                } else {
                    repository.getStreamUrl(song.id)
                }

                if (streamUrl != null) {
                    musicServiceConnection.playSong(song, streamUrl)
                } else {
                    _error.value = "No se pudo obtener el stream"
                }

            } catch (e: Exception) {

                e.printStackTrace()
                _error.value = e.message ?: "Error al reproducir"

            } finally {

                _isLoading.value = false

            }
        }
    }
}