package com.example.darkmusic.ui.home

import com.example.darkmusic.domain.model.Song

/**
 * Estado de la Home dividido por secciones reales.
 */
data class HomeState(
    val songs: List<Song> = emptyList(),          // Top Mundial (Tendencias)
    val recentSongs: List<Song> = emptyList(),    // Reproducciones recientes
    val suggestedSongs: List<Song> = emptyList(), // Sugerencias para el usuario
    val isLoading: Boolean = false,
    val error: String? = null
)

