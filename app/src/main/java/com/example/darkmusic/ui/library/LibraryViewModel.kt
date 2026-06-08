package com.example.darkmusic.ui.library

import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import com.example.darkmusic.ui.common.BaseMusicViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(
    val favoriteSongs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val downloadingSongIds: Set<String> = emptySet(), // Rastrear qué canciones se están descargando
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

data class AlbumItem(
    val name: String,
    val artist: String,
    val coverUrl: String?,
    val songCount: Int
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: MusicRepository,
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
                repository.getAllSongs()
            ) { favorites, allSongs ->
                val downloadedSongs = allSongs.filter { it.isDownloaded }
                
                val groupedAlbums = allSongs
                    .filter { it.album != null && it.album != "Offline" }
                    .groupBy { it.album }
                    .map { (albumName, songs) ->
                        AlbumItem(
                            name = albumName ?: "Unknown Album",
                            artist = songs.firstOrNull()?.artist ?: "Unknown Artist",
                            coverUrl = songs.firstOrNull { it.coverUrl != null }?.coverUrl,
                            songCount = songs.size
                        )
                    }.toMutableList()

                if (downloadedSongs.isNotEmpty()) {
                    groupedAlbums.add(0, AlbumItem(
                        name = "Offline",
                        artist = "Música local",
                        coverUrl = null,
                        songCount = downloadedSongs.size
                    ))
                }
                
                _state.value.copy(
                    favoriteSongs = favorites,
                    allSongs = allSongs,
                    albums = groupedAlbums,
                    isLoading = false
                )
            }.catch { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }.collect { newState ->
                _state.update { it.copy(
                    favoriteSongs = newState.favoriteSongs,
                    allSongs = newState.allSongs,
                    albums = newState.albums,
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

    fun addToPlaylist(song: Song) { /* TODO */ }
    fun addToAlbum(song: Song) { /* TODO */ }
    fun signInWithSupabase() { /* TODO */ }
}
