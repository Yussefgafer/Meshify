package com.p2p.meshify.feature.chat.state

import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.domain.model.TransportType

/**
 * UI state for message display, pagination, and connection status.
 * Extracted from ChatViewModel to reduce coupling and improve testability.
 *
 * @property isLoading Whether the initial message load is in progress.
 * @property messages The current list of messages displayed in the chat.
 * @property isOnline Whether the peer is currently online.
 * @property isPeerTyping Whether the peer is currently typing.
 * @property hasMoreMessages Whether there are older messages available to load.
 * @property isLoadingMore Whether older messages are currently being loaded.
 * @property securityWarning Optional warning message to display to the user.
 * @property transportUsed Map of message IDs to the transport type used for sending.
 * @property sendError Optional error message for a failed message send attempt.
 * @property uploadError Optional error message for a failed file upload attempt.
 */
data class ChatMessagesUiState(
    val isLoading: Boolean = true,
    val messages: List<MessageEntity> = emptyList(),
    val isOnline: Boolean = false,
    val isPeerTyping: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val securityWarning: String? = null,
    val transportUsed: Map<String, TransportType> = emptyMap(),
    val sendError: String? = null,
    val uploadError: String? = null
)
