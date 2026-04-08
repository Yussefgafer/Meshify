package com.p2p.meshify.domain.security.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Unit tests for EcdhSessionManager — domain layer.
 *
 * Focus areas:
 * - Key pair generation validity
 * - Shared secret derivation correctness
 * - Session establishment from handshake (full round-trip)
 * - Session validation (key length, format)
 * - Error cases (invalid keys, malformed data, empty inputs)
 */
class EcdhSessionManagerTest {

    private lateinit var subject: EcdhSessionManager

    @Before
    fun setup() {
        subject = EcdhSessionManager()
    }

    // ============================================================================================
    // KEY PAIR GENERATION TESTS
    // ============================================================================================

    @Test
    fun `createEphemeralSession generates valid EC key pair`() {
        // When
        val session = subject.createEphemeralSession()

        // Then: Public key can be reconstructed
        val pubKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(session.ephemeralPublicKey))
        assertEquals("EC", pubKey.algorithm)

        // Then: Private key can be reconstructed
        val privKey = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(session.ephemeralPrivateKey))
        assertEquals("EC", privKey.algorithm)
    }

    @Test
    fun `createEphemeralSession generates non-zero keys and nonce`() {
        // When
        val session = subject.createEphemeralSession()

        // Then: Keys and nonce are non-empty
        assertTrue(session.ephemeralPublicKey.isNotEmpty())
        assertTrue(session.ephemeralPrivateKey.isNotEmpty())
        assertTrue(session.sessionNonce.isNotEmpty())
    }

    @Test
    fun `createEphemeralSession produces unique keys across multiple calls`() {
        // When
        val session1 = subject.createEphemeralSession()
        val session2 = subject.createEphemeralSession()

        // Then: All fields differ between sessions
        assertFalse(session1.ephemeralPublicKey.contentEquals(session2.ephemeralPublicKey))
        assertFalse(session1.ephemeralPrivateKey.contentEquals(session2.ephemeralPrivateKey))
        assertFalse(session1.sessionNonce.contentEquals(session2.sessionNonce))
    }

    @Test
    fun `generateEphemeralKeypair produces usable key pair`() {
        // When
        val keyPair = subject.generateEphemeralKeypair()

        // Then: Both keys are non-null and non-empty
        assertTrue(keyPair.public.encoded.isNotEmpty())
        assertTrue(keyPair.private.encoded.isNotEmpty())
        assertEquals("EC", keyPair.public.algorithm)
    }

    @Test
    fun `generateNonce produces exactly 16 bytes`() {
        // When
        val nonce = subject.generateNonce()

        // Then
        assertEquals(16, nonce.size)
    }

    @Test
    fun `generateNonce produces unique nonces`() {
        // When
        val nonces = List(50) { subject.generateNonce() }

        // Then: All nonces are unique
        for (i in nonces.indices) {
            for (j in i + 1 until nonces.size) {
                assertFalse(
                    "Nonce $i and $j should differ",
                    nonces[i].contentEquals(nonces[j])
                )
            }
        }
    }

    // ============================================================================================
    // SHARED SECRET DERIVATION TESTS
    // ============================================================================================

    @Test
    fun `both parties derive identical session key`() {
        // Given: Alice creates ephemeral session
        val aliceSession = subject.createEphemeralSession()

        // Given: Bob generates his own ephemeral keypair and nonce
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Bob derives session key (responder side)
        val bobSessionKey = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )

        // When: Alice finalizes session key (initiator side)
        val aliceSessionKey = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // Then: Both keys must be identical
        assertArrayEquals(
            "Alice and Bob must derive the same session key",
            bobSessionKey,
            aliceSessionKey
        )
    }

    @Test
    fun `deriveSessionKey produces 32-byte key for AES-256`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When
        val sessionKey = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )

        // Then
        assertEquals(32, sessionKey.size)
    }

    @Test
    fun `finalizeSessionKey produces 32-byte key for AES-256`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When
        val sessionKey = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // Then
        assertEquals(32, sessionKey.size)
    }

    @Test
    fun `HKDF is deterministic — same inputs yield same output`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Derive twice with same inputs
        val key1 = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )
        val key2 = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // Then
        assertArrayEquals("HKDF must be deterministic", key1, key2)
    }

    // ============================================================================================
    // SESSION ESTABLISHMENT FROM HANDSHAKE TESTS
    // ============================================================================================

    @Test
    fun `full handshake round-trip produces matching session keys`() {
        // Given: Alice initiates
        val aliceSession = subject.createEphemeralSession()

        // Given: Bob responds with his own ephemeral keypair
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Bob derives his session key from Alice's handshake
        val bobKey = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )

        // When: Alice finalizes from Bob's response
        val aliceKey = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // Then: Keys match
        assertArrayEquals(bobKey, aliceKey)
    }

    @Test
    fun `different peer ephemeral keys produce different session keys`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair1 = subject.generateEphemeralKeypair()
        val bobKeyPair2 = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When
        val key1 = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair1,
            myNonce = bobNonce
        )
        val key2 = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair2,
            myNonce = bobNonce
        )

        // Then
        assertFalse("Different keypairs must yield different session keys", key1.contentEquals(key2))
    }

    @Test
    fun `different responder nonces produce different session keys`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce1 = subject.generateNonce()
        val bobNonce2 = subject.generateNonce()

        // When
        val key1 = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce1
        )
        val key2 = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce2
        )

        // Then
        assertFalse("Different nonces must yield different session keys", key1.contentEquals(key2))
    }

    // ============================================================================================
    // SESSION VALIDATION TESTS
    // ============================================================================================

    @Test
    fun `zeroPrivateKey fills array with zeros`() {
        // Given
        val keyBytes = ByteArray(32) { 0x42 }

        // When
        subject.zeroPrivateKey(keyBytes)

        // Then
        assertTrue(keyBytes.all { it == 0.toByte() })
    }

    @Test
    fun `zeroPrivateKey handles empty array`() {
        // Given
        val keyBytes = ByteArray(0)

        // When + Then: Should not throw
        subject.zeroPrivateKey(keyBytes)
        assertEquals(0, keyBytes.size)
    }

    @Test
    fun `createEphemeralSession placeholder sessionKey is empty`() {
        // When
        val session = subject.createEphemeralSession()

        // Then
        assertTrue(
            "Session key must be empty placeholder before finalizeSessionKey",
            session.sessionKey.isEmpty()
        )
    }

    // ============================================================================================
    // ERROR CASES — INVALID KEYS
    // ============================================================================================

    @Test(expected = IllegalStateException::class)
    fun `finalizeSessionKey throws on empty private key`() {
        val aliceSession = subject.createEphemeralSession()

        subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralPrivateKey = ByteArray(0),
            myNonce = aliceSession.sessionNonce
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `finalizeSessionKey throws on garbage private key`() {
        val aliceSession = subject.createEphemeralSession()

        subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralPrivateKey = ByteArray(32) { 0xFF.toByte() },
            myNonce = aliceSession.sessionNonce
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `finalizeSessionKey throws on empty peer public key`() {
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = ByteArray(0),
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `finalizeSessionKey throws on malformed peer public key`() {
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = ByteArray(32) { 0xDE.toByte() },
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `deriveSessionKeyFromPeer throws on empty peer public key`() {
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = ByteArray(0),
            peerNonce = ByteArray(16),
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `deriveSessionKeyFromPeer throws on malformed peer public key`() {
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = ByteArray(16) { 0xAA.toByte() },
            peerNonce = ByteArray(16),
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )
    }

    // ============================================================================================
    // ERROR CASES — MALFORMED DATA
    // ============================================================================================

    @Test
    fun `tampered peer public key produces different session key`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Valid derivation
        val validKey = subject.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobKeyPair,
            myNonce = bobNonce
        )

        // When: Tampered public key (flip first byte)
        val tamperedPubKey = aliceSession.ephemeralPublicKey.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedKey = try {
            subject.deriveSessionKeyFromPeer(
                peerEphemeralPubKeyBytes = tamperedPubKey,
                peerNonce = aliceSession.sessionNonce,
                myEphemeralKeyPair = bobKeyPair,
                myNonce = bobNonce
            )
        } catch (e: Exception) {
            null
        }

        // Then: Either throws or produces different key
        if (tamperedKey != null) {
            assertFalse("Tampered key must differ", validKey.contentEquals(tamperedKey))
        }
    }

    @Test
    fun `tampered private key produces different session key`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Valid derivation
        val validKey = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // When: Tampered private key
        val tamperedPrivKey = aliceSession.ephemeralPrivateKey.copyOf().also {
            if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0x01).toByte()
        }
        val tamperedKey = try {
            subject.finalizeSessionKey(
                peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
                peerNonce = bobNonce,
                myEphemeralPrivateKey = tamperedPrivKey,
                myNonce = aliceSession.sessionNonce
            )
        } catch (e: Exception) {
            null
        }

        // Then
        if (tamperedKey != null) {
            assertFalse("Tampered private key must yield different session key", validKey.contentEquals(tamperedKey))
        }
    }

    @Test
    fun `swapped nonce order produces different session key`() {
        // Given
        val aliceSession = subject.createEphemeralSession()
        val bobKeyPair = subject.generateEphemeralKeypair()
        val bobNonce = subject.generateNonce()

        // When: Correct nonce order (initiator || responder)
        val correctKey = subject.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        // When: Swapped nonce order (responder || initiator) — should differ
        val swappedKey = try {
            subject.finalizeSessionKey(
                peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
                peerNonce = aliceSession.sessionNonce, // swapped
                myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
                myNonce = bobNonce // swapped
            )
        } catch (e: Exception) {
            null
        }

        // Then
        if (swappedKey != null) {
            assertFalse("Swapped nonce order must produce different key", correctKey.contentEquals(swappedKey))
        }
    }
}
