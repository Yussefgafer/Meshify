package com.p2p.meshify.domain.security.model

/**
 * Plaintext message envelope for LAN-only P2P communication.
 *
 * After Phase 3 security simplification, messages are sent as plaintext
 * without encryption. This envelope carries the message metadata and content.
 *
 * Structure:
 * - senderId: who sent this message
 * - recipientId: who this message is for
 * - text: the message content (plaintext)
 * - timestamp: when message was created
 * - messageType: type of message (text, image, video, etc.)
 */
data class MessageEnvelope(
    val senderId: String,
    val recipientId: String,
    val text: String,
    val timestamp: Long,
    val messageType: String = "text"
)
