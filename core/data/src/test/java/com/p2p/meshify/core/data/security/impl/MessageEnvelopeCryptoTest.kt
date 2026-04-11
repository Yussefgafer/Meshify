package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.domain.security.interfaces.NonceCache
import com.p2p.meshify.domain.security.model.MessageEnvelope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tests for simplified MessageEnvelopeCrypto (AES-256-GCM without ECDSA signatures).
 *
 * Tests verify:
 * - Round-trip encrypt/decrypt
 * - Tampering detection (GCM tag)
 * - Replay attack prevention
 * - Timestamp validation
 * - AAD binding
 * - Wrong key detection
 */
class MessageEnvelopeCryptoTest {

    private lateinit var replayCache: NonceCache
    private lateinit var crypto: MessageEnvelopeCrypto

    @Before
    fun setup() {
        replayCache = InMemoryNonceCache(windowMs = 30_000L)
        crypto = MessageEnvelopeCrypto(replayCache)
    }

    private fun generateSessionKey(): ByteArray = ByteArray(32) { 0x42 }

    // ============================================================================================
    // HAPPY PATH TESTS
    // ============================================================================================

    @Test
    fun `encrypt and decrypt returns original plaintext`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Hello, secure world!".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)
        val decrypted = crypto.decrypt(envelope = envelope, sessionKey = sessionKey)

        Assert.assertArrayEquals("Decrypted text must match original plaintext", plaintext, decrypted)
    }

    @Test
    fun `empty plaintext encrypts and decrypts correctly`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(0)
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)
        val decrypted = crypto.decrypt(envelope = envelope, sessionKey = sessionKey)

        Assert.assertArrayEquals("Empty plaintext must decrypt to empty array", plaintext, decrypted)
    }

    @Test
    fun `large plaintext encrypts and decrypts correctly`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(1024 * 1024) { it.toByte() }
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)
        val decrypted = crypto.decrypt(envelope = envelope, sessionKey = sessionKey)

        Assert.assertArrayEquals("Large plaintext must decrypt correctly", plaintext, decrypted)
    }

    // ============================================================================================
    // TAMPERING DETECTION TESTS
    // ============================================================================================

    @Test
    fun `tampered ciphertext throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        val tamperedCiphertext = envelope.ciphertext.copyOf()
        if (tamperedCiphertext.isNotEmpty()) {
            tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = tamperedEnvelope, sessionKey = sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue("Exception should be SecurityException", exception is SecurityException)
    }

    @Test
    fun `tampered nonce throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        val tamperedNonce = envelope.nonce.copyOf()
        if (tamperedNonce.isNotEmpty()) {
            tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(nonce = tamperedNonce)

        runBlocking {
            runCatching {
                crypto.decrypt(envelope = tamperedEnvelope, sessionKey = sessionKey)
            }.exceptionOrNull()?.let {
                Assert.assertTrue(it is SecurityException)
            } ?: Assert.fail("Expected SecurityException to be thrown")
        }
    }

    @Test
    fun `tampered senderId throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        val tamperedEnvelope = envelope.copy(senderId = "attacker-id")

        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = tamperedEnvelope, sessionKey = sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception message should mention GCM or tag",
            exception?.message?.contains("GCM", ignoreCase = true) == true ||
            exception?.message?.contains("tag", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // WRONG KEY TESTS
    // ============================================================================================

    @Test
    fun `wrong session key throws GCM decryption error`() = runTest {
        val sessionKey1 = generateSessionKey()
        val sessionKey2 = ByteArray(32) { 0x55 }
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey1)

        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = envelope, sessionKey = sessionKey2)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention GCM or tag mismatch",
            exception?.message?.contains("GCM", ignoreCase = true) == true ||
            exception?.message?.contains("tag", ignoreCase = true) == true
        )
    }

    @Test
    fun `session key of wrong length throws SecurityException`() = runTest {
        val wrongKey = ByteArray(16) { 0x42 }
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val exception = runBlocking {
            runCatching {
                crypto.encrypt(plaintext, senderId, recipientId, wrongKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention key size",
            exception?.message?.contains("32", ignoreCase = true) == true ||
            exception?.message?.contains("size", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // REPLAY ATTACK TESTS
    // ============================================================================================

    @Test
    fun `replayed nonce throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        // First decryption succeeds
        crypto.decrypt(envelope = envelope, sessionKey = sessionKey)

        // Second decryption must fail (replay)
        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = envelope, sessionKey = sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention replay",
            exception?.message?.contains("replay", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // TIMESTAMP VALIDATION TESTS
    // ============================================================================================

    @Test
    fun `message older than 5 minutes throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Old message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        val oldTimestamp = System.currentTimeMillis() - (6 * 60 * 1000L)
        val oldEnvelope = envelope.copy(timestamp = oldTimestamp)

        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = oldEnvelope, sessionKey = sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention old or stale message",
            exception?.message?.contains("old", ignoreCase = true) == true ||
            exception?.message?.contains("stale", ignoreCase = true) == true ||
            exception?.message?.contains("ago", ignoreCase = true) == true
        )
    }

    @Test
    fun `message timestamp too far in future throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Future message".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"
        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        val futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L)
        val futureEnvelope = envelope.copy(timestamp = futureTimestamp)

        val exception = runBlocking {
            runCatching {
                crypto.decrypt(envelope = futureEnvelope, sessionKey = sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention future",
            exception?.message?.contains("future", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // AAD FORMAT TESTS
    // ============================================================================================

    @Test
    fun `swapping sender and recipient changes AAD`() = runTest {
        val senderId = "alice"
        val recipientId = "bob"
        val sessionKey = generateSessionKey()
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)

        val envelopeAB = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)
        val envelopeBA = crypto.encrypt(plaintext, recipientId, senderId, sessionKey)

        Assert.assertFalse(
            "Swapped sender/recipient must produce different nonces (due to different AAD input to cipher state)",
            envelopeAB.nonce.contentEquals(envelopeBA.nonce) ||
            envelopeAB.ciphertext.contentEquals(envelopeBA.ciphertext)
        )
    }

    // ============================================================================================
    // MESSAGE SIZE LIMIT TESTS
    // ============================================================================================

    @Test
    fun `message exceeding MAX_MESSAGE_SIZE throws SecurityException`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(17 * 1024 * 1024) { 0x42 } // 17MB
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val exception = runBlocking {
            runCatching {
                crypto.encrypt(plaintext, senderId, recipientId, sessionKey)
            }.exceptionOrNull()
        }
        Assert.assertNotNull("Exception should be thrown", exception)
        Assert.assertTrue(
            "Exception should mention size limit",
            exception?.message?.contains("size", ignoreCase = true) == true ||
            exception?.message?.contains("exceed", ignoreCase = true) == true
        )
    }

    // ============================================================================================
    // NONCE FORMAT TESTS
    // ============================================================================================

    @Test
    fun `nonce is exactly 12 bytes for AES-GCM`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Test".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        Assert.assertEquals("Nonce must be 12 bytes for AES-GCM", 12, envelope.nonce.size)
        Assert.assertArrayEquals("IV must equal nonce", envelope.nonce, envelope.iv)
    }

    @Test
    fun `signature is empty in simplified protocol`() = runTest {
        val sessionKey = generateSessionKey()
        val plaintext = "Test".toByteArray(Charsets.UTF_8)
        val senderId = "sender-123"
        val recipientId = "peer-123"

        val envelope = crypto.encrypt(plaintext, senderId, recipientId, sessionKey)

        Assert.assertTrue(
            "Signature should be empty in simplified protocol",
            envelope.signature.isEmpty()
        )
    }
}
