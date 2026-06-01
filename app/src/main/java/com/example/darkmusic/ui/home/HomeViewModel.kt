package com.example.darkmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

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

    /**
     * Lógica mejorada para reproducir una canción con cola dinámica.
     */
    fun onSongClick(song: Song) {
        viewModelScope.launch {
            // Buscamos en qué lista está la canción para configurar la cola
            val queue = when {
                _state.value.trendingSongs.any { it.id == song.id } -> _state.value.trendingSongs
                _state.value.favoriteGenresRecommendations.any { it.id == song.id } -> _state.value.favoriteGenresRecommendations
                _state.value.downloadedGenresRecommendations.any { it.id == song.id } -> _state.value.downloadedGenresRecommendations
                _state.value.recentSongs.any { it.id == song.id } -> _state.value.recentSongs
                else -> listOf(song)
            }
            
            musicServiceConnection.playSong(song, queue)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val fullInfo = if (song.genre == null) repository.getFullSongInfo(song.id) else song
            val updatedSong = (fullInfo ?: song).copy(isFavorite = !song.isFavorite)
            repository.insertSong(updatedSong)
        }
    }

    fun downloadSong(song: Song) {
        if (song.isDownloaded || _state.value.downloadingSongIds.contains(song.id)) return
        
        viewModelScope.launch {
            _state.update { it.copy(downloadingSongIds = it.downloadingSongIds + song.id) }
            try {
                val fullSong = if (song.genre == null) repository.getFullSongInfo(song.id) ?: song else song
                repository.downloadSong(fullSong)
            } finally {
                _state.update { it.copy(downloadingSongIds = it.downloadingSongIds - song.id) }
            }
        }
    }
}
