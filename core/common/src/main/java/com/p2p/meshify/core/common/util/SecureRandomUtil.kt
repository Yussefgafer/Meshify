package com.p2p.meshify.core.common.util

import java.security.SecureRandom

/**
 * Secure random number generator for cryptographic operations.
 */
object SecureRandomUtil {
    
    private val secureRandom = SecureRandom()
    
    /**
     * Generate random bytes.
     */
    fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { secureRandom.nextBytes(it) }
    
    /**
     * Generate random 16-byte nonce.
     */
    fun randomNonce(): ByteArray = randomBytes(16)
    
    /**
     * Generate random 12-byte IV (for AES-GCM).
     */
    fun randomIv(): ByteArray = randomBytes(12)
    
    /**
     * Generate random 32-byte salt (for HKDF).
     */
    fun randomSalt(): ByteArray = randomBytes(32)
    
    /**
     * Generate random challenge (for handshake).
     */
    fun randomChallenge(): ByteArray = randomBytes(32)
}
