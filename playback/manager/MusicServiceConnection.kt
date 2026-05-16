package com.example.darkmusic.playback.manager

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
    @ApplicationContext context: Context
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _player.value = controller
            setupPlayerListener(controller)
        }, MoreExecutors.directExecutor())

        updatePlaybackPosition()
    }

    private fun setupPlayerListener(player: Player?) {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _duration.value = player.duration.coerceAtLeast(0L)
                // Aquí podrías actualizar _currentSong si tienes un mapa de MediaItems a Songs
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    private fun updatePlaybackPosition() {
        scope.launch {
            while (true) {
                _player.value?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(1000L)
            }
        }
    }

    fun playSong(song: Song, streamUrl: String) {
        _currentSong.value = song
        _player.value?.let { p ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(android.net.Uri.parse(song.coverUrl ?: ""))
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                // Priorizamos el path local si ya está descargada
                .setUri(song.localPath ?: streamUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
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

    fun addSongsToQueue(songs: List<Song>, repository: com.example.darkmusic.domain.repository.MusicRepository) {
        scope.launch {
            songs.forEach { song ->
                val url = song.localPath ?: repository.getStreamUrl(song.id)
                if (url != null) {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(android.net.Uri.parse(song.coverUrl ?: ""))
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(song.id)
                        .setUri(url)
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    _player.value?.addMediaItem(mediaItem)
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
