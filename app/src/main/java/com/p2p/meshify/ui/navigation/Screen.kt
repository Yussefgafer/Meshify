package com.p2p.meshify.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Route definitions for Meshify Navigation 3.
 * Type-safe and state-aware.
 */
@Serializable
sealed class Screen {
    @Serializable data object Home : Screen()
    @Serializable data object Discovery : Screen()
    @Serializable data object Settings : Screen()
    @Serializable data class Chat(val peerId: String) : Screen()
}
