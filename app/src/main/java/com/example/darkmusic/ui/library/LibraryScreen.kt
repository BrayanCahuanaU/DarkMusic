package com.example.darkmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.darkmusic.core.designsystem.AppleMusicRed
import com.example.darkmusic.core.designsystem.CanvasBlack
import com.example.darkmusic.core.designsystem.LabelSecondaryDark
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.components.SongItem
import com.example.darkmusic.ui.home.SectionHeader

@Composable
fun LibraryScreen(
    onOfflineClick: () -> Unit,
    onSongClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = CanvasBlack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Biblioteca",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    IconButton(onClick = { viewModel.signInWithSupabase() }) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sync with Supabase",
                            tint = AppleMusicRed
                        )
                    }
                }
            }

            // SECCIÓN: FAVORITOS
            if (state.favoriteSongs.isNotEmpty()) {
                item {
                    SectionHeader("Favoritos")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.favoriteSongs) { song ->
                            FavoriteSongCard(song) {
                                viewModel.onSongClick(song)
                                onSongClick()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // SECCIÓN: ÁLBUMES
            item {
                SectionHeader("Álbumes")
            }

            if (state.albums.isNotEmpty()) {
                items(state.albums) { album ->
                    AlbumRow(
                        album = album,
                        onClick = {
                            if (album.name == "Offline") {
                                onOfflineClick()
                            }
                        }
                    )
                }
            } else {
                item {
                    EmptyLibrarySection("No se encontraron álbumes organizados.")
                }
            }

            // SECCIÓN: TODAS LAS CANCIONES
            if (state.allSongs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionHeader("Canciones")
                }
                items(state.allSongs) { song ->
                    SongItem(
                        song = song,
                        onClick = { 
                            viewModel.onSongClick(song)
                            onSongClick()
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(song) },
                        onDownloadClick = { viewModel.downloadSong(song) },
                        onAddToPlaylist = { viewModel.addToPlaylist(song) },
                        onAddToAlbum = { viewModel.addToAlbum(song) },
                        isDownloading = state.downloadingSongIds.contains(song.id)
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist,
            color = LabelSecondaryDark,
            maxLines = 1,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AlbumRow(album: AlbumItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (album.name == "Offline") AppleMusicRed.copy(alpha = 0.1f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (album.name == "Offline") {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = AppleMusicRed,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = album.name,
                color = if (album.name == "Offline") AppleMusicRed else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${album.artist} • ${album.songCount} canciones",
                color = LabelSecondaryDark,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun EmptyLibrarySection(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = LabelSecondaryDark,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
