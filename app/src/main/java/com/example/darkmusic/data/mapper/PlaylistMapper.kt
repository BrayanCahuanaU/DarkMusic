package com.example.darkmusic.data.mapper

import com.example.darkmusic.data.local.entity.PlaylistEntity
import com.example.darkmusic.data.local.entity.PlaylistWithSongs
import com.example.darkmusic.domain.model.Playlist

fun PlaylistEntity.toDomain(): Playlist {
    return Playlist(
        id = id,
        name = name,
        createdAt = createdAt,
        songs = emptyList()
    )
}

fun PlaylistWithSongs.toDomain(): Playlist {
    return Playlist(
        id = playlist.id,
        name = playlist.name,
        createdAt = playlist.createdAt,
        songs = songs.map { it.toDomain() }
    )
}

fun Playlist.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        name = name,
        createdAt = createdAt
    )
}
