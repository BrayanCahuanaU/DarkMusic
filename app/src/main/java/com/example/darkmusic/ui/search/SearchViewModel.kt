package com.example.darkmusic.ui.search

import androidx.lifecycle.viewModelScope
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import com.example.darkmusic.domain.repository.PlaylistRepository
import com.example.darkmusic.playback.manager.MusicServiceConnection
import com.example.darkmusic.ui.common.BaseMusicViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    musicServiceConnection: MusicServiceConnection,
) : BaseMusicViewModel(repository, musicServiceConnection) {

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val playlists = playlistRepository.getPlaylists()

    init {
        // Collect search history
        repository.getRecentSearches()
            .onEach { history ->
                _state.update { it.copy(recentSearches = history) }
            }
            .launchIn(viewModelScope)

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

    override fun onSongClick(song: Song, queue: List<Song>) {
        viewModelScope.launch {
            // Add to search history
            repository.addSongToHistory(song)
            // Proceder con la reproducción usando la lógica base
            super.onSongClick(song, _state.value.searchResults)
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

    fun onRemoveRecentSearch(song: Song) {
        viewModelScope.launch {
            repository.removeSongFromHistory(song.id)
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }
}
