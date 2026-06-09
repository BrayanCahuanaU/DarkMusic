package com.example.darkmusic.ui.player

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.darkmusic.core.designsystem.*
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.components.AddToPlaylistDialog
import java.util.concurrent.TimeUnit
import java.util.Locale
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CanvasBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            AsyncImage(
                model = currentSong?.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "No se está reproduciendo nada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "-",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LabelSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { currentSong?.let { viewModel.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (currentSong?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (currentSong?.isFavorite == true) AppleMusicRed else LabelSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosition), color = LabelSecondaryDark, fontSize = 12.sp)
                    Text(text = formatTime(duration), color = LabelSecondaryDark, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    enabled = !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = if (isLoading) LabelSecondaryDark else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    onClick = { if (!isLoading) viewModel.playPause() },
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(72.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    enabled = !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (isLoading) LabelSecondaryDark else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { currentSong?.let { viewModel.downloadSong(it) } }) {
                    Icon(
                        imageVector = if (currentSong?.isDownloaded == true) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = "Descargar",
                        tint = if (currentSong?.isDownloaded == true) SystemBlue else LabelSecondaryDark
                    )
                }
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Ver cola", tint = if (showQueueSheet) Color.White else LabelSecondaryDark)
                }
                IconButton(onClick = { showMoreOptionsSheet = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Más", tint = if (showMoreOptionsSheet) Color.White else LabelSecondaryDark)
                }
            }
        }

        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = Surface1Dark,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
            ) {
                QueueList(
                    queue = currentQueue,
                    currentSongId = currentSong?.id,
                    onRemove = { viewModel.removeFromQueue(it) },
                    onReorder = { from, to -> viewModel.moveInQueue(from, to) },
                    onSongClick = { song -> 
                        viewModel.onSongClick(song, currentQueue)
                    }
                )
            }
        }

        if (showMoreOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreOptionsSheet = false },
                containerColor = Surface1Dark,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
            ) {
                MoreOptionsContent(
                    song = currentSong,
                    onAddToPlaylist = { showAddToPlaylistDialog = true },
                    onShare = { 
                        currentSong?.let { song ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Escucha esta canción: ${song.title}")
                                putExtra(Intent.EXTRA_TEXT, "Te comparto esta canción de ${song.artist}: ${song.mediaUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir canción"))
                        }
                    },
                    onDismiss = { showMoreOptionsSheet = false }
                )
            }
        }

        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showAddToPlaylistDialog = false },
                onPlaylistSelected = { playlist ->
                    currentSong?.let { viewModel.addToPlaylist(it, playlist.id) }
                    showAddToPlaylistDialog = false
                }
            )
        }
    }
}

@Composable
fun MoreOptionsContent(
    song: Song?,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = LabelSecondaryDark,
                        fontSize = 14.sp
                    )
                }
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            
            ListItem(
                headlineContent = { Text("Añadir a una lista...") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White, leadingIconColor = Color.White),
                modifier = Modifier.clickable { 
                    onAddToPlaylist()
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Compartir canción") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White, leadingIconColor = Color.White),
                modifier = Modifier.clickable { 
                    onShare()
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Información de la canción") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White, leadingIconColor = Color.White),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

@Composable
fun QueueList(
    queue: List<Song>,
    currentSongId: String?,
    onRemove: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "A continuación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            // Aquí se podría añadir un botón de "Limpiar cola" si se desea
        }

        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            onMove = { from, to -> onReorder(from.index, to.index) }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                ReorderableItem(reorderableLazyListState, key = song.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    Surface(
                        tonalElevation = elevation,
                        color = if (isDragging) Color.White.copy(alpha = 0.1f) else Color.Transparent
                    ) {
                        QueueItem(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            onRemove = { onRemove(song.id) },
                            modifier = Modifier.draggableHandle()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) AppleMusicRed else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = LabelSecondaryDark,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Quitar",
                tint = LabelSecondaryDark,
                modifier = Modifier.size(20.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.Reorder,
            contentDescription = "Mover",
            tint = LabelSecondaryDark,
            modifier = modifier.size(20.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}