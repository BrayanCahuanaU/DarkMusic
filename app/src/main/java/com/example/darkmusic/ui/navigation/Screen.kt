package com.example.darkmusic.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Library : Screen("library")
    object Search : Screen("search")
    object Player : Screen("player")
    object Offline : Screen("offline")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist_detail/$playlistId"
    }
}
