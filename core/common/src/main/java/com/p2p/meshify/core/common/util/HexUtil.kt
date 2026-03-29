package com.p2p.meshify.core.common.util

import java.security.SecureRandom

/**
 * Hex encoding/decoding utilities for cryptographic operations.
 */
object HexUtil {
    
    /**
     * Encode byte array to lowercase hex string.
     */
    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
    
    /**
     * Decode hex string to byte array.
     * @throws IllegalArgumentException if hex string is invalid
     */
    fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(length / 2) { i ->
            val substring = substring(i * 2, i * 2 + 2)
            substring.toInt(16).toByte()
        }
    }
    
    /**
     * Get first N bytes as hex string (for fingerprints).
     */
    fun toHexPrefix(bytes: ByteArray, count: Int = 4): String =
        bytes.take(count).joinToString("") { "%02x".format(it) }
    
    /**
     * Format as fingerprint with colons (e.g., "a1:b2:c3:d4").
     */
    fun toFingerprint(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02x".format(it).uppercase() }
    
    /**
     * Format as fingerprint with spaces (e.g., "A1 B2 C3 D4").
     */
    fun toFingerprintSpaced(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}
