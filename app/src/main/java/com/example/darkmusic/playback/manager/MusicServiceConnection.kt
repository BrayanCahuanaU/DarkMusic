package com.example.darkmusic.playback.manager

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.playback.service.MusicService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicServiceConnection @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: com.example.darkmusic.domain.repository.MusicRepository
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player = _player.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private var currentQueue: List<Song> = emptyList()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                _player.value = controller
                setupPlayerListener(controller)
            } catch (e: Exception) {
                Log.e("MusicServiceConnection", "Error initializing MediaController", e)
            }
        }, MoreExecutors.directExecutor())

        updatePlaybackPosition()
    }

    private fun setupPlayerListener(player: Player?) {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val song = currentQueue.find { it.id == item.mediaId }
                    _currentSong.value = song
                    
                    // Si el item no tiene URI (porque es el siguiente en la cola), se la buscamos ahora
                    if (item.localConfiguration?.uri == null && song != null) {
                        fetchAndSetUri(song, player)
                    }
                }
                _duration.value = player.duration.coerceAtLeast(0L)
                _currentPosition.value = 0L
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicServiceConnection", "Player Error: ${error.errorCodeName} (${error.errorCode})", error)
            }
        })
    }

        private fun fetchAndSetUri(song: Song, player: Player) {
        scope.launch {
            val streamUrl = if (song.isDownloaded && song.localPath != null) {
                val file = java.io.File(song.localPath)
                if (file.exists()) {
                    android.net.Uri.fromFile(file).toString()
                } else {
                    null
                }
            } else {
                repository.getStreamUrl(extractVideoId(song.id))
            }

            streamUrl?.let { uri ->
                // Encontrar el índice del elemento de medios con el ID de la canción coincidente
                val index = (0 until player.mediaItemCount).firstOrNull { i ->
                    player.getMediaItemAt(i).mediaId == song.id
                }

                index?.let { mediaIndex ->
                    val currentItem = player.getMediaItemAt(mediaIndex)
                    val updatedItem = currentItem.buildUpon()
                        .setUri(android.net.Uri.parse(uri))
                        .build()

                    player.replaceMediaItem(mediaIndex, updatedItem)
                }
            }
        }
    }

    private fun updatePlaybackPosition() {
        scope.launch {
            while (true) {
                _player.value?.let {
                    if (it.isPlaying) {
                        _currentPosition.value = it.currentPosition
                    }
                }
                delay(1000L)
            }
        }
    }

    /**
     * Reproduce una canción y configura la lista completa como cola.
     */
    fun playSong(selectedSong: Song, songs: List<Song>) {
        scope.launch {
            currentQueue = songs
            _currentSong.value = selectedSong

            val player = _player.value
            if (player == null) {
                Log.e("MusicServiceConnection", "Player is null")
                currentQueue = emptyList()
                _currentSong.value = null
                return@launch
            }

            // Obtener la URL de stream para la canción seleccionada primero
            val selectedSongStreamUrl = if (selectedSong.isDownloaded && selectedSong.localPath != null) {
                android.net.Uri.fromFile(java.io.File(selectedSong.localPath)).toString()
            } else {
                repository.getStreamUrl(extractVideoId(selectedSong.id))
            }

            if (selectedSongStreamUrl == null) {
                Log.e("MusicServiceConnection", "Could not get stream URL for selected song: ${selectedSong.id}")
                currentQueue = emptyList()
                _currentSong.value = null
                return@launch
            }

            // Crear los elementos de medios
            val mediaItems = songs.map { song ->
                val mediaMetadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)

                // Establecer la URI de portada solo si está disponible y no está vacía
                song.coverUrl?.takeIf { it.isNotEmpty() }?.let { coverUri ->
                    mediaMetadataBuilder.setArtworkUri(android.net.Uri.parse(coverUri))
                }

                val mediaMetadata = mediaMetadataBuilder.build()

                val mediaItemBuilder = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setMediaMetadata(mediaMetadata)

                // Establecer la URI de medios solo para la canción seleccionada
                if (song.id == selectedSong.id) {
                    mediaItemBuilder.setUri(selectedSongStreamUrl)
                }

                mediaItemBuilder.build()
            }

            // Encontrar el índice de la canción seleccionada
            val index = songs.indexOfFirst { it.id == selectedSong.id }.coerceAtLeast(0)
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playPause() {
        _player.value?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        _player.value?.seekTo(position)
    }

    fun skipToNext() {
        _player.value?.seekToNext()
    }

    fun skipToPrevious() {
        _player.value?.seekToPrevious()
    }

    fun release() {
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    private fun extractVideoId(url: String): String {
        return Uri.parse(url).getQueryParameter("v") ?: url
    }

}
