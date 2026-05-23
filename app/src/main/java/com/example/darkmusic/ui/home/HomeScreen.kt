package com.example.darkmusic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.components.SongItem

@Composable
fun HomeScreen(
    onSongClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading && state.songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Título Principal
            item {
                Text(
                    text = "Escuchar",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp)
                )
            }

            // SECCIÓN 1: SUGERENCIAS (Scroll Horizontal)
            if (state.suggestedSongs.isNotEmpty()) {
                item {
                    SectionHeader("Sugerencias para ti")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.suggestedSongs) { song ->
                            SuggestedSongCard(song) { 
                                viewModel.onSongClick(song)
                                onSongClick()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // SECCIÓN 2: RECIENTES
            if (state.recentSongs.isNotEmpty()) {
                item {
                    SectionHeader("Escuchado recientemente")
                }
                items(state.recentSongs) { song ->
                    SongItem(
                        song = song,
                        onClick = { 
                            viewModel.onSongClick(song)
                            onSongClick()
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(song) },
                        onDownloadClick = { viewModel.downloadSong(song) },
                        onAddToPlaylist = { /* TODO */ },
                        onAddToAlbum = { /* TODO */ },
                        isDownloading = state.downloadingSongIds.contains(song.id)
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // SECCIÓN 3: TOP MUNDIAL
            item {
                SectionHeader("Top Mundial")
            }

            items(state.songs) { song ->
                SongItem(
                    song = song,
                    onClick = { 
                        viewModel.onSongClick(song)
                        onSongClick()
                    },
                    onFavoriteClick = { viewModel.toggleFavorite(song) },
                    onDownloadClick = { viewModel.downloadSong(song) },
                    onAddToPlaylist = { /* TODO */ },
                    onAddToAlbum = { /* TODO */ },
                    isDownloading = state.downloadingSongIds.contains(song.id)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
    )
}

@Composable
fun SuggestedSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = song.artist,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
