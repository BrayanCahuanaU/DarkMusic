package com.example.darkmusic.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Playlist
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.domain.repository.PlaylistRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import com.example.darkmusic.ui.common.BaseMusicViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    repository: MusicRepository,
    musicServiceConnection: MusicServiceConnection,
    savedStateHandle: SavedStateHandle
) : BaseMusicViewModel(repository, musicServiceConnection) {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])
    
    private val _state = MutableStateFlow(PlaylistDetailState())
    val state: StateFlow<PlaylistDetailState> = _state.asStateFlow()

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            playlistRepository.getPlaylistById(playlistId)
                .onEach { playlist ->
                    _state.update { it.copy(playlist = playlist, isLoading = false) }
                }
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
                .launchIn(viewModelScope)
        }
    }

    fun playAll() {
        state.value.playlist?.songs?.let { songs ->
            if (songs.isNotEmpty()) {
                onSongClick(songs.first(), songs)
            }
        }
    }

    fun shuffle() {
        state.value.playlist?.songs?.let { songs ->
            if (songs.isNotEmpty()) {
                onSongClick(songs.shuffled().first(), songs.shuffled())
            }
        }
    }

    fun removeSongFromPlaylist(songId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            state.value.playlist?.let {
                playlistRepository.deletePlaylist(it)
            }
        }
    }

    fun addPlaylistToQueue() {
        state.value.playlist?.songs?.let { songs ->
            viewModelScope.launch {
                musicServiceConnection.addSongsToQueue(songs)
            }
        }
    }

    fun addSongToPlaylist(song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }
}
