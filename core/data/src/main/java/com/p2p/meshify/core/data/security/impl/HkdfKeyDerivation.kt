package com.p2p.meshify.core.data.security.impl

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 key derivation (RFC 5869).
 * 
 * Used to derive cryptographically strong session keys from ECDH shared secrets.
 * 
 * Why HKDF?
 * - ECDH shared secrets have non-uniform distribution — HKDF extracts uniform keys
 * - HKDF allows key separation via "info" parameter (different keys for different purposes)
 * - HKDF is standardized and well-studied (RFC 5869)
 * 
 * Usage:
 * 1. Extract: PRK = HMAC-SHA256(salt, IKM)
 * 2. Expand: OKM = HMAC-SHA256(PRK, info + counter)
 */
object HkdfKeyDerivation {
    
    private const val HASH_LENGTH = 32 // SHA-256 output length in bytes
    
    /**
     * Full HKDF (RFC 5869) — Extract + Expand.
     *
     * Per RFC 5869 Section 3.1:
     * > If salt is not provided, it is set to a string of HashLen zeros.
     * > If caller explicitly passes empty byte array, we treat it as "not provided"
     * > and use default zeros.
     *
     * @param inputKeyMaterial raw shared secret from ECDH
     * @param salt optional salt (if empty or default, uses hash-length zeros)
     * @param info context/application-specific info (e.g., "meshify-v1-session")
     * @param outputLen desired output length in bytes (default: 32 for AES-256)
     * @return derived key
     */
    fun deriveKey(
        inputKeyMaterial: ByteArray,
        salt: ByteArray = ByteArray(HASH_LENGTH),
        info: ByteArray = ByteArray(0),
        outputLen: Int = HASH_LENGTH
    ): ByteArray {
        // FIX: Treat empty salt as "not provided" - use default zeros per RFC 5869
        val effectiveSalt = if (salt.isEmpty()) {
            ByteArray(HASH_LENGTH)  // 32 zeros for SHA-256
        } else {
            salt
        }

        // Step 1: Extract — PRK = HMAC-SHA256(salt, IKM)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)
        
        // Step 2: Expand — OKM = T(1) | T(2) | ... | T(N)
        val result = ByteArray(outputLen)
        var prev = ByteArray(0)
        var offset = 0
        var counter = 1
        
        while (offset < outputLen) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(counter.toByte())
            prev = mac.doFinal()
            
            val len = minOf(HASH_LENGTH, outputLen - offset)
            prev.copyInto(result, offset, 0, len)
            offset += len
            counter++
        }
        
        return result
    }
    
    /**
     * Derive separate send/recv keys for full-duplex communication.
     * Key separation ensures send key ≠ recv key even with same shared secret.
     * 
     * @param sharedSecret raw ECDH shared secret
     * @param sessionId unique session identifier (e.g., sorted peer IDs)
     * @return Pair(sendKey, recvKey) — each 32 bytes
     */
    fun deriveSessionKeys(sharedSecret: ByteArray, sessionId: String): Pair<ByteArray, ByteArray> {
        // Salt = SHA-256(sessionId) — binds keys to this specific session
        val salt = sha256(sessionId.toByteArray(Charsets.UTF_8))
        
        // Derive separate keys with different "info" strings
        val sendKey = deriveKey(sharedSecret, salt, "send".toByteArray(Charsets.UTF_8), 32)
        val recvKey = deriveKey(sharedSecret, salt, "recv".toByteArray(Charsets.UTF_8), 32)
        
        return Pair(sendKey, recvKey)
    }
    
    /**
     * Convenience function for session key derivation.
     * @param sharedSecret raw ECDH shared secret
     * @param salt salt for extraction
     * @param info info string for expansion (e.g., "meshify-v1-session")
     * @return 32-byte session key
     */
    fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray, info: String): ByteArray {
        return deriveKey(sharedSecret, salt, info.toByteArray(Charsets.UTF_8), 32)
    }
    
    private fun sha256(data: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)
}
