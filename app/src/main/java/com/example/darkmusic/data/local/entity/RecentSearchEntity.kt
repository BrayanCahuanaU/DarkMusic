package com.example.darkmusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val mediaUrl: String,
    val timestamp: Long
)
