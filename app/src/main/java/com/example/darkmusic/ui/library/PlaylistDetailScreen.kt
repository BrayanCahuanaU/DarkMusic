package com.example.darkmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.darkmusic.core.designsystem.AppleMusicRed
import com.example.darkmusic.core.designsystem.CanvasBlack
import com.example.darkmusic.core.designsystem.LabelSecondaryDark
import com.example.darkmusic.ui.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBackClick: () -> Unit,
    onSongClick: () -> Unit,
    onAddSongsClick: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val playlist = state.playlist
    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Añadir a la lista de reproducción") },
                            leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                viewModel.addPlaylistToQueue()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar nombre") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showRenameDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar playlist", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                viewModel.deletePlaylist()
                                onBackClick()
                                showMenu = false
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBlack)
            )
        },
        containerColor = CanvasBlack
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppleMusicRed)
            }
        } else if (playlist != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    PlaylistHeader(
                        playlistName = playlist.name,
                        songCount = playlist.songs.size,
                        coverUrl = playlist.songs.firstOrNull()?.coverUrl,
                        onPlayClick = { viewModel.playAll() },
                        onShuffleClick = { viewModel.shuffle() },
                        onAddClick = onAddSongsClick
                    )
                }

                items(playlist.songs) { song ->
                    SongItem(
                        song = song,
                        onClick = {
                            viewModel.onSongClick(song, playlist.songs)
                            onSongClick()
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(song) },
                        onDownloadClick = { viewModel.downloadSong(song) },
                        onAddToPlaylist = { /* Ya está en la playlist */ },
                        onAddToAlbum = { /* Ya está en la playlist */ },
                        onRemoveClick = { viewModel.removeSongFromPlaylist(song.id) }
                    )
                }

                if (playlist.songs.isEmpty()) {
                    item {
                        EmptyPlaylistPlaceholder()
                    }
                }
            }
        }

        if (showRenameDialog) {
            PlaylistNameDialog(
                title = "Editar nombre",
                initialName = playlist?.name ?: "",
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    viewModel.renamePlaylist(newName)
                    showRenameDialog = false
                }
            )
        }
    }
}

@Composable
fun PlaylistHeader(
    playlistName: String,
    songCount: Int,
    coverUrl: String?,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = playlistName,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$songCount canciones",
            color = LabelSecondaryDark,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlayClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppleMusicRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reproducir")
            }
            
            Button(
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = AppleMusicRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aleatorio", color = AppleMusicRed)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = AppleMusicRed)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Añadir canciones")
        }
    }
}

@Composable
fun EmptyPlaylistPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = LabelSecondaryDark,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Esta playlist está vacía",
                color = LabelSecondaryDark,
                fontSize = 16.sp
            )
        }
    }
}
