package com.example.darkmusic.ui.home

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
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        loadHomeData()
    }

    /**
     * Carga las tendencias (remoto) y las canciones locales de forma independiente.
     */
    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // 1. Obtenemos tendencias reales del repositorio
                val trending = repository.getTrendingSongs()

                // 2. Observamos las canciones locales sin bloquear el hilo principal
                // Usamos collect para reaccionar a cambios en tiempo real
                repository.getAllSongs().onEach { localSongs ->
                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            songs = trending,
                            recentSongs = localSongs.take(10),
                            suggestedSongs = if (trending.isNotEmpty()) trending.shuffled().take(5) else emptyList(),
                            error = null
                        )
                    }
                }.launchIn(this)

            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        error = "No se pudo cargar la música. Verifica tu conexión." 
                    ) 
                }
            }
        }
    }

    fun onSongClick(song: Song) {
        viewModelScope.launch {
            if (song.isDownloaded && song.localPath != null) {
                musicServiceConnection.playSong(song, song.localPath)
            } else {
                repository.getStreamUrl(song.id)?.let { url ->
                    musicServiceConnection.playSong(song, url)
                }
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.insertSong(song.copy(isFavorite = !song.isFavorite))
        }
    }

    fun downloadSong(song: Song) {
        if (song.isDownloaded || _state.value.downloadingSongIds.contains(song.id)) return
        
        viewModelScope.launch {
            _state.update { it.copy(downloadingSongIds = it.downloadingSongIds + song.id) }
            try {
                repository.downloadSong(song)
            } finally {
                _state.update { it.copy(downloadingSongIds = it.downloadingSongIds - song.id) }
            }
        }
    }
}
