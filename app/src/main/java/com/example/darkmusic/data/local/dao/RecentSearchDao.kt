package com.example.darkmusic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.darkmusic.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(recentSearch: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE id = :songId")
    suspend fun deleteRecentSearch(songId: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearHistory()
}
