package com.p2p.meshify.ui.navigation

/**
 * Route definitions for Meshify Navigation.
 * Centrally managed for safety and deep linking.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Discovery : Screen("discovery")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
}
