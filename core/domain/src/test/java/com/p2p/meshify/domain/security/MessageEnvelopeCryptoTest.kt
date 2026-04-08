package com.p2p.meshify.domain.security

import com.p2p.meshify.domain.security.model.MessageEnvelope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Domain-layer tests for message encryption/decryption contract.
 *
 * Tests the AES-256-GCM envelope encryption pattern using domain models
 * without depending on core:data implementation classes.
 *
 * Covers:
 * - Encrypt then decrypt returns original payload
 * - Different keys produce different ciphertexts
 * - Tampered ciphertext fails to decrypt
 * - Empty payload handling
 * - Large payload handling
 */
class MessageEnvelopeCryptoTest {

    private val keyPair: KeyPair

    init {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        keyPair = kpg.generateKeyPair()
    }

    private fun encrypt(
        plaintext: ByteArray,
        senderId: String,
        recipientId: String,
        sessionKey: ByteArray
    ): MessageEnvelope {
        val nonce = ByteArray(12) { it.toByte() }
        val timestamp = System.currentTimeMillis()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val aad = buildAad(senderId, recipientId, nonce, timestamp)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        val toSign = aad + ciphertext
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(toSign)
        val signature = sig.sign()

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = nonce,
            ciphertext = ciphertext,
            signature = signature
        )
    }

    private fun decrypt(
        envelope: MessageEnvelope,
        sessionKey: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(128, envelope.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val aad = buildAad(envelope.senderId, envelope.recipientId, envelope.nonce, envelope.timestamp)
        cipher.updateAAD(aad)

        return cipher.doFinal(envelope.ciphertext)
    }

    private fun verifySignature(envelope: MessageEnvelope): Boolean {
        return try {
            val aad = buildAad(envelope.senderId, envelope.recipientId, envelope.nonce, envelope.timestamp)
            val toVerify = aad + envelope.ciphertext
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(keyPair.public)
            sig.update(toVerify)
            sig.verify(envelope.signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun buildAad(
        senderId: String,
        recipientId: String,
        nonce: ByteArray,
        timestamp: Long
    ): ByteArray {
        val senderBytes = senderId.toByteArray(Charsets.UTF_8)
        val recipientBytes = recipientId.toByteArray(Charsets.UTF_8)
        val totalSize = 2 + senderBytes.size + 2 + recipientBytes.size + 2 + nonce.size + 8

        return java.nio.ByteBuffer.allocate(totalSize).apply {
            putShort(senderBytes.size.toShort())
            put(senderBytes)
            putShort(recipientBytes.size.toShort())
            put(recipientBytes)
            putShort(nonce.size.toShort())
            put(nonce)
            putLong(timestamp)
        }.array()
    }

    private fun generateSessionKey(): ByteArray = ByteArray(32) { 0x42 }

    // ============================================================================================
    // ENCRYPT THEN DECRYPT — ROUND TRIP
    // ============================================================================================

    @Test
    fun `encrypt then decrypt returns original payload`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Hello, secure world!".toByteArray(Charsets.UTF_8)

        // When
        val envelope = encrypt(plaintext, "sender-1", "recipient-1", sessionKey)
        val decrypted = decrypt(envelope, sessionKey)

        // Then
        assertArrayEquals("Decrypted must match original plaintext", plaintext, decrypted)
    }

    @Test
    fun `encrypt then decrypt with binary payload`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(256) { it.toByte() }

        // When
        val envelope = encrypt(plaintext, "sender-2", "recipient-2", sessionKey)
        val decrypted = decrypt(envelope, sessionKey)

        // Then
        assertArrayEquals(plaintext, decrypted)
    }

    // ============================================================================================
    // EMPTY PAYLOAD HANDLING
    // ============================================================================================

    @Test
    fun `empty payload encrypts and decrypts correctly`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(0)

        // When
        val envelope = encrypt(plaintext, "sender-empty", "recipient-empty", sessionKey)
        val decrypted = decrypt(envelope, sessionKey)

        // Then
        assertArrayEquals("Empty payload must round-trip correctly", plaintext, decrypted)
        assertEquals(0, decrypted.size)
    }

    // ============================================================================================
    // LARGE PAYLOAD HANDLING
    // ============================================================================================

    @Test
    fun `large payload encrypts and decrypts correctly`() {
        // Given: 1MB payload
        val sessionKey = generateSessionKey()
        val plaintext = ByteArray(1024 * 1024) { (it % 256).toByte() }

        // When
        val envelope = encrypt(plaintext, "sender-large", "recipient-large", sessionKey)
        val decrypted = decrypt(envelope, sessionKey)

        // Then
        assertArrayEquals("Large payload must round-trip correctly", plaintext, decrypted)
    }

    // ============================================================================================
    // DIFFERENT KEYS PRODUCE DIFFERENT CIPHERTEXTS
    // ============================================================================================

    @Test
    fun `different session keys produce different ciphertexts`() {
        // Given
        val key1 = ByteArray(32) { 0x11 }
        val key2 = ByteArray(32) { 0x22 }
        val plaintext = "Same plaintext".toByteArray(Charsets.UTF_8)

        // When
        val envelope1 = encrypt(plaintext, "sender-diff", "recipient-diff", key1)
        val envelope2 = encrypt(plaintext, "sender-diff", "recipient-diff", key2)

        // Then
        assertFalse(
            "Different keys must produce different ciphertexts",
            envelope1.ciphertext.contentEquals(envelope2.ciphertext)
        )
    }

    @Test
    fun `same key with different nonces produces different ciphertexts`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Repeated message".toByteArray(Charsets.UTF_8)

        // When: Two encryptions with different nonces
        val nonce1 = ByteArray(12) { 0x01 }
        val envelope1 = encryptWithNonce(plaintext, "sender-nonce", "recipient-nonce", sessionKey, nonce1)

        val nonce2 = ByteArray(12) { 0x02 }
        val envelope2 = encryptWithNonce(plaintext, "sender-nonce", "recipient-nonce", sessionKey, nonce2)

        // Then
        assertFalse(
            "Different nonces must produce different ciphertexts",
            envelope1.ciphertext.contentEquals(envelope2.ciphertext)
        )
    }

    private fun encryptWithNonce(
        plaintext: ByteArray,
        senderId: String,
        recipientId: String,
        sessionKey: ByteArray,
        nonce: ByteArray
    ): MessageEnvelope {
        val timestamp = System.currentTimeMillis()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val aad = buildAad(senderId, recipientId, nonce, timestamp)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        val toSign = aad + ciphertext
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(toSign)
        val signature = sig.sign()

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = nonce,
            ciphertext = ciphertext,
            signature = signature
        )
    }

    // ============================================================================================
    // TAMPERED CIPHERTEXT FAILS TO DECRYPT
    // ============================================================================================

    @Test
    fun `tampered ciphertext throws AEADBadTagException`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Secret data".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-tamper", "recipient-tamper", sessionKey)

        // When: Flip one bit in ciphertext
        val tamperedCiphertext = envelope.ciphertext.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        // Then
        val thrown = runCatching { decrypt(tamperedEnvelope, sessionKey) }.exceptionOrNull()
        assertNotNull("Tampered ciphertext must throw", thrown)
    }

    @Test
    fun `tampered nonce causes decryption failure`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Secret data".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-nt", "recipient-nt", sessionKey)

        // When: Flip one bit in nonce
        val tamperedNonce = envelope.nonce.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(nonce = tamperedNonce, iv = tamperedNonce)

        // Then
        val thrown = runCatching { decrypt(tamperedEnvelope, sessionKey) }.exceptionOrNull()
        assertNotNull("Tampered nonce must throw", thrown)
    }

    @Test
    fun `wrong session key causes decryption failure`() {
        // Given
        val correctKey = generateSessionKey()
        val wrongKey = ByteArray(32) { 0x99.toByte() }
        val plaintext = "Secret data".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-wk", "recipient-wk", correctKey)

        // When
        val thrown = runCatching { decrypt(envelope, wrongKey) }.exceptionOrNull()
        assertNotNull("Wrong key must throw", thrown)
    }

    // ============================================================================================
    // SIGNATURE VERIFICATION
    // ============================================================================================

    @Test
    fun `valid envelope passes signature verification`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Sign me".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-sig", "recipient-sig", sessionKey)

        // When + Then
        assertTrue("Valid envelope must pass signature verification", verifySignature(envelope))
    }

    @Test
    fun `tampered ciphertext fails signature verification`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Sign me".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-sig2", "recipient-sig2", sessionKey)

        // When
        val tamperedCiphertext = envelope.ciphertext.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        // Then
        assertFalse("Tampered ciphertext must fail signature verification", verifySignature(tamperedEnvelope))
    }

    @Test
    fun `tampered senderId fails signature verification`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Sign me".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-sig3", "recipient-sig3", sessionKey)

        // When
        val tamperedEnvelope = envelope.copy(senderId = "attacker")

        // Then
        assertFalse("Tampered senderId must fail signature verification", verifySignature(tamperedEnvelope))
    }

    @Test
    fun `tampered signature is detected`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Sign me".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-sig4", "recipient-sig4", sessionKey)

        // When
        val tamperedSignature = envelope.signature.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedEnvelope = envelope.copy(signature = tamperedSignature)

        // Then
        assertFalse("Tampered signature must fail verification", verifySignature(tamperedEnvelope))
    }

    // ============================================================================================
    // ENVELOPE MODEL INTEGRITY
    // ============================================================================================

    @Test
    fun `envelope equals is reflexive`() {
        // Given
        val sessionKey = generateSessionKey()
        val plaintext = "Equality test".toByteArray(Charsets.UTF_8)
        val envelope = encrypt(plaintext, "sender-eq", "recipient-eq", sessionKey)

        // Then
        assertEquals(envelope, envelope)
    }

    @Test
    fun `envelope equals with same content`() {
        // Given
        val nonce = ByteArray(12) { 0x42 }
        val timestamp = System.currentTimeMillis()
        val sessionKey = generateSessionKey()

        val aad = buildAad("sender-eq2", "recipient-eq2", nonce, timestamp)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(sessionKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal("test".toByteArray(Charsets.UTF_8))
        val toSign = aad + ciphertext
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(toSign)
        val signature = sig.sign()

        val e1 = MessageEnvelope("sender-eq2", "recipient-eq2", nonce, timestamp, nonce, ciphertext, signature)
        val e2 = MessageEnvelope("sender-eq2", "recipient-eq2", nonce, timestamp, nonce, ciphertext, signature)

        // Then
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `envelope not equals with different ciphertext`() {
        // Given
        val nonce = ByteArray(12) { 0x42 }
        val timestamp = System.currentTimeMillis()

        val e1 = MessageEnvelope("sender", "recipient", nonce, timestamp, nonce, ByteArray(10) { 0x01 }, ByteArray(10))
        val e2 = MessageEnvelope("sender", "recipient", nonce, timestamp, nonce, ByteArray(10) { 0x02 }, ByteArray(10))

        // Then
        assertTrue("Envelopes with different ciphertext should not be equal", e1 != e2)
    }

    @Test
    fun `envelope hashCode is consistent`() {
        // Given
        val nonce = ByteArray(12) { 0x42 }
        val timestamp = 12345L
        val data = ByteArray(10) { 0x01 }

        val envelope = MessageEnvelope("sender-hc", "recipient-hc", nonce, timestamp, nonce, data, data)

        // Then
        assertEquals(envelope.hashCode(), envelope.hashCode())
    }
}
