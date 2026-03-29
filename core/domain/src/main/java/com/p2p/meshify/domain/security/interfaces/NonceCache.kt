package com.p2p.meshify.domain.security.interfaces

/**
 * Nonce cache for replay attack protection.
 * Stores seen nonces with timestamps and rejects duplicates within the time window.
 */
interface NonceCache {
    /**
     * Attempt to add a nonce to the cache.
     * @return true if nonce was added (first time seen), false if already present (replay detected)
     */
    fun addIfAbsent(nonce: ByteArray): Boolean
    
    /**
     * Clean up expired nonces from the cache.
     * Should be called periodically or before addIfAbsent.
     */
    fun cleanup()
    
    /**
     * Clear all nonces from the cache.
     * Use with caution — only in tests or on explicit user reset.
     */
    fun clear()
}
