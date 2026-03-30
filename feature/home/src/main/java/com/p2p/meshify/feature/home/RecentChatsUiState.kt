package com.p2p.meshify.feature.home

import com.p2p.meshify.core.data.local.entity.ChatEntity

/**
 * UI State for the Recent Chats (Home) Screen.
 * Represents all possible states: loading, error, empty, and content.
 */
data class RecentChatsUiState(
    val chats: List<ChatEntity> = emptyList(),
    val onlinePeers: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)
