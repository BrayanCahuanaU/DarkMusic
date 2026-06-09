package com.example.darkmusic.data.repository

import com.example.darkmusic.data.local.dao.PlaylistDao
import com.example.darkmusic.data.local.dao.SongDao
import com.example.darkmusic.data.local.entity.PlaylistEntity
import com.example.darkmusic.data.local.entity.PlaylistSongCrossRef
import com.example.darkmusic.data.mapper.toDomain
import com.example.darkmusic.data.mapper.toEntity
import com.example.darkmusic.domain.model.Playlist
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsWithSongs().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPlaylistById(id: Long): Flow<Playlist?> {
        return playlistDao.getPlaylistWithSongs(id).map { it.toDomain() }
    }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist.toEntity())
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.renamePlaylist(playlistId, newName)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) = withContext(Dispatchers.IO) {
        // Asegurarse de que la canción existe en la BD local primero
        songDao.insertSong(song.toEntity())
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }
}
