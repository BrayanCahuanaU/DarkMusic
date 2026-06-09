package com.example.darkmusic.ui.library

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

data class LibraryState(
    val favoriteSongs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloadingSongIds: Set<String> = emptySet(), // Rastrear qué canciones se están descargando
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

// Eliminamos AlbumItem ya que unificamos con Playlist

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    musicServiceConnection: MusicServiceConnection
) : BaseMusicViewModel(repository, musicServiceConnection) {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            combine(
                repository.getFavoriteSongs(),
                repository.getAllSongs(),
                playlistRepository.getPlaylists()
            ) { favorites, allSongs, playlists ->
                _state.value.copy(
                    favoriteSongs = favorites,
                    allSongs = allSongs,
                    playlists = playlists,
                    isLoading = false
                )
            }.catch { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }.collect { newState ->
                _state.update { it.copy(
                    favoriteSongs = newState.favoriteSongs,
                    allSongs = newState.allSongs,
                    playlists = newState.playlists,
                    isLoading = false
                ) }
            }
        }
    }

    override fun onSongClick(song: Song, queue: List<Song>) {
        super.onSongClick(song, _state.value.allSongs)
    }

    override fun downloadSong(song: Song, onDownloadStatusChange: (Boolean) -> Unit) {
        super.downloadSong(song) { isDownloading ->
            if (isDownloading) {
                _state.update { it.copy(downloadingSongIds = it.downloadingSongIds + song.id) }
            } else {
                _state.update { it.copy(downloadingSongIds = it.downloadingSongIds - song.id) }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }

    fun addToAlbum(song: Song) {
        // Redirigimos a addToPlaylist ya que son lo mismo
        // En el UI, esto abrirá el mismo diálogo
    }
    fun signInWithSupabase() { /* TODO */ }
}
