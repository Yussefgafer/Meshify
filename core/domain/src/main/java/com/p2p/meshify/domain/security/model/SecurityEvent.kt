package com.p2p.meshify.domain.security.model

/**
 * Security events that should be surfaced to the user.
 * These events indicate potential security issues that require user awareness.
 */
sealed class SecurityEvent {
    /**
     * Decryption failed for a message from a peer.
     * This could indicate tampering, key mismatch, or corrupted data.
     *
     * @param peerId The ID of the peer who sent the message
     * @param reason Human-readable explanation of the failure
     */
    data class DecryptionFailed(val peerId: String, val reason: String) : SecurityEvent()

    /**
     * TOFU (Trust On First Use) violation detected.
     * The peer's public key has changed, which could indicate a MITM attack.
     *
     * @param peerId The ID of the peer
     * @param oldKey The previously trusted public key (hex)
     * @param newKey The new public key presented by the peer (hex)
     */
    data class TofuViolation(val peerId: String, val oldKey: String, val newKey: String) : SecurityEvent()

    /**
     * Session with a peer has expired and needs to be re-established.
     *
     * @param peerId The ID of the peer
     */
    data class SessionExpired(val peerId: String) : SecurityEvent()
}
