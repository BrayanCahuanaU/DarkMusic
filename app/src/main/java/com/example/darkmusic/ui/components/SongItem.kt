package com.example.darkmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.darkmusic.core.designsystem.AppleMusicRed
import com.example.darkmusic.core.designsystem.LabelSecondaryDark
import com.example.darkmusic.domain.model.Song

@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloading: Boolean = false // Nuevo parámetro para el estado de descarga
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            
            // Retroalimentación visual de descarga (Barra de progreso)
            if (isDownloading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(50.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
                    color = AppleMusicRed,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = LabelSecondaryDark,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        
        // Icono de Descarga
        IconButton(onClick = onDownloadClick, enabled = !isDownloading) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AppleMusicRed
                )
            } else {
                Icon(
                    imageVector = if (song.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                    contentDescription = "Download",
                    tint = if (song.isDownloaded) AppleMusicRed else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Icono de Favorito (Retroalimentación visual inmediata)
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) AppleMusicRed else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Icono de 3 puntos (Menu)
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Añadir a lista de reproducción") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        onAddToPlaylist()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Agregar a álbum") },
                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                    onClick = {
                        onAddToAlbum()
                        showMenu = false
                    }
                )
            }
        }
    }
}
