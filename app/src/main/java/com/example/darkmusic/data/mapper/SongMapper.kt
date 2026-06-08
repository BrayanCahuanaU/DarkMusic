package com.example.darkmusic.data.mapper

import com.example.darkmusic.data.local.entity.RecentSearchEntity
import com.example.darkmusic.data.local.entity.SongEntity
import com.example.darkmusic.domain.model.Song

fun SongEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
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
        genre = genre,
        durationMs = durationMs,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded,
        localPath = localPath
    )
}

fun Song.toRecentSearchEntity(timestamp: Long): RecentSearchEntity {
    return RecentSearchEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        timestamp = timestamp
    )
}

fun RecentSearchEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        isFavorite = false, // Esto se actualizará si está en la DB local de canciones
        isDownloaded = false,
        localPath = null
    )
}
