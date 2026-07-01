package com.p2p.meshify.core.ui.model

import com.p2p.meshify.domain.model.MessageType

/**
 * UI model for a message attachment, used in UI components like AlbumMediaGrid.
 * This replaces direct dependency on [com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity]
 * to keep core:ui free of data-layer dependencies.
 */
data class AttachmentUiModel(
    val id: String,
    val type: MessageType,
    val filePath: String
)
