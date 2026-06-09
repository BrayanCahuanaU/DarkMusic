package com.example.darkmusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.darkmusic.core.designsystem.CanvasBlack
import com.example.darkmusic.core.designsystem.LabelSecondaryDark
import com.example.darkmusic.core.designsystem.Surface1Dark
import com.example.darkmusic.ui.components.AddToPlaylistDialog
import com.example.darkmusic.ui.components.SongItem

@Composable
fun SearchScreen(
    onSongClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var songToAddToPlaylist by remember { mutableStateOf<com.example.darkmusic.domain.model.Song?>(null) }
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        Text(
            text = "Buscar",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp)
        )

        SearchBar(
            query = state.query,
            onQueryChange = { viewModel.onQueryChange(it) },
            onClearQuery = { viewModel.onQueryChange("") }
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (state.searchResults.isEmpty()) {
            if (state.query.isBlank()) {
                if (state.recentSearches.isNotEmpty()) {
                    RecentSearchesSection(
                        recentSearches = state.recentSearches,
                        onSongClick = { song ->
                            viewModel.onSongClick(song)
                            onSongClick()
                        },
                        onRemoveRecentSearch = { viewModel.onRemoveRecentSearch(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { songToAddToPlaylist = it },
                        onClearHistory = { viewModel.onClearHistory() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Busca tus canciones favoritas", color = LabelSecondaryDark)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No se encontraron resultados", color = LabelSecondaryDark)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(state.searchResults) { song ->
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

        if (songToAddToPlaylist != null) {
            AddToPlaylistDialog(
                playlists = playlists,
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
fun RecentSearchesSection(
    recentSearches: List<com.example.darkmusic.domain.model.Song>,
    onSongClick: (com.example.darkmusic.domain.model.Song) -> Unit,
    onRemoveRecentSearch: (com.example.darkmusic.domain.model.Song) -> Unit,
    onAddToQueue: (com.example.darkmusic.domain.model.Song) -> Unit,
    onAddToPlaylist: (com.example.darkmusic.domain.model.Song) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Búsquedas recientes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            TextButton(onClick = onClearHistory) {
                Text(text = "Limpiar todo", color = Color.Red)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(recentSearches) { song ->
                SongItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onFavoriteClick = { /* No favorito en historial directamente para evitar desorden */ },
                    onDownloadClick = { /* No descarga en historial directamente */ },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onAddToAlbum = { onAddToPlaylist(song) },
                    onRemoveClick = { onRemoveRecentSearch(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp)),
        placeholder = { Text("Artistas, canciones, letras y más", color = LabelSecondaryDark) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LabelSecondaryDark) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = LabelSecondaryDark)
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface1Dark,
            unfocusedContainerColor = Surface1Dark,
            disabledContainerColor = Surface1Dark,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = Color.Red,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}
