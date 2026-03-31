package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.core.common.util.HexUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Session key cache for ECDH-derived shared secrets.
 *
 * Stores derived session keys per peer ID along with the peer's public key
 * for TOFU (Trust On First Use) validation.
 *
 * Security properties:
 * - Session keys are cached per peer for efficient encryption/decryption
 * - Peer's public key is stored to detect key changes (TOFU violation)
 * - Session can be cleared if peer's public key changes
 *
 * Thread-safe via ConcurrentHashMap.
 */
class SessionKeyCache {

    data class SessionKeyInfo(
        val sessionKey: ByteArray,
        val peerPublicKeyHex: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    /** Map of peerId → SessionKeyInfo */
    private val cache = ConcurrentHashMap<String, SessionKeyInfo>()

    companion object {
        // Maximum session lifetime (24 hours)
        private const val MAX_SESSION_AGE_MS = 24 * 60 * 60 * 1000L

        // Maximum number of cached sessions
        private const val MAX_CACHE_SIZE = 1000
    }

    /**
     * Store a derived session key for a peer.
     * @param peerId the peer's ID
     * @param sessionKey the 32-byte derived session key
     * @param peerPublicKeyHex the peer's public key in hex format
     */
    fun putSessionKey(peerId: String, sessionKey: ByteArray, peerPublicKeyHex: String) {
        // Cleanup if cache is getting too large
        if (cache.size >= MAX_CACHE_SIZE) {
            cleanup()
        }

        cache[peerId] = SessionKeyInfo(
            sessionKey = sessionKey.copyOf(),
            peerPublicKeyHex = peerPublicKeyHex,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Get the session key for a peer.
     * @param peerId the peer's ID
     * @return SessionKeyInfo if session exists, null otherwise
     */
    fun getSessionKey(peerId: String): SessionKeyInfo? {
        val info = cache[peerId] ?: return null

        // Check if session has expired
        if (System.currentTimeMillis() - info.createdAt > MAX_SESSION_AGE_MS) {
            cache.remove(peerId)
            return null
        }

        return info
    }

    /**
     * Check if a session exists for a peer.
     * @param peerId the peer's ID
     * @return true if session exists and is valid
     */
    fun hasSession(peerId: String): Boolean {
        return getSessionKey(peerId) != null
    }

    /**
     * Validate that the peer's public key matches the cached one (TOFU check).
     * @param peerId the peer's ID
     * @param publicKeyHex the public key to validate
     * @return true if keys match or no session exists, false if TOFU violation
     */
    fun validatePeerPublicKey(peerId: String, publicKeyHex: String): Boolean {
        val info = cache[peerId] ?: return true // No session yet, OK
        return info.peerPublicKeyHex == publicKeyHex
    }

    /**
     * Clear session for a peer (e.g., on TOFU violation).
     * @param peerId the peer's ID
     */
    fun clearSession(peerId: String) {
        cache.remove(peerId)
    }

    /**
     * Clear all sessions (e.g., on identity reset).
     */
    fun clearAll() {
        cache.clear()
    }

    /**
     * Cleanup expired sessions.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - it.value.createdAt > MAX_SESSION_AGE_MS }
    }

    /**
     * Get current cache size (for testing/monitoring).
     */
    fun size(): Int = cache.size
}
