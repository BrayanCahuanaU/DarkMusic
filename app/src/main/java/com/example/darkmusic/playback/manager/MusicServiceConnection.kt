package com.example.darkmusic.playback.manager

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.playback.service.MusicService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.example.darkmusic.core.preferences.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class MusicServiceConnection @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: com.example.darkmusic.domain.repository.MusicRepository,
    private val recommendationEngine: com.example.darkmusic.domain.recommendation.RecommendationEngine,
    private val settingsManager: SettingsManager
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

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue = _currentQueue.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val MAX_DURATION_MS = 7 * 60 * 1000L // 7 minutos en ms

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Mapa para rastrear y esperar resoluciones de URIs en curso
    private val resolvingJobs = mutableMapOf<String, Deferred<Boolean>>()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _player.value = controller
                
                // Forzar modo repetición desactivado por defecto
                controller.repeatMode = Player.REPEAT_MODE_OFF
                
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
                Log.d("MusicServiceConnection", "Transición detectada. Razón: $reason, MediaId: ${mediaItem?.mediaId}")

                player.repeatMode = Player.REPEAT_MODE_OFF

                mediaItem?.let { item ->
                    val song = _currentQueue.value.find { it.id == item.mediaId }
                    _currentSong.value = song
                }
                _duration.value = player.duration.coerceAtLeast(0L)
                _currentPosition.value = 0L

                checkAndTriggerRecommendations(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isLoading.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    checkAndTriggerRecommendations(player)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicServiceConnection", "Player Error: ${error.errorCodeName} (${error.errorCode})", error)
                
                val player = _player.value ?: return
                val failedMediaId = player.currentMediaItem?.mediaId ?: return
                val song = _currentQueue.value.find { it.id == failedMediaId } ?: return
                
                // Intento de recuperación para errores de red o decodificador (URL expirada)
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || 
                    error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    
                    scope.launch {
                        _isLoading.value = true
                        val replaced = resolveAndReplaceMediaItem(song, player)
                        _isLoading.value = false
                        if (replaced) {
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
        })
    }

    private fun buildMediaItem(song: Song, uri: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .apply {
                        song.coverUrl?.takeIf { it.isNotEmpty() }?.let { setArtworkUri(it.toUri()) }
                    }
                    .build()
            )
            .build()
    }

    private suspend fun resolveUri(song: Song): String? {
        return withContext(Dispatchers.IO) {
            if (song.isDownloaded && song.localPath != null) {
                if (song.localPath!!.startsWith("content://")) {
                    song.localPath
                } else {
                    val file = java.io.File(song.localPath)
                    if (file.exists()) "file://${song.localPath}" else null
                }
            } else {
                repository.getStreamUrl(extractVideoId(song.id))
            }
        }
    }

    private suspend fun resolveAndReplaceMediaItem(song: Song, player: Player): Boolean {
        // Si ya hay una resolución en curso para esta canción, esperar a que termine
        resolvingJobs[song.id]?.let { return it.await() }

        val deferred = scope.async(Dispatchers.Main) {
            try {
                // Obtener URL del stream en IO
                val streamUrl = withContext(Dispatchers.IO) {
                    if (song.isDownloaded && song.localPath != null) {
                        if (song.localPath!!.startsWith("content://")) {
                            song.localPath
                        } else {
                            val file = java.io.File(song.localPath)
                            if (file.exists()) "file://${song.localPath}" else null
                        }
                    } else {
                        repository.getStreamUrl(extractVideoId(song.id))
                    }
                }

                if (streamUrl == null) return@async false

                // Buscar el índice actual de la canción (podría haber cambiado)
                val index = (0 until player.mediaItemCount).firstOrNull { i ->
                    player.getMediaItemAt(i).mediaId == song.id
                } ?: return@async false

                val currentItem = player.getMediaItemAt(index)
                val updatedItem = currentItem.buildUpon()
                    .setUri(streamUrl.toUri())
                    .build()

                player.replaceMediaItem(index, updatedItem)
                true
            } catch (e: Exception) {
                Log.e("MusicServiceConnection", "Error resolviendo canción ${song.id}", e)
                false
            } finally {
                resolvingJobs.remove(song.id)
            }
        }
        
        resolvingJobs[song.id] = deferred
        return deferred.await()
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

    fun playSong(selectedSong: Song, streamUrl: String, queue: List<Song> = emptyList()) {
        scope.launch {
            if (selectedSong.durationMs > MAX_DURATION_MS) {
                _error.value = "La canción supera el límite de 7 minutos."
                return@launch
            }

            _currentSong.value = selectedSong
            val newQueue = if (queue.isNotEmpty()) queue.filter { it.durationMs <= MAX_DURATION_MS } else listOf(selectedSong)

            val player = _player.value ?: return@launch

            _isLoading.value = true

            // Construir MediaItem de la canción seleccionada (ya tiene URI)
            val currentItem = buildMediaItem(selectedSong, streamUrl)

            // Resolver URIs del resto de la cola en paralelo
            val otherSongs = newQueue.filter { it.id != selectedSong.id }
            val resolvedItems = if (otherSongs.isNotEmpty()) {
                supervisorScope {
                    otherSongs.map { song ->
                        async {
                            try {
                                val uri = resolveUri(song)
                                uri?.let { song to buildMediaItem(song, it) }
                            } catch (e: Exception) {
                                Log.e("MusicServiceConnection", "Error resolviendo URI para ${song.id}", e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            } else emptyList()

            _isLoading.value = false

            // Actualizar cola solo con canciones resueltas exitosamente
            val resolvedSongs = listOf(selectedSong) + resolvedItems.map { it.first }
            _currentQueue.value = resolvedSongs

            val allItems = listOf(currentItem) + resolvedItems.map { it.second }
            if (allItems.isEmpty()) return@launch

            player.setMediaItems(allItems)
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
        val player = _player.value ?: return
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex < player.mediaItemCount) {
            scope.launch {
                _isLoading.value = true
                player.seekTo(nextIndex, 0)
                player.prepare()
                player.play()
                _isLoading.value = false
            }
        }
    }

    fun skipToPrevious() {
        val player = _player.value ?: return
        val prevIndex = player.currentMediaItemIndex - 1
        if (prevIndex >= 0) {
            scope.launch {
                _isLoading.value = true
                player.seekTo(prevIndex, 0)
                player.prepare()
                player.play()
                _isLoading.value = false
            }
        }
    }

    fun updateSongMetadata(updatedSong: Song) {
        val queue = _currentQueue.value
        if (queue.any { it.id == updatedSong.id }) {
            _currentQueue.value = queue.map { if (it.id == updatedSong.id) updatedSong else it }
        }
        if (_currentSong.value?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
    }

    fun addSongsToQueue(songs: List<Song>) {
        val player = _player.value ?: return
        scope.launch {
            val allowed = songs.filter { it.durationMs <= MAX_DURATION_MS }
            if (allowed.isEmpty()) return@launch

            val results = supervisorScope {
                allowed.map { song ->
                    async {
                        try {
                            val uri = resolveUri(song)
                            uri?.let { song to buildMediaItem(song, it) }
                        } catch (e: Exception) {
                            Log.e("MusicServiceConnection", "Error resolviendo URI para ${song.id}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (results.isEmpty()) return@launch

            val addedSongs = results.map { it.first }
            val mediaItems = results.map { it.second }

            player.addMediaItems(mediaItems)
            _currentQueue.value += addedSongs

            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
                player.play()
            }
        }
    }

    fun removeSongsFromQueue(songIds: List<String>) {
        val player = _player.value ?: return
        scope.launch {
            val indicesToRemove = mutableListOf<Int>()
            for (i in 0 until player.mediaItemCount) {
                if (songIds.contains(player.getMediaItemAt(i).mediaId)) {
                    indicesToRemove.add(i)
                }
            }
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
            _currentQueue.value = _currentQueue.value.filterNot { songIds.contains(it.id) }
        }
    }

    fun moveSongInQueue(fromIndex: Int, toIndex: Int) {
        val player = _player.value ?: return
        scope.launch {
            player.moveMediaItem(fromIndex, toIndex)
            val mutableList = _currentQueue.value.toMutableList()
            val song = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, song)
            _currentQueue.value = mutableList
        }
    }

    private var isFetchingRecommendations = false

    private fun checkAndTriggerRecommendations(player: Player) {
        if (isFetchingRecommendations) return
        if (!settingsManager.getAutoPlayRelated()) return
        val remainingItems = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remainingItems <= 1) {
            scope.launch {
                isFetchingRecommendations = true
                try {
                    val currentQueue = _currentQueue.value
                    if (currentQueue.isEmpty()) return@launch
                    val currentIndex = player.currentMediaItemIndex
                    val lastSongs = mutableListOf<Song>()
                    if (currentIndex >= 0 && currentIndex < currentQueue.size) lastSongs.add(currentQueue[currentIndex])
                    if (currentIndex > 0 && currentIndex - 1 < currentQueue.size) lastSongs.add(currentQueue[currentIndex - 1])

                    if (lastSongs.isNotEmpty()) {
                        val recommendations = recommendationEngine.getRecommendations(lastSongs, currentQueue)
                        if (recommendations.isNotEmpty()) {
                            addSongsToQueue(recommendations)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicServiceConnection", "Error triggering recommendations", e)
                } finally {
                    isFetchingRecommendations = false
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun extractVideoId(url: String): String = repository.extractVideoId(url)

    fun updateQueue(currentSong: Song, newQueue: List<Song>) {
        val player = _player.value ?: return
        if (player.currentMediaItem?.mediaId != currentSong.id) return

        val filteredQueue = newQueue.filter { it.durationMs <= MAX_DURATION_MS }
        if (filteredQueue.none { it.id == currentSong.id }) return

        scope.launch {
            val results = supervisorScope {
                filteredQueue.map { song ->
                    async {
                        try {
                            val uri = if (song.id == currentSong.id) {
                                (0 until player.mediaItemCount)
                                    .firstOrNull { player.getMediaItemAt(it).mediaId == song.id }
                                    ?.let { player.getMediaItemAt(it).localConfiguration?.uri?.toString() }
                                    ?: resolveUri(song)
                            } else {
                                resolveUri(song)
                            }
                            uri?.let { song to buildMediaItem(song, it) }
                        } catch (e: Exception) {
                            Log.e("MusicServiceConnection", "Error resolviendo URI para ${song.id}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (results.isEmpty()) return@launch

            val addedSongs = results.map { it.first }
            val mediaItems = results.map { it.second }

            // Actualizar cola solo con canciones resueltas exitosamente
            _currentQueue.value = addedSongs

            val newCurrentIndex = addedSongs.indexOfFirst { it.id == currentSong.id }

            val playerCurrentIndex = (0 until player.mediaItemCount)
                .firstOrNull { player.getMediaItemAt(it).mediaId == currentSong.id }
                ?: return@launch

            if (player.mediaItemCount > 1) {
                if (playerCurrentIndex + 1 < player.mediaItemCount)
                    player.removeMediaItems(playerCurrentIndex + 1, player.mediaItemCount)
                if (playerCurrentIndex > 0)
                    player.removeMediaItems(0, playerCurrentIndex)
            }

            if (newCurrentIndex > 0)
                player.addMediaItems(0, mediaItems.take(newCurrentIndex))
            if (newCurrentIndex + 1 < mediaItems.size)
                player.addMediaItems(newCurrentIndex + 1, mediaItems.drop(newCurrentIndex + 1))
        }
    }
}
