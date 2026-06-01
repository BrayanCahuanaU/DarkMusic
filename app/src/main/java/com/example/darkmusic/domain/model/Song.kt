package com.example.darkmusic.domain.model

/**
 * Modelo de datos principal para una Canción.
 * Se utiliza en toda la capa de dominio y UI.
 */
data class Song(
    val id: String,              // ID único (puede ser el de YouTube o un UUID)
    val title: String,           // Nombre de la canción
    val artist: String,          // Nombre del artista o canal
    val album: String?,          // Nombre del álbum (opcional)
    val genre: String?,          // Género musical
    val durationMs: Long,        // Duración en milisegundos
    val coverUrl: String?,       // URL de la imagen de portada
    val mediaUrl: String,        // URL o path del archivo de audio
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null // Ruta en el almacenamiento si está descargada
)
