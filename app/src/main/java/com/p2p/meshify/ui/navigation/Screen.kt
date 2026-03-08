package com.p2p.meshify.ui.navigation

import kotlinx.serialization.Serializable

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
