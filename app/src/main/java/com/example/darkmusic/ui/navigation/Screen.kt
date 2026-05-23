package com.example.darkmusic.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object New : Screen("new")
    object Library : Screen("library")
    object Search : Screen("search")
    object Player : Screen("player")
    object Offline : Screen("offline")
}
