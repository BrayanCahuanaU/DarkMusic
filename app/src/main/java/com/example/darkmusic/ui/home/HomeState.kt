package com.example.darkmusic.ui.home

import com.example.darkmusic.domain.model.Song

/**
 * Estado de la Home dividido por secciones reales.
 */
data class HomeState(
    val trendingSongs: List<Song> = emptyList(),    // Top Mundial (Tendencias)
    val favoriteGenresRecommendations: List<Song> = emptyList(), // Recomendaciones por géneros favoritos
    val downloadedGenresRecommendations: List<Song> = emptyList(), // Recomendaciones por géneros descargados
    val recentSongs: List<Song> = emptyList(),    // Reproducciones recientes
    val downloadingSongIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

