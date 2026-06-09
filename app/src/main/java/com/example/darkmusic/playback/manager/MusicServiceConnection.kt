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
    private val recommendationEngine: com.example.darkmusic.domain.recommendation.RecommendationEngine
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
                    val song = _currentQueue.value.find { it.id == item.mediaId }
                    _currentSong.value = song

                    // Pre-cargar el siguiente item de la cola
                    val nextIndex = player.currentMediaItemIndex + 1
                    if (nextIndex < player.mediaItemCount) {
                        val nextItem = player.getMediaItemAt(nextIndex)
                        val nextSong = _currentQueue.value.find { it.id == nextItem.mediaId }
                        if (nextItem.localConfiguration?.uri == null && nextSong != null) {
                            fetchAndSetUri(nextSong, player)
                        }
                    }

                    // Si el item actual no tiene URI (se añadió sin resolver), intentar resolver y reproducir
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < player.mediaItemCount) {
                        val currentItem = player.getMediaItemAt(currentIndex)
                        if (currentItem.localConfiguration?.uri == null) {
                            val currentSong = _currentQueue.value.find { it.id == currentItem.mediaId }
                            if (currentSong != null) {
                                scope.launch {
                                    val replaced = resolveAndReplaceMediaItem(currentSong, player)
                                    if (replaced) {
                                        try {
                                            player.prepare()
                                            player.play()
                                        } catch (e: Exception) {
                                            Log.e("MusicServiceConnection", "Error al reanudar reproducción después de reemplazar mediaItem", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
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
            }
        })
    }

        private fun fetchAndSetUri(song: Song, player: Player) {
        scope.launch {
            val streamUrl = if (song.isDownloaded && song.localPath != null) {
                val file = java.io.File(song.localPath)
                if (file.exists()) {
                    "file://${song.localPath}" // Siempre usar el esquema file://
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
                        .setUri(uri.toUri())
                        .build()

                    player.replaceMediaItem(mediaIndex, updatedItem)
                }
            }
        }
    }

    private suspend fun resolveAndReplaceMediaItem(song: Song, player: Player): Boolean {

        // IO solamente para resolver stream
        val streamUrl = withContext(Dispatchers.IO) {

            if (song.isDownloaded && song.localPath != null) {
                val file = java.io.File(song.localPath)

                if (file.exists()) {
                    "file://${song.localPath}"
                } else {
                    null
                }
            } else {
                repository.getStreamUrl(extractVideoId(song.id))
            }
        }

        if (streamUrl == null) return false

        // TODO lo relacionado a player/controller en Main
        return withContext(Dispatchers.Main) {

            val index = (0 until player.mediaItemCount).firstOrNull { i ->
                player.getMediaItemAt(i).mediaId == song.id
            } ?: return@withContext false

            val updatedItem = player.getMediaItemAt(index)
                .buildUpon()
                .setUri(streamUrl.toUri())
                .build()

            player.replaceMediaItem(index, updatedItem)

            true
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

            // Bloquear canciones demasiado largas
            if (selectedSong.durationMs > MAX_DURATION_MS) {
                _error.value = "La canción supera el límite de 7 minutos y no puede reproducirse."
                return@launch
            }

            _currentSong.value = selectedSong
            
            // Actualizar la cola actual para que onMediaItemTransition pueda encontrar las canciones
            val newQueue = if (queue.isNotEmpty()) queue.filter { it.durationMs <= MAX_DURATION_MS } else listOf(selectedSong)
            _currentQueue.value = newQueue

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
                        setArtworkUri(it.toUri())
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(selectedSong.id)
                .setUri(streamUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem)
            
            // Si hay cola, agregar los demás items (filtrados por duración)
            if (newQueue.isNotEmpty()) {
                val queueItems = newQueue.filter { it.id != selectedSong.id }.map { song ->
                    MediaItem.Builder()
                        .setMediaId(song.id)
                        // Inicialmente sin URI; se cargarán cuando sea necesario en fetchAndSetUri
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .apply {
                                    song.coverUrl?.takeIf { it.isNotEmpty() }?.let {
                                        setArtworkUri(it.toUri())
                                    }
                                }
                                .build()
                        )
                        .build()
                }
                player.addMediaItems(queueItems)
            }

            // Pre-cargar el segundo item de la cola si existe
            if (newQueue.size > 1) {
                val nextSong = newQueue.first { it.id != selectedSong.id }
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
        val player = _player.value ?: return

        scope.launch {
            try {

                val nextIndex = player.currentMediaItemIndex + 1

                if (nextIndex < player.mediaItemCount) {

                    val nextItem = player.getMediaItemAt(nextIndex)

                    if (nextItem.localConfiguration?.uri == null) {

                        val nextSong = _currentQueue.value
                            .find { it.id == nextItem.mediaId }

                        if (nextSong != null) {

                            resolveAndReplaceMediaItem(nextSong, player)

                        }
                    }

                    // mover inmediatamente
                    player.seekTo(nextIndex, 0)
                    player.playWhenReady = true
                    player.prepare()

                }

            } catch (e: Exception) {

                Log.e("MusicServiceConnection", "Error en skipToNext", e)

            }
        }
    }
    fun skipToPrevious() {
        val player = _player.value ?: return

        scope.launch {
            try {
                val prevIndex = player.currentMediaItemIndex - 1

                if (prevIndex < 0) return@launch

                val prevItem = player.getMediaItemAt(prevIndex)

                if (prevItem.localConfiguration?.uri == null) {

                    val prevSong = _currentQueue.value.find {
                        it.id == prevItem.mediaId
                    } ?: return@launch

                    val resolved = resolveAndReplaceMediaItem(prevSong, player)

                    if (!resolved) return@launch
                }

                withContext(Dispatchers.Main) {
                    player.seekTo(prevIndex, 0)
                    player.playWhenReady = true
                    player.prepare()
                }

            } catch (e: Exception) {
                Log.e("MusicServiceConnection", "Error en skipToPrevious", e)
            }
        }
    }

    fun updateSongMetadata(updatedSong: Song) {
        // Actualizar la cola actual
        val queue = _currentQueue.value
        if (queue.any { it.id == updatedSong.id }) {
            _currentQueue.value = queue.map { if (it.id == updatedSong.id) updatedSong else it }
        }
        // Actualizar la canción actual si coincide
        if (_currentSong.value?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
    }

    fun addSongsToQueue(songs: List<Song>) {
        val player = _player.value ?: return
        scope.launch {
            val allowed = songs.filter { it.durationMs <= MAX_DURATION_MS }
            if (allowed.size < songs.size) {
                Log.d("MusicServiceConnection", "Skipped ${songs.size - allowed.size} items longer than 7 minutes")
            }

            val mediaItems = allowed.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .apply {
                                song.coverUrl?.takeIf { it.isNotEmpty() }
                                    ?.let { setArtworkUri(it.toUri()) }
                            }
                            .build()
                    )
                    .build()
            }
            player.addMediaItems(mediaItems)
            _currentQueue.value += allowed
            
            // Si el reproductor estaba detenido, prepararlo y reproducir la primera canción añadida
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
            // Eliminar de atrás hacia adelante para no invalidar índices
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

        val remainingItems = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remainingItems <= 1) {
            scope.launch {
                isFetchingRecommendations = true
                try {
                    val currentQueue = _currentQueue.value
                    if (currentQueue.isEmpty()) return@launch

                    // Tomar las últimas 2 canciones reproducidas o en reproducción
                    val currentIndex = player.currentMediaItemIndex
                    val lastSongs = mutableListOf<Song>()
                    
                    if (currentIndex >= 0 && currentIndex < currentQueue.size) {
                        lastSongs.add(currentQueue[currentIndex])
                    }
                    if (currentIndex > 0 && currentIndex - 1 < currentQueue.size) {
                        lastSongs.add(currentQueue[currentIndex - 1])
                    }

                    if (lastSongs.isNotEmpty()) {
                        val recommendations = recommendationEngine.getRecommendations(lastSongs, currentQueue)
                        if (recommendations.isNotEmpty()) {
                            addSongsToQueue(recommendations)
                            
                            // Si acabamos de añadir recomendaciones porque la cola estaba vacía (o casi),
                            // asegurarnos de resolver el URI de la siguiente inmediatamente.
                            val nextIndex = player.currentMediaItemIndex + 1
                            if (nextIndex < player.mediaItemCount) {
                                val nextItem = player.getMediaItemAt(nextIndex)
                                val nextSong = recommendations.find { it.id == nextItem.mediaId }
                                if (nextItem.localConfiguration?.uri == null && nextSong != null) {
                                    fetchAndSetUri(nextSong, player)
                                }
                            }
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
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    private fun extractVideoId(url: String): String {
        return repository.extractVideoId(url)
    }

    fun updateQueue(currentSong: Song, newQueue: List<Song>) {
        val player = _player.value ?: return
        if (player.currentMediaItem?.mediaId == currentSong.id) {
            // Filtrar canciones demasiado largas
            val filteredQueue = newQueue.filter { it.durationMs <= MAX_DURATION_MS }
            if (filteredQueue.size != newQueue.size) {
                Log.d("MusicServiceConnection", "Removed ${newQueue.size - filteredQueue.size} items >7min from updated queue")
            }
            if (filteredQueue.none { it.id == currentSong.id }) return

            _currentQueue.value = filteredQueue
            // No interrumpir el item actual, solo agregar los demás
            val currentIndex = filteredQueue.indexOfFirst { it.id == currentSong.id }
            if (currentIndex < 0) return
            scope.launch {
                val items = filteredQueue.mapIndexed { i, song ->
                    MediaItem.Builder()
                        .setMediaId(song.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .apply {
                                    song.coverUrl?.takeIf { it.isNotEmpty() }
                                        ?.let { setArtworkUri(it.toUri()) }
                                }
                                .build()
                        )
                        .build()
                }
                // Reemplazar todos excepto el actual que ya está reproduciéndose
                val playerCurrentIndex = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).mediaId == currentSong.id }
                    ?: return@launch
                
                // Limpiar items anteriores y posteriores
                if (player.mediaItemCount > 1) {
                    // Primero removemos lo que hay después
                    if (playerCurrentIndex + 1 < player.mediaItemCount) {
                        player.removeMediaItems(playerCurrentIndex + 1, player.mediaItemCount)
                    }
                    // Luego removemos lo que hay antes
                    if (playerCurrentIndex > 0) {
                        player.removeMediaItems(0, playerCurrentIndex)
                    }
                }

                // Ahora el item actual está en el índice 0 del player
                // Agregamos lo que va antes
                if (currentIndex > 0) {
                    player.addMediaItems(0, items.take(currentIndex))
                }
                // Agregamos lo que va después
                if (currentIndex + 1 < items.size) {
                    player.addMediaItems(currentIndex + 1, items.drop(currentIndex + 1))
                }
            }
        }
    }

}
