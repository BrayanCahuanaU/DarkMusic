package com.example.darkmusic.domain.recommendation

import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class RecommendationEngineTest {

    @Mock
    lateinit var repository: MusicRepository

    private lateinit var engine: RecommendationEngine

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        engine = RecommendationEngine(repository)
    }

    @Test
    fun `getRecommendations filters out existing songs and session recommendations`() = runTest {
        val lastSong = createSong("1", "Artist A")
        val alreadyInQueue = createSong("2", "Artist B")
        val relatedSongs = listOf(
            createSong("2", "Artist B"), // In queue
            createSong("3", "Artist C"), // New
            createSong("4", "Artist D")  // New
        )

        `when`(repository.getRelatedSongs("1")).thenReturn(relatedSongs)

        // First call
        val recommendations1 = engine.getRecommendations(listOf(lastSong), listOf(lastSong, alreadyInQueue))
        
        assertEquals(2, recommendations1.size)
        assertTrue(recommendations1.any { it.id == "3" })
        assertTrue(recommendations1.any { it.id == "4" })

        // Second call with same related results - should be empty because 3 and 4 were already recommended
        val recommendations2 = engine.getRecommendations(listOf(lastSong), listOf(lastSong, alreadyInQueue))
        assertTrue(recommendations2.isEmpty())
    }

    @Test
    fun `getRecommendations prioritizes same artist`() = runTest {
        val lastSong = createSong("1", "Artist A")
        val relatedSongs = listOf(
            createSong("2", "Artist B"),
            createSong("3", "Artist A"), // Same artist
            createSong("4", "Artist C")
        )

        `when`(repository.getRelatedSongs("1")).thenReturn(relatedSongs)

        // Since the engine uses Random for count (2-10), and we have 3 candidates, 
        // it might pick 2 or 3. In both cases, "3" (Artist A) should be prioritized by scoring
        // and thus more likely included if count < 3.
        // Actually scoring is only for ordering.
        val recommendations = engine.getRecommendations(listOf(lastSong), listOf(lastSong))
        
        assertTrue(recommendations.any { it.artist == "Artist A" })
    }

    private fun createSong(id: String, artist: String): Song {
        return Song(
            id = id,
            title = "Title $id",
            artist = artist,
            album = null,
            genre = null,
            durationMs = 0,
            coverUrl = null,
            mediaUrl = "url $id"
        )
    }
}
