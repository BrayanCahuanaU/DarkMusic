package com.example.darkmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.darkmusic.core.designsystem.AppleMusicRed
import com.example.darkmusic.core.designsystem.CanvasBlack
import com.example.darkmusic.core.designsystem.LabelSecondaryDark
import com.example.darkmusic.ui.home.HomeScreen
import com.example.darkmusic.ui.navigation.Screen

/**
 * Contenedor principal de la aplicación que gestiona la navegación por pestañas (BottomBar).
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    // Definición de los elementos de navegación de la barra inferior
    val items = listOf(
        NavigationItem("Escuchar", Screen.Home.route, Icons.Default.Home),
        NavigationItem("Novedades", Screen.New.route, Icons.Default.MusicNote),
        NavigationItem("Radio", Screen.Radio.route, Icons.Default.Radio),
        NavigationItem("Biblioteca", Screen.Library.route, Icons.Default.LibraryMusic),
        NavigationItem("Buscar", Screen.Search.route, Icons.Default.Search)
    )

    Scaffold(
        bottomBar = {
            // Barra de navegación inferior con estilo translúcido (imitando blur de iOS)
            NavigationBar(
                containerColor = CanvasBlack.copy(alpha = 0.9f),
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                // Observamos la ruta actual para resaltar el icono seleccionado
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selected,
                        onClick = {
                            // Navegación optimizada para no acumular la misma pantalla en el stack
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
                            indicatorColor = Color.Transparent // Quitamos el círculo de fondo de M3
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // Host de navegación que define qué pantalla se muestra según la ruta
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Ruta "Escuchar": La pantalla que acabamos de mejorar
            composable(Screen.Home.route) { HomeScreen() }
            
            // Rutas temporales con placeholders
            composable(Screen.New.route) { PlaceholderScreen("Novedades") }
            composable(Screen.Radio.route) { PlaceholderScreen("Radio") }
            composable(Screen.Library.route) { PlaceholderScreen("Biblioteca") }
            composable(Screen.Search.route) { PlaceholderScreen("Buscar") }
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
