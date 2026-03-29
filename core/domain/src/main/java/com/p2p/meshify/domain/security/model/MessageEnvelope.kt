package com.p2p.meshify.domain.security.model

/**
 * Encrypted message envelope — wraps plaintext payload with encryption, signature, and replay protection.
 * 
 * Structure:
 * - senderId: who sent this message
 * - recipientId: who this message is for
 * - nonce: unique per-message value (prevents replay)
 * - timestamp: when message was created
 * - iv: initialization vector for AES-GCM (12 bytes, random per message)
 * - ciphertext: encrypted payload (AES-256-GCM)
 * - signature: ECDSA signature over all fields (authenticates sender)
 */
data class MessageEnvelope(
    val senderId: String,
    val recipientId: String,
    val nonce: ByteArray,
    val timestamp: Long,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageEnvelope
        return senderId == other.senderId &&
                recipientId == other.recipientId &&
                nonce.contentEquals(other.nonce) &&
                timestamp == other.timestamp &&
                iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext) &&
                signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
