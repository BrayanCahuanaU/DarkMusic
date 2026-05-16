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
            val streamUrl = song.localPath ?: repository.getStreamUrl(song.id)
            if (streamUrl != null) {
                musicServiceConnection.playSong(song, streamUrl)
                // Lógica de auto-cola: Si no hay más canciones, añadimos 2 similares
                ensureQueueIsNotEmpty(song)
            }
        }
    }

    private fun ensureQueueIsNotEmpty(song: Song) {
        viewModelScope.launch {
            val player = musicServiceConnection.player.value
            if (player != null && player.mediaItemCount <= 1) {
                // Buscamos canciones del mismo artista para la cola
                val similarSongs = repository.searchSongs(song.artist).take(2)
                musicServiceConnection.addSongsToQueue(similarSongs, repository)
            }
        }
    }
}
