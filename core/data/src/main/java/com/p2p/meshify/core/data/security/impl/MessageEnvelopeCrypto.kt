package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.domain.security.interfaces.NonceCache
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.domain.security.model.MessageEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for messages using AES-256-GCM + ECDSA signatures.
 * 
 * Security properties:
 * - Confidentiality: AES-256-GCM encryption
 * - Integrity: GCM authentication tag (128 bits)
 * - Authentication: ECDSA signature over envelope fields
 * - Replay protection: Nonce cache + timestamp validation
 * 
 * Encryption flow:
 * 1. Generate fresh 12-byte IV from SecureRandom
 * 2. Build AAD (senderId|recipientId|nonce|timestamp)
 * 3. AES-256-GCM encrypt with AAD
 * 4. ECDSA sign over AAD + ciphertext
 * 5. Build MessageEnvelope
 * 
 * Decryption flow:
 * 1. Validate timestamp (±30 second window)
 * 2. Check nonce in NonceCache (reject if seen)
 * 3. Verify ECDSA signature FIRST (fail-fast on tampering)
 * 4. AES-256-GCM decrypt with AAD
 * 5. GCM tag verification is implicit in doFinal()
 */
class MessageEnvelopeCrypto(
    private val peerIdentity: PeerIdentityRepository,
    private val replayCache: NonceCache
) {
    companion object {
        private const val REPLAY_WINDOW_MS = 30_000L   // 30 seconds
        private const val FUTURE_TOLERANCE_MS = 5_000L // 5 seconds future tolerance
        private const val MAX_MESSAGE_SIZE = 16 * 1024 * 1024   // 16MB hard cap
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }
    
    /**
     * Encrypt plaintext into MessageEnvelope.
     * @param plaintext raw message bytes
     * @param recipientId peer ID of intended recipient
     * @param sessionKey 32-byte AES session key (derived via ECDH+HKDF)
     * @return encrypted MessageEnvelope
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        recipientId: String,
        sessionKey: ByteArray
    ): MessageEnvelope = withContext(Dispatchers.IO) {
        require(plaintext.size <= MAX_MESSAGE_SIZE) { 
            "Message exceeds max size (${plaintext.size} > $MAX_MESSAGE_SIZE)" 
        }
        require(sessionKey.size == 32) { 
            "Session key must be 32 bytes (AES-256), got ${sessionKey.size}" 
        }

        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val timestamp = System.currentTimeMillis()
        val senderId = peerIdentity.getPeerId()

        // AES-256-GCM encryption
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        // Bind ciphertext to sender+recipient+nonce via AAD
        val ad = buildAad(senderId, recipientId, nonce, timestamp)
        cipher.updateAAD(ad)
        val ciphertext = cipher.doFinal(plaintext)

        // Sign the envelope fields (AAD + ciphertext)
        val toSign = ad + ciphertext
        val signature = peerIdentity.signChallenge(toSign)

        MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = iv,
            ciphertext = ciphertext,
            signature = signature
        )
    }
    
    /**
     * Decrypt MessageEnvelope to plaintext.
     * @param envelope encrypted message
     * @param senderPublicKeyBytes sender's DER-encoded public key
     * @param sessionKey 32-byte AES session key
     * @return decrypted plaintext
     * @throws SecurityException on signature verification failure or replay detection
     */
    suspend fun decrypt(
        envelope: MessageEnvelope,
        senderPublicKeyBytes: ByteArray,
        sessionKey: ByteArray
    ): ByteArray = withContext(Dispatchers.IO) {
        require(sessionKey.size == 32) { 
            "Session key must be 32 bytes (AES-256), got ${sessionKey.size}" 
        }
        
        // 1. Timestamp validation — reject stale or future messages
        val age = System.currentTimeMillis() - envelope.timestamp
        require(age <= REPLAY_WINDOW_MS) { 
            "Message too old: ${age}ms (max: ${REPLAY_WINDOW_MS}ms)" 
        }
        require(age >= -FUTURE_TOLERANCE_MS) { 
            "Message timestamp in future by ${-age}ms" 
        }
        
        // 2. Replay protection — reject duplicate nonces
        require(replayCache.addIfAbsent(envelope.nonce)) { 
            "Replay detected: nonce already seen" 
        }

        // 3. Signature verification BEFORE decryption (fail-fast on tampered envelopes)
        val toVerify = buildAad(envelope.senderId, envelope.recipientId, envelope.nonce, envelope.timestamp) + envelope.ciphertext
        require(peerIdentity.verifySignature(senderPublicKeyBytes, toVerify, envelope.signature)) {
            "Signature verification failed — possible impersonation or tampering"
        }

        // 4. AES-GCM decryption with AAD
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv))
        val ad = buildAad(envelope.senderId, envelope.recipientId, envelope.nonce, envelope.timestamp)
        cipher.updateAAD(ad)
        
        // GCM tag verification is implicit — doFinal() throws AEADBadTagException if tampered
        return@withContext cipher.doFinal(envelope.ciphertext)
    }
    
    private fun buildAad(senderId: String, recipientId: String, nonce: ByteArray, timestamp: Long): ByteArray {
        val nonceHex = HexUtil.toHex(nonce)
        return "$senderId|$recipientId|$nonceHex|$timestamp".toByteArray(Charsets.UTF_8)
    }
}
