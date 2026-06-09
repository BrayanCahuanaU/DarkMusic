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
import com.example.darkmusic.domain.model.Playlist
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.components.SongItem
import com.example.darkmusic.ui.home.SectionHeader

@Composable
fun LibraryScreen(
    onOfflineClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

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

            // SECCIÓN: PLAYLISTS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Playlists")
                    IconButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Crear Playlist", tint = AppleMusicRed)
                    }
                }
            }

            // Item especial para música Offline
            item {
                OfflineRow(onClick = onOfflineClick)
            }

            if (state.playlists.isNotEmpty()) {
                items(state.playlists) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDeleteClick = { viewModel.deletePlaylist(playlist) },
                        onRenameClick = { playlistToRename = playlist }
                    )
                }
            } else {
                item {
                    EmptyLibrarySection("No has creado ninguna playlist aún.")
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
                        onAddToQueue = { viewModel.addToQueue(song) },
                        onAddToPlaylist = { songToAddToPlaylist = song },
                        onAddToAlbum = { songToAddToPlaylist = song },
                        isDownloading = state.downloadingSongIds.contains(song.id)
                    )
                }
            }
        }

        if (showCreatePlaylistDialog) {
            PlaylistNameDialog(
                title = "Nueva Playlist",
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                }
            )
        }

        if (playlistToRename != null) {
            PlaylistNameDialog(
                title = "Editar nombre",
                initialName = playlistToRename?.name ?: "",
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    playlistToRename?.let { viewModel.renamePlaylist(it.id, newName) }
                    playlistToRename = null
                }
            )
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

@Composable
fun OfflineRow(onClick: () -> Unit) {
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
                .background(AppleMusicRed.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = null,
                tint = AppleMusicRed,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Música Offline",
                color = AppleMusicRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Canciones descargadas",
                color = LabelSecondaryDark,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist, 
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.songs.isNotEmpty()) {
                AsyncImage(
                    model = playlist.songs.first().coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${playlist.songs.size} canciones",
                color = LabelSecondaryDark,
                fontSize = 14.sp
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color.White)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Editar nombre") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        onRenameClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar playlist", color = Color.Red) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    onClick = {
                        onDeleteClick()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nombre de la playlist") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    // Ya no se usa, reemplazado por PlaylistNameDialog
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a Playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No tienes playlists. Crea una primero.")
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        Text(
                            text = playlist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist) }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
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
