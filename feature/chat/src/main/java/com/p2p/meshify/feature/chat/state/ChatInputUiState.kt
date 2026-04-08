package com.p2p.meshify.feature.chat.state

import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.ui.components.ForwardDialogState

/**
 * UI state for text input, reply, draft, send operations, and forwarding.
 * Extracted from ChatViewModel to reduce coupling and improve testability.
 *
 * @property inputText Current text in the input field.
 * @property draftText Persisted draft text that survives configuration changes.
 * @property replyTo The message being replied to, or null if no reply.
 * @property isSending Whether a message send operation is in progress.
 * @property sendError Optional error message for a failed message send attempt.
 * @property forwardDialogState State for the forward message dialog.
 * @property selectedMessages Set of message IDs selected in multi-select mode.
 */
data class ChatInputUiState(
    val inputText: String = "",
    val draftText: String = "",
    val replyTo: MessageEntity? = null,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val forwardDialogState: ForwardDialogState = ForwardDialogState(),
    val selectedMessages: Set<String> = emptySet()
)
