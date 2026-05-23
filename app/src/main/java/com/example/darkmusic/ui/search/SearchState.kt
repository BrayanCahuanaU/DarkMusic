package com.example.darkmusic.ui.search

import com.example.darkmusic.domain.model.Song

data class SearchState(
    val query: String = "",
    val searchResults: List<Song> = emptyList(),
    val downloadingSongIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)
