package com.example.darkmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        _searchQuery
            .debounce(500L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank()) {
                    performSearch(query)
                } else {
                    _state.update { it.copy(searchResults = emptyList(), isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        _state.update { it.copy(query = newQuery) }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                println("Buscando: $query")

                val results = repository.searchSongs(query)

                println("Resultados encontrados: ${results.size}")
                println(results)

                _state.update {
                    it.copy(
                        searchResults = results,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()

                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al buscar canciones"
                    )
                }
            }
        }
    }

    fun onSongClick(song: Song) {
        viewModelScope.launch {
            musicServiceConnection.playSong(song, _state.value.searchResults)
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
