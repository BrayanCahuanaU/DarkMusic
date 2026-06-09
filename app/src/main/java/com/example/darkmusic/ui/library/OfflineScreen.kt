package com.example.darkmusic.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.darkmusic.core.designsystem.CanvasBlack
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    onBackClick: () -> Unit,
    onSongClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val downloadedSongs = state.allSongs.filter { it.isDownloaded }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = CanvasBlack) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Música local", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasBlack,
                    titleContentColor = Color.White
                )
            )

            if (downloadedSongs.isEmpty()) {
                EmptyLibrarySection("No tienes música descargada aún.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(downloadedSongs) { song ->
                        SongItem(
                            song = song,
                            onClick = { 
                                viewModel.onSongClick(song)
                                onSongClick()
                            },
                            onFavoriteClick = { viewModel.toggleFavorite(song) },
                            onDownloadClick = { viewModel.downloadSong(song) },
                            onAddToPlaylist = { songToAddToPlaylist = song },
                            onAddToAlbum = { viewModel.addToAlbum(song) },
                            isDownloading = state.downloadingSongIds.contains(song.id)
                        )
                    }
                }
            }
        }

        if (songToAddToPlaylist != null) {
            AddToPlaylistDialog(
                playlists = state.playlists,
                onDismiss = { songToAddToPlaylist = null },
                onPlaylistSelected = { playlist ->
                    songToAddToPlaylist?.let { song ->
                        viewModel.addToPlaylist(song, playlist.id)
                    }
                    songToAddToPlaylist = null
                }
            )
        }
    }
}
