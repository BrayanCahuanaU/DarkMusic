package com.example.darkmusic.ui.home

import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.domain.repository.PlaylistRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import com.example.darkmusic.ui.common.BaseMusicViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    musicServiceConnection: MusicServiceConnection
) : BaseMusicViewModel(repository, musicServiceConnection) {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    val playlists = playlistRepository.getPlaylists()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // 1. Cargamos tendencias inmediatamente
                val trending = repository.getTrendingSongs()
                _state.update { it.copy(trendingSongs = trending) }

                // 2. Escuchamos cambios en la BD local de forma reactiva
                repository.getAllSongs().collect { localSongs ->
                    val favorites = localSongs.filter { it.isFavorite }
                    val downloads = localSongs.filter { it.isDownloaded }

                    // Recomendaciones por favoritos (Top 4 géneros)
                    val favoriteGenres = favorites.mapNotNull { it.genre }
                        .groupBy { it }.mapValues { it.value.size }
                        .toList().sortedByDescending { it.second }.take(4).map { it.first }

                    val favoriteRecs = if (favoriteGenres.isNotEmpty()) {
                        favoriteGenres.map { async { repository.getSongsByGenre(it) } }
                            .awaitAll().flatten().shuffled().distinctBy { it.id }.take(10)
                    } else {
                        repository.getSongsByGenre("Pop Hits").take(10)
                    }

                    // Recomendaciones por descargas (Top 3 géneros)
                    val downloadGenres = downloads.mapNotNull { it.genre }
                        .groupBy { it }.mapValues { it.value.size }
                        .toList().sortedByDescending { it.second }.take(3).map { it.first }

                    val downloadRecs = if (downloadGenres.isNotEmpty()) {
                        downloadGenres.map { async { repository.getSongsByGenre(it) } }
                            .awaitAll().flatten().shuffled().distinctBy { it.id }.take(10)
                    } else {
                        repository.getSongsByGenre("Dance Mix").take(10)
                    }

                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            favoriteGenresRecommendations = favoriteRecs,
                            downloadedGenresRecommendations = downloadRecs,
                            recentSongs = localSongs.take(10),
                            error = null
                        )
                    }
                }

            } catch (e: Exception) {
                val localSongs = repository.getAllSongs().firstOrNull() ?: emptyList()
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        recentSongs = localSongs.take(10),
                        error = if (localSongs.isEmpty()) "No se pudo cargar la música. Verifica tu conexión." else null
                    ) 
                }
            }
        }
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
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
}
