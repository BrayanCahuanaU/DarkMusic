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

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private var currentQueue: List<Song> = emptyList()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

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

                    // Pre-cargar el siguiente item de la cola
                    val nextIndex = player.currentMediaItemIndex + 1
                    if (nextIndex < player.mediaItemCount) {
                        val nextItem = player.getMediaItemAt(nextIndex)
                        val nextSong = currentQueue.find { it.id == nextItem.mediaId }
                        if (nextItem.localConfiguration?.uri == null && nextSong != null) {
                            fetchAndSetUri(nextSong, player)
                        }
                    }
                }
                _duration.value = player.duration.coerceAtLeast(0L)
                _currentPosition.value = 0L
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isLoading.value = playbackState == Player.STATE_BUFFERING
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
    fun playSong(selectedSong: Song, streamUrl: String, queue: List<Song> = emptyList()) {
        scope.launch {

            _currentSong.value = selectedSong
            
            // Actualizar la cola actual para que onMediaItemTransition pueda encontrar las canciones
            currentQueue = if (queue.isNotEmpty()) queue else listOf(selectedSong)

            val player = _player.value
            if (player == null) {
                Log.e("MusicServiceConnection", "Player is null")
                return@launch
            }

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(selectedSong.title)
                .setArtist(selectedSong.artist)
                .apply {
                    selectedSong.coverUrl?.takeIf { it.isNotEmpty() }?.let {
                        setArtworkUri(Uri.parse(it))
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(selectedSong.id)
                .setUri(streamUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem)
            
            // Si hay cola, agregar los demás items
            if (queue.isNotEmpty()) {
                val queueItems = queue.filter { it.id != selectedSong.id }.map { song ->
                    MediaItem.Builder()
                        .setMediaId(song.id)
                        // Inicialmente sin URI; se cargarán cuando sea necesario en fetchAndSetUri
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .apply {
                                    song.coverUrl?.takeIf { it.isNotEmpty() }?.let {
                                        setArtworkUri(android.net.Uri.parse(it))
                                    }
                                }
                                .build()
                        )
                        .build()
                }
                player.addMediaItems(queueItems)
            }

            // Pre-cargar el segundo item de la cola si existe
            if (queue.size > 1) {
                val nextSong = queue.first { it.id != selectedSong.id }
                fetchAndSetUri(nextSong, player)
            }

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

    fun updateQueue(currentSong: Song, newQueue: List<Song>) {
        val player = _player.value ?: return
        if (player.currentMediaItem?.mediaId == currentSong.id) {
            currentQueue = newQueue
            // No interrumpir el item actual, solo agregar los demás
            val currentIndex = newQueue.indexOfFirst { it.id == currentSong.id }
            if (currentIndex < 0) return
            scope.launch {
                val items = newQueue.mapIndexed { i, song ->
                    MediaItem.Builder()
                        .setMediaId(song.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .apply {
                                    song.coverUrl?.takeIf { it.isNotEmpty() }
                                        ?.let { setArtworkUri(android.net.Uri.parse(it)) }
                                }
                                .build()
                        )
                        .build()
                }
                // Reemplazar todos excepto el actual que ya está reproduciéndose
                val playerCurrentIndex = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).mediaId == currentSong.id }
                    ?: return@launch
                // Agregar siguientes
                if (playerCurrentIndex + 1 < items.size) {
                    player.addMediaItems(playerCurrentIndex + 1, items.drop(currentIndex + 1))
                }
            }
        }
    }

}
