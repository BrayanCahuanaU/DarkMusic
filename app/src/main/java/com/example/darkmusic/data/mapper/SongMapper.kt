package com.example.darkmusic.data.mapper

import com.example.darkmusic.data.local.entity.SongEntity
import com.example.darkmusic.domain.model.Song

fun SongEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded,
        localPath = localPath
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded,
        localPath = localPath
    )
}
