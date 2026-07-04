package com.p2p.meshify.core.ui.model

import com.p2p.meshify.domain.model.MessageType

/**
 * UI model for a message, used in UI components like ForwardMessageDialog.
 * This replaces direct dependency on [com.p2p.meshify.core.data.local.entity.MessageEntity]
 * to keep core:ui free of data-layer dependencies.
 */
data class MessageUiModel(
    val id: String,
    val text: String?,
    val type: MessageType,
    val timestamp: Long = 0L
)
