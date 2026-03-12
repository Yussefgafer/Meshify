package com.p2p.meshify.core.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Sealed class representing all navigation destinations in the app.
 * Uses type-safe navigation with Kotlinx Serialization.
 */
@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()

    @Serializable
    data object Discovery : Screen()

    @Serializable
    data class Chat(val peerId: String, val peerName: String? = null) : Screen()

    @Serializable
    data object Settings : Screen()
}
