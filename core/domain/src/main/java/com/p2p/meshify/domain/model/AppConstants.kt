package com.p2p.meshify.domain.model

/**
 * Application-wide constants
 */
object AppConstants {
    /**
     * Maximum file size for media forwarding.
     * Prevents OOM errors and excessive bandwidth usage.
     *
     * Rationale:
     * - 100MB is large enough for most photos/videos
     * - Prevents OOM on devices with limited heap (<256MB)
     * - Reduces network bandwidth for slow connections
     */
    const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L // 100MB

    /**
     * Default prefix for peer names when no display name is available.
     * Used in ChatRepositoryImpl.updateChatLastMessage() and saveIncomingMessage().
     */
    const val DEFAULT_PEER_NAME_PREFIX = "Peer_"
}
