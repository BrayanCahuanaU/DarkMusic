package com.example.darkmusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.darkmusic.core.designsystem.*
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.ui.home.HomeScreen
import com.example.darkmusic.ui.library.LibraryScreen
import com.example.darkmusic.ui.library.OfflineScreen
import com.example.darkmusic.ui.library.PlaylistDetailScreen
import com.example.darkmusic.ui.settings.SettingsScreen
import com.example.darkmusic.ui.navigation.Screen
import com.example.darkmusic.ui.player.PlayerScreen
import com.example.darkmusic.ui.player.PlayerViewModel
import com.example.darkmusic.ui.search.SearchScreen

/**
 * Contenedor principal de la aplicación que gestiona la navegación por pestañas (BottomBar).
 */
@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    
    // Definición de los elementos de navegación de la barra inferior
    val items = listOf(
        NavigationItem("Escuchar", Screen.Home.route, Icons.Default.Home),
        NavigationItem("Ajustes", Screen.Settings.route, Icons.Default.Settings),
        NavigationItem("Biblioteca", Screen.Library.route, Icons.Default.LibraryMusic),
        NavigationItem("Buscar", Screen.Search.route, Icons.Default.Search)
    )

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Player.route) {
                Column {
    // Mini Reproductor
    val currentSongValue = currentSong
    if (currentSongValue != null) {
        MiniPlayer(
            song = currentSongValue,
            isPlaying = isPlaying,
            onPlayPause = { playerViewModel.playPause() },
            onClick = { navController.navigate(Screen.Player.route) },
            onNext = { playerViewModel.skipNext() }
        )
    }

                    // Barra de navegación inferior
                    NavigationBar(
                        containerColor = CanvasBlack.copy(alpha = 0.95f),
                        contentColor = Color.White,
                        tonalElevation = 0.dp
                    ) {
                        val currentDestination = navBackStackEntry?.destination
                        
                        items.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) },
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppleMusicRed,
                                    selectedTextColor = AppleMusicRed,
                                    unselectedIconColor = LabelSecondaryDark,
                                    unselectedTextColor = LabelSecondaryDark,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(if (currentRoute == Screen.Player.route) PaddingValues(0.dp) else innerPadding)
        ) {
            composable(Screen.Home.route) { 
                HomeScreen(onSongClick = { navController.navigate(Screen.Player.route) }) 
            }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Library.route) { 
                LibraryScreen(
                    onOfflineClick = { navController.navigate(Screen.Offline.route) },
                    onPlaylistClick = { playlist -> 
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                    },
                    onSongClick = { navController.navigate(Screen.Player.route) }
                ) 
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) {
                PlaylistDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onSongClick = { navController.navigate(Screen.Player.route) },
                    onAddSongsClick = { navController.navigate(Screen.Search.route) }
                )
            }
            composable(Screen.Offline.route) {
                OfflineScreen(
                    onBackClick = { navController.popBackStack() },
                    onSongClick = { navController.navigate(Screen.Player.route) }
                )
            }
            composable(Screen.Search.route) { 
                SearchScreen(onSongClick = { navController.navigate(Screen.Player.route) }) 
            }
            composable(Screen.Player.route) { PlayerScreen() }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Surface1Dark)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
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
                color = Color.White,
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
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Pantalla genérica para secciones que aún no han sido implementadas.
 */
@Composable
fun PlaceholderScreen(name: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = CanvasBlack) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = "Pantalla de $name\n(Próximamente)",
                style = MaterialTheme.typography.titleLarge,
                color = LabelSecondaryDark,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Clase de datos para representar un item de la BottomBar.
 */
data class NavigationItem(val title: String, val route: String, val icon: ImageVector)
