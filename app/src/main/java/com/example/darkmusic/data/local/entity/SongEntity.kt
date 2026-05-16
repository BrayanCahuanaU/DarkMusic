package com.example.darkmusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val mediaUrl: String,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val localPath: String?
)
