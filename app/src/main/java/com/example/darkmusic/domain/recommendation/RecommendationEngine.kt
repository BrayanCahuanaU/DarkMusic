package com.example.darkmusic.domain.recommendation

import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class RecommendationEngine @Inject constructor(
    private val repository: MusicRepository
) {
    private val recommendedInSession = mutableSetOf<String>()

    /**
     * Obtiene una lista de recomendaciones basada en las últimas canciones reproducidas.
     * @param lastSongs Las últimas canciones (hasta 2) que se usaron como base.
     * @param currentQueue La cola actual para evitar duplicados.
     * @return Una lista de 2 a 10 canciones recomendadas.
     */
    suspend fun getRecommendations(lastSongs: List<Song>, currentQueue: List<Song>): List<Song> {
        val candidates = mutableListOf<Song>()
        
        // Obtener canciones relacionadas para cada una de las últimas canciones
        for (song in lastSongs) {
            val related = repository.getRelatedSongs(song.id)
            candidates.addAll(related)
        }

        // Filtrar candidatos:
        // 1. No estar en la cola actual
        // 2. No haber sido recomendadas ya en esta sesión
        // 3. No ser ninguna de las canciones base
        val currentIds = currentQueue.map { it.id }.toSet()
        val baseIds = lastSongs.map { it.id }.toSet()
        
        val filteredCandidates = candidates.filter { candidate ->
            candidate.id !in currentIds && 
            candidate.id !in recommendedInSession &&
            candidate.id !in baseIds
        }.distinctBy { it.id }

        if (filteredCandidates.isEmpty()) return emptyList()

        // Puntuación simple: priorizar si coincide el artista con alguna de las canciones base
        val artists = lastSongs.map { it.artist }.toSet()
        val scoredCandidates = filteredCandidates.map { candidate ->
            var score = 0
            if (candidate.artist in artists) score += 2
            // Podríamos añadir más criterios si tuviéramos géneros precisos
            candidate to score
        }.sortedByDescending { it.second }

        // Seleccionar una cantidad aleatoria entre 2 y 10 (o el máximo disponible)
        val count = Random.nextInt(2, 11).coerceAtMost(scoredCandidates.size)
        val selected = scoredCandidates.take(count).map { it.first }
        
        // Registrar en la sesión para evitar repetición
        selected.forEach { recommendedInSession.add(it.id) }
        
        return selected
    }

    fun clearSession() {
        recommendedInSession.clear()
    }
}
