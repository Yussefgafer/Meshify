package com.p2p.meshify.domain.model

/**
 * Application-wide constants
 */
object AppConstants {
    /**
     * Maximum file size for media forwarding.
     *
     * Rationale:
     * - Must fit a single wire frame (receiver rejects frames over the cap)
     * - Prevents OOM on devices with limited heap (<256MB)
     */
    // Must fit a single wire frame: core.common AppConfig.MAX_PAYLOAD_SIZE_BYTES
    // is 10MB; keep a 64KB margin for envelope/framing overhead so the
    // serialized payload never exceeds the receiver's frame cap.
    const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024 - 64L * 1024 // ~10MB

    /**
     * Default prefix for peer names when no display name is available.
     * Used in ChatRepositoryImpl.updateChatLastMessage() and saveIncomingMessage().
     */
    const val DEFAULT_PEER_NAME_PREFIX = "Peer_"
}
