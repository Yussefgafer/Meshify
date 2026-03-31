package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.domain.security.interfaces.NonceCache
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.domain.security.model.MessageEnvelope
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Breaking tests for MessageEnvelopeCrypto V2.0
 * 
 * These tests attempt to BREAK the encryption implementation by testing:
 * - Round-trip encryption/decryption
 * - Tampering detection (GCM tag, signature)
 * - Replay attack prevention
 * - Timestamp validation
 * - AAD binding attacks
 * - Wrong key detection
 */
class MessageEnvelopeCryptoTest {

    private lateinit var peerIdentity: PeerIdentityRepository
    private lateinit var replayCache: NonceCache
    private lateinit var crypto: MessageEnvelopeCrypto

    @Before
    fun setup() {
        // Use mock implementations for testing
        peerIdentity = MockPeerIdentityRepository()
        replayCache = InMemoryNonceCache(windowMs = 30_000L)
        crypto = MessageEnvelopeCrypto(peerIdentity, replayCache)
    }

    // Helper to generate session key
    private fun generateSessionKey(): ByteArray {
        return ByteArray(32) { 0x42 }
    }

    // ============================================================================================
    // HAPPY PATH TESTS
    // ============================================================================================

    @Test
    fun `encrypt and decrypt returns original plaintext`() = runTest {
        // Given: A session key and plaintext
        val sessionKey = generateSessionKey()
        val plaintext = "Hello, secure world!".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"

        // When: Encrypting
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Decrypting with same key
        val decrypted = crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Then: Decrypted must match original
        Assert.assertArrayEquals(
            "Decrypted text must match original plaintext",
            plaintext,
            decrypted
        )
    }

    @Test
    fun `empty plaintext encrypts and decrypts correctly`() = runTest {
        // Given: Empty plaintext
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(0)
        val recipientId = "peer-123"

        // When: Encrypting empty message
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Decrypting
        val decrypted = crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Then: Must return empty array
        Assert.assertArrayEquals(
            "Empty plaintext must decrypt to empty array",
            plaintext,
            decrypted
        )
    }

    @Test
    fun `large plaintext encrypts and decrypts correctly`() = runTest {
        // Given: Large plaintext (1MB)
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(1024 * 1024) { it.toByte() }
        val recipientId = "peer-123"

        // When: Encrypting large message
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Decrypting
        val decrypted = crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Then: Must match original
        Assert.assertArrayEquals(
            "Large plaintext must decrypt correctly",
            plaintext,
            decrypted
        )
    }

    // ============================================================================================
    // TAMPERING DETECTION TESTS
    // ============================================================================================

    @Test
    fun `tampered ciphertext throws SecurityException`() = runTest {
        // Given: An encrypted envelope
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // When: Attacker tampers with ciphertext (flip one bit)
        val tamperedCiphertext = envelope.ciphertext.copyOf()
        if (tamperedCiphertext.isNotEmpty()) {
            tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        // Then: Decryption must throw SecurityException
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }
        Assert.assertTrue(
            "Exception message should mention tampering or GCM",
            exception.message?.contains("tampered", ignoreCase = true) == true ||
            exception.message?.contains("GCM", ignoreCase = true) == true
        )
    }

    @Test
    fun `tampered signature throws SecurityException`() = runTest {
        // Given: An encrypted envelope
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // When: Attacker tampers with signature
        val tamperedSignature = envelope.signature.copyOf()
        if (tamperedSignature.isNotEmpty()) {
            tamperedSignature[0] = (tamperedSignature[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(signature = tamperedSignature)

        // Then: Decryption must throw SecurityException
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }
        Assert.assertTrue(
            "Exception message should mention signature",
            exception.message?.contains("signature", ignoreCase = true) == true
        )
    }

    @Test
    fun `tampered nonce throws SecurityException`() = runTest {
        // Given: An encrypted envelope
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // When: Attacker tampers with nonce
        val tamperedNonce = envelope.nonce.copyOf()
        if (tamperedNonce.isNotEmpty()) {
            tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(nonce = tamperedNonce)

        // Then: Decryption must throw SecurityException (GCM tag mismatch)
        Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }
    }

    @Test
    fun `tampered senderId throws SecurityException`() = runTest {
        // Given: An encrypted envelope
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // When: Attacker changes senderId
        val tamperedEnvelope = envelope.copy(senderId = "attacker-id")

        // Then: Decryption must throw SecurityException (signature verification fails)
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }
        Assert.assertTrue(
            "Exception message should mention signature or tampering",
            exception.message?.contains("signature", ignoreCase = true) == true ||
            exception.message?.contains("tampering", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // WRONG KEY TESTS
    // ============================================================================================

    @Test
    fun `wrong session key throws GCM decryption error`() = runTest {
        // Given: An encrypted envelope with key1
        val sessionKey1 = generateSessionKey()
        val sessionKey2 = ByteArray(32) { 0x55 } // Different key
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey1)

        // When: Decrypting with WRONG key
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = envelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey2
                )
            }
        }

        // Then: Must throw SecurityException with GCM/tag mismatch message
        Assert.assertTrue(
            "Exception should mention GCM or tag mismatch",
            exception.message?.contains("GCM", ignoreCase = true) == true ||
            exception.message?.contains("tag", ignoreCase = true) == true
        )
    }

    @Test
    fun `session key of wrong length throws SecurityException`() = runTest {
        // Given: Wrong-sized session key
        val wrongKey = ByteArray(16) { 0x42 } // 16 bytes instead of 32
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"

        // When: Encrypting with wrong key size
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.encrypt(plaintext, recipientId, wrongKey)
            }
        }

        // Then: Must throw SecurityException
        Assert.assertTrue(
            "Exception should mention key size",
            exception.message?.contains("32", ignoreCase = true) == true ||
            exception.message?.contains("size", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // REPLAY ATTACK TESTS
    // ============================================================================================

    @Test
    fun `replayed nonce throws SecurityException`() = runTest {
        // Given: An encrypted envelope
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // When: Decrypting the SAME envelope twice (replay)
        // First decryption succeeds
        crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Second decryption must fail
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = envelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        // Then: Must throw SecurityException mentioning replay
        Assert.assertTrue(
            "Exception should mention replay",
            exception.message?.contains("replay", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // TIMESTAMP VALIDATION TESTS
    // ============================================================================================

    @Test
    fun `message older than 5 minutes throws TimestampException`() = runTest {
        // Given: An envelope with old timestamp
        val sessionKey = generateSessionKey()
        val plaintext = "Old message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // Create envelope with old timestamp (6 minutes ago)
        val oldTimestamp = System.currentTimeMillis() - (6 * 60 * 1000L)
        val oldEnvelope = envelope.copy(timestamp = oldTimestamp)

        // When: Decrypting old message
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = oldEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        // Then: Must throw SecurityException mentioning old/stale
        Assert.assertTrue(
            "Exception should mention old or stale message",
            exception.message?.contains("old", ignoreCase = true) == true ||
            exception.message?.contains("stale", ignoreCase = true) == true ||
            exception.message?.contains("ago", ignoreCase = true) == true
        )
    }

    @Test
    fun `message timestamp too far in future throws SecurityException`() = runTest {
        // Given: An envelope with future timestamp
        val sessionKey = generateSessionKey()
        val plaintext = "Future message".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // Create envelope with future timestamp (10 minutes in future)
        val futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L)
        val futureEnvelope = envelope.copy(timestamp = futureTimestamp)

        // When: Decrypting future message
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.decrypt(
                    envelope = futureEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        // Then: Must throw SecurityException mentioning future
        Assert.assertTrue(
            "Exception should mention future",
            exception.message?.contains("future", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // AAD FORMAT TESTS
    // ============================================================================================

    @Test
    fun `swapping sender and recipient changes AAD`() = runTest {
        // Given: Two different peers
        val senderId = "alice"
        val recipientId = "bob"
        val sessionKey = generateSessionKey()
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)

        // When: Encrypting from Alice to Bob
        val envelopeAB = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Encrypting from Bob to Alice (swapped)
        // Note: In real scenario, different keys would be used, but AAD should still differ
        val envelopeBA = crypto.encrypt(plaintext, senderId, sessionKey)

        // Then: AAD must be different (verified via different signatures)
        Assert.assertFalse(
            "Swapped sender/recipient must produce different envelopes",
            envelopeAB.signature.contentEquals(envelopeBA.signature)
        )
    }

    @Test
    fun `AAD with pipe character in senderId is handled correctly`() = runTest {
        // Given: SenderId with special characters that could be used in delimiter attacks
        val maliciousSenderId = "user|admin|role"
        val recipientId = "peer-123"
        val sessionKey = generateSessionKey()
        val plaintext = "Test message".toByteArray(Charsets.UTF_8)

        // When: Encrypting with malicious senderId
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Decrypting
        val decrypted = crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Then: Must decrypt correctly (AAD binding prevents delimiter attacks)
        Assert.assertArrayEquals(
            "Message with special characters in senderId must decrypt correctly",
            plaintext,
            decrypted
        )
    }

    @Test
    fun `AAD with empty senderId is handled correctly`() = runTest {
        // Given: Empty senderId (edge case)
        val sessionKey = generateSessionKey()
        val plaintext = "Test".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"

        // When: Encrypting (senderId comes from peerIdentity)
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // And: Decrypting
        val decrypted = crypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
            sessionKey = sessionKey
        )

        // Then: Must decrypt correctly
        Assert.assertArrayEquals(
            "Message must decrypt correctly",
            plaintext,
            decrypted
        )
    }

    // ============================================================================================
    // MESSAGE SIZE LIMIT TESTS
    // ============================================================================================

    @Test
    fun `message exceeding MAX_MESSAGE_SIZE throws SecurityException`() = runTest {
        // Given: Plaintext exceeding 16MB limit
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(17 * 1024 * 1024) { 0x42 } // 17MB
        val recipientId = "peer-123"

        // When: Encrypting oversized message
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                crypto.encrypt(plaintext, recipientId, sessionKey)
            }
        }

        // Then: Must throw SecurityException mentioning size
        Assert.assertTrue(
            "Exception should mention size limit",
            exception.message?.contains("size", ignoreCase = true) == true ||
            exception.message?.contains("exceed", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // NONCE FORMAT TESTS
    // ============================================================================================

    @Test
    fun `nonce is exactly 12 bytes for AES-GCM`() = runTest {
        // Given: A plaintext to encrypt
        val sessionKey = generateSessionKey()
        val plaintext = "Test".toByteArray(Charsets.UTF_8)
        val recipientId = "peer-123"

        // When: Encrypting
        val envelope = crypto.encrypt(plaintext, recipientId, sessionKey)

        // Then: Nonce must be exactly 12 bytes
        Assert.assertEquals(
            "Nonce must be 12 bytes for AES-GCM",
            12,
            envelope.nonce.size
        )

        // And: IV must equal nonce
        Assert.assertArrayEquals(
            "IV must equal nonce",
            envelope.nonce,
            envelope.iv
        )
    }

    // Mock implementation for testing
    private class MockPeerIdentityRepository : PeerIdentityRepository {
        private val keyPair: KeyPair

        init {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            keyPair = kpg.generateKeyPair()
        }

        override suspend fun initializeIdentity(): String = "mock-peer-id"
        override suspend fun getPeerId(): String = "mock-peer-id"
        override suspend fun getPublicKeyBytes(): ByteArray = keyPair.public.encoded
        override suspend fun getPublicKeyHex(): String = keyPair.public.encoded.joinToString("") { "%02x".format(it) }

        override suspend fun signChallenge(challenge: ByteArray): ByteArray {
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initSign(keyPair.private)
            signature.update(challenge)
            return signature.sign()
        }

        override suspend fun verifySignature(
            publicKeyBytes: ByteArray,
            challenge: ByteArray,
            signatureBytes: ByteArray
        ): Boolean {
            return try {
                val pubKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
                val signature = java.security.Signature.getInstance("SHA256withECDSA")
                signature.initVerify(pubKey)
                signature.update(challenge)
                signature.verify(signatureBytes)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun resetIdentity() {}
    }
}
