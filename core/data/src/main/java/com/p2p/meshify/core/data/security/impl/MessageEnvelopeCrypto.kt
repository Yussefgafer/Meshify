package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.domain.security.interfaces.NonceCache
import com.p2p.meshify.domain.security.model.MessageEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Message encryption/decryption using AES-256-GCM.
 *
 * Simplified from Protocol V2.0 — removed ECDSA signatures and peer identity.
 * Messages are encrypted with AES-256-GCM using a shared session key.
 * Integrity is provided by the GCM authentication tag (128 bits).
 *
 * Encryption properties:
 * - Confidentiality: AES-256-GCM with 12-byte random nonce
 * - Integrity: GCM authentication tag
 * - Replay protection: Nonce cache + timestamp validation
 * - No authentication: Messages are not signed (plaintext identity)
 */
class MessageEnvelopeCrypto(
    private val replayCache: NonceCache
) {
    companion object {
        private const val REPLAY_WINDOW_MS = 5 * 60 * 1000L   // 5 minutes
        private const val FUTURE_TOLERANCE_MS = 5 * 60 * 1000L // 5 seconds future tolerance
        private const val MAX_MESSAGE_SIZE = 16 * 1024 * 1024   // 16MB hard cap
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val NONCE_LENGTH_BYTES = 12 // 12 bytes = 96 bits (same as IV for AES-GCM)
    }

    /**
     * Encrypt plaintext into MessageEnvelope.
     * @param plaintext raw message bytes
     * @param senderId sender's peer ID (UUID string)
     * @param recipientId peer ID of intended recipient
     * @param sessionKey 32-byte AES session key (derived via ECDH+HKDF)
     * @return encrypted MessageEnvelope
     * @throws SecurityException if encryption fails
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        senderId: String,
        recipientId: String,
        sessionKey: ByteArray
    ): MessageEnvelope = withContext(Dispatchers.IO) {
        if (plaintext.size > MAX_MESSAGE_SIZE) {
            throw SecurityException(
                "Message exceeds max size (${plaintext.size} > $MAX_MESSAGE_SIZE)"
            )
        }
        if (sessionKey.size != 32) {
            throw SecurityException(
                "Session key must be 32 bytes (AES-256), got ${sessionKey.size}"
            )
        }

        // Generate 12-byte nonce (used directly as IV for AES-GCM)
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val timestamp = System.currentTimeMillis()

        // AES-256-GCM encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce) // nonce = IV
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        // Build AAD with length-prefixed binary format
        val aad = buildAad(senderId, recipientId, nonce, timestamp)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = nonce, // IV is same as nonce
            ciphertext = ciphertext,
            signature = ByteArray(0) // No signature in simplified protocol
        )
    }

    /**
     * Decrypt MessageEnvelope to plaintext.
     * @param envelope encrypted message
     * @param sessionKey 32-byte AES session key
     * @return decrypted plaintext
     * @throws SecurityException on replay detection or decryption error
     */
    suspend fun decrypt(
        envelope: MessageEnvelope,
        sessionKey: ByteArray
    ): ByteArray = withContext(Dispatchers.IO) {
        if (sessionKey.size != 32) {
            throw SecurityException(
                "Session key must be 32 bytes (AES-256), got ${sessionKey.size}"
            )
        }

        // 1. Timestamp validation — reject stale or future messages
        val age = System.currentTimeMillis() - envelope.timestamp
        if (age > REPLAY_WINDOW_MS) {
            throw SecurityException(
                "Message too old: ${age}ms (max: ${REPLAY_WINDOW_MS}ms)"
            )
        }
        if (age < -FUTURE_TOLERANCE_MS) {
            throw SecurityException(
                "Message timestamp in future by ${-age}ms"
            )
        }

        // 2. Replay protection — reject duplicate nonces
        if (!replayCache.addIfAbsent(envelope.nonce)) {
            throw SecurityException("Replay detected: nonce already seen")
        }

        // 3. AES-GCM decryption with AAD
        val aad = buildAad(envelope.senderId, envelope.recipientId, envelope.nonce, envelope.timestamp)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.nonce) // nonce = IV
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        cipher.updateAAD(aad)

        // GCM tag verification is implicit — doFinal() throws AEADBadTagException if tampered
        return@withContext try {
            cipher.doFinal(envelope.ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw SecurityException(
                "Decryption failed: GCM tag mismatch. Message may be tampered or wrong key used."
            )
        }
    }

    /**
     * Build AAD with length-prefixed binary format to prevent delimiter attacks.
     *
     * Binary format:
     * [senderIdLen:2][senderId][recipientIdLen:2][recipientId]
     * [nonceLen:2][nonce][timestamp:8]
     *
     * @param senderId sender's peer ID (UUID string)
     * @param recipientId recipient's peer ID (UUID string)
     * @param nonce 12-byte nonce/IV
     * @param timestamp Unix timestamp in milliseconds
     * @return length-prefixed binary AAD
     */
    private fun buildAad(
        senderId: String,
        recipientId: String,
        nonce: ByteArray,
        timestamp: Long
    ): ByteArray {
        val senderBytes = senderId.toByteArray(Charsets.UTF_8)
        val recipientBytes = recipientId.toByteArray(Charsets.UTF_8)

        // Calculate total size: 2+len + 2+len + 2+nonce + 8
        val totalSize = 2 + senderBytes.size +
                        2 + recipientBytes.size +
                        2 + nonce.size +
                        8 // timestamp

        return ByteBuffer.allocate(totalSize).apply {
            // Sender ID (length-prefixed)
            putShort(senderBytes.size.toShort())
            put(senderBytes)

            // Recipient ID (length-prefixed)
            putShort(recipientBytes.size.toShort())
            put(recipientBytes)

            // Nonce (length-prefixed)
            putShort(nonce.size.toShort())
            put(nonce)

            // Timestamp (8 bytes, big-endian)
            putLong(timestamp)
        }.array()
    }
}
