package com.example.darkmusic.playback.service

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject
    lateinit var player: Player

    private var mediaSession: MediaSession? = null

    /**
     * Callback para manejar la reanudación de la música.
     * Implementado para evitar el crash 'UnsupportedOperationException'.
     */
    private val callback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = mediaSession.player
            
            // Obtenemos la lista actual de canciones del reproductor
            val mediaItems = mutableListOf<MediaItem>()
            for (i in 0 until player.mediaItemCount) {
                mediaItems.add(player.getMediaItemAt(i))
            }
            
            // Creamos el objeto de retorno con la posición exacta donde se quedó
            val mediaItemsWithStartPosition = MediaSession.MediaItemsWithStartPosition(
                mediaItems,
                player.currentMediaItemIndex,
                player.currentPosition
            )
            
            return Futures.immediateFuture(mediaItemsWithStartPosition)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

        override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            // Detener el servicio solo si no estamos reproduciendo y no hay elementos en la cola
            // Esto permite que la música continúe reproduciéndose incluso si el usuario aleja la app
            // y permite una reanudación rápida si está pausada pero tiene una cola cargada
            if (!player.playWhenReady && player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
