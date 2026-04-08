package com.p2p.meshify.feature.chat.state

import com.p2p.meshify.core.ui.model.StagedAttachment

/**
 * UI state for attachment staging, upload progress, and media operations.
 * Extracted from ChatViewModel to reduce coupling and improve testability.
 *
 * @property stagedAttachments List of attachments staged for sending.
 * @property uploadProgress Map of message IDs to upload progress percentages (0-100).
 * @property uploadError Optional error message for a failed file upload attempt.
 */
data class ChatAttachmentsUiState(
    val stagedAttachments: List<StagedAttachment> = emptyList(),
    val uploadProgress: Map<String, Int> = emptyMap(),
    val uploadError: String? = null
)
