package com.p2p.meshify.core.ui.model

/**
 * UI model for a chat conversation, used in UI components.
 * This replaces direct dependency on [com.p2p.meshify.core.data.local.entity.ChatEntity]
 * to keep core:ui free of data-layer dependencies.
 */
data class ChatUiModel(
    val peerId: String,
    val peerName: String,
    val lastMessage: String?,
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0
)
