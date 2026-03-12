package com.p2p.meshify.core.ui.model

import android.net.Uri
import com.p2p.meshify.domain.model.MessageType

/**
 * Represents a media attachment staged for sending.
 * Holds the URI, raw bytes, and type before being sent.
 */
data class StagedAttachment(
    val uri: Uri,
    val bytes: ByteArray,
    val type: MessageType
)
