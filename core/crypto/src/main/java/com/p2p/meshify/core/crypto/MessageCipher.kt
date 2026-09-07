package com.p2p.meshify.core.crypto

sealed class EncryptResult {
    data class Success(val ciphertext: ByteArray) : EncryptResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int = ciphertext.contentHashCode()
    }

    /**
     * Waiting timeout expired without receiving recipient's public key.
     * Caller should fallback to offline queueing / retry.
     */
    data object PublicKeyUnavailable : EncryptResult()

    /**
     * Peer explicitly signaled it does not support encryption (e.g. handshake without public key).
     * Caller must not send automatically; requires explicit unencrypted user action.
     */
    data object PeerDoesNotSupportEncryption : EncryptResult()
}

interface MessageCipher {
    /** Encrypts plaintext intended for a specific peer using their public key */
    suspend fun encryptFor(peerId: String, plaintext: ByteArray): EncryptResult

    /** Decrypts incoming ciphertext intended for this device using our private key */
    fun decrypt(ciphertext: ByteArray): ByteArray

    /** Returns this device's public key as a Base64-encoded JSON string, for sharing in Handshake */
    fun getPublicKeyBase64(): String
}
