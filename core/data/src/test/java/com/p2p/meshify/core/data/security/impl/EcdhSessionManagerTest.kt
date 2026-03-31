package com.p2p.meshify.core.data.security.impl

import org.junit.Assert
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.SecureRandom

/**
 * Breaking tests for EcdhSessionManager V2.0
 */
class EcdhSessionManagerTest {

    private val sessionManager = EcdhSessionManager()

    private fun generateIdentityKeypair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        return kpg.generateKeyPair()
    }

    @Test
    fun `both parties derive same session key`() {
        val aliceIdentity = generateIdentityKeypair()
        val bobIdentity = generateIdentityKeypair()

        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)

        val bobSessionKey = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobIdentity,
            myNonce = aliceSession.sessionNonce
        )

        val aliceFinalizedKey = sessionManager.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobIdentity.public.encoded,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        Assert.assertArrayEquals(
            "Session keys must match after key exchange",
            bobSessionKey,
            aliceFinalizedKey
        )
    }

    @Test
    fun `session key is 32 bytes for AES-256`() {
        val peerIdentity = generateIdentityKeypair()
        val session = sessionManager.createEphemeralSession(peerIdentity.public.encoded)

        Assert.assertEquals(
            "Session key must be 32 bytes for AES-256",
            32,
            session.sessionKey.size
        )
    }

    @Test(expected = Exception::class)
    fun `empty peer public key throws exception`() {
        sessionManager.createEphemeralSession(ByteArray(0))
    }

    @Test(expected = Exception::class)
    fun `null-like empty peer public key throws exception`() {
        sessionManager.createEphemeralSession(byteArrayOf(0x00, 0x00, 0x00, 0x00))
    }

    @Test(expected = Exception::class)
    fun `malformed public key throws exception`() {
        val garbageKey = ByteArray(64) { it.toByte() }
        sessionManager.createEphemeralSession(garbageKey)
    }

    @Test
    fun `different nonces produce different session keys`() {
        val aliceIdentity = generateIdentityKeypair()
        val bobIdentity = generateIdentityKeypair()

        val aliceSession1 = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)
        val nonceA = aliceSession1.sessionNonce

        val nonceB = sessionManager.generateNonce()
        val bobSessionKey1 = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession1.ephemeralPublicKey,
            peerNonce = nonceA,
            myEphemeralKeyPair = bobIdentity,
            myNonce = nonceB
        )

        val nonceC = sessionManager.generateNonce()
        val bobSessionKey2 = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession1.ephemeralPublicKey,
            peerNonce = nonceA,
            myEphemeralKeyPair = bobIdentity,
            myNonce = nonceC
        )

        Assert.assertFalse(
            "Different nonces must produce different session keys",
            bobSessionKey1.contentEquals(bobSessionKey2)
        )
    }

    @Test
    fun `nonce is exactly 16 bytes`() {
        val nonce = sessionManager.generateNonce()

        Assert.assertEquals(
            "Nonce must be 16 bytes",
            16,
            nonce.size
        )
    }

    @Test
    fun `multiple nonces are unique`() {
        val nonces = List(100) { sessionManager.generateNonce() }

        val uniqueNonces = nonces.map { it.contentHashCode() }.toSet()
        Assert.assertEquals(
            "All generated nonces must be unique",
            100,
            uniqueNonces.size
        )
    }

    @Test
    fun `tampered ephemeral key produces different session key`() {
        val aliceIdentity = generateIdentityKeypair()
        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)

        val tamperedKey = aliceSession.ephemeralPublicKey.copyOf()
        if (tamperedKey.isNotEmpty()) {
            tamperedKey[0] = (tamperedKey[0].toInt() xor 1).toByte()
        }

        val bobSessionKeyOriginal = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = generateIdentityKeypair(),
            myNonce = aliceSession.sessionNonce
        )

        val bobSessionKeyTampered = try {
            sessionManager.deriveSessionKeyFromPeer(
                peerEphemeralPubKeyBytes = tamperedKey,
                peerNonce = aliceSession.sessionNonce,
                myEphemeralKeyPair = generateIdentityKeypair(),
                myNonce = aliceSession.sessionNonce
            )
        } catch (e: Exception) {
            null
        }

        if (bobSessionKeyTampered != null) {
            Assert.assertFalse(
                "Tampered ephemeral key must produce different session key",
                bobSessionKeyOriginal.contentEquals(bobSessionKeyTampered)
            )
        }
    }

    @Test
    fun `tampered nonce produces different session key`() {
        val aliceIdentity = generateIdentityKeypair()
        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)

        val tamperedNonce = aliceSession.sessionNonce.copyOf()
        if (tamperedNonce.isNotEmpty()) {
            tamperedNonce[0] = (tamperedNonce[0].toInt() xor 1).toByte()
        }

        val bobSessionKeyOriginal = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = generateIdentityKeypair(),
            myNonce = aliceSession.sessionNonce
        )

        val bobSessionKeyTampered = sessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = tamperedNonce,
            myEphemeralKeyPair = generateIdentityKeypair(),
            myNonce = aliceSession.sessionNonce
        )

        Assert.assertFalse(
            "Tampered nonce must produce different session key",
            bobSessionKeyOriginal.contentEquals(bobSessionKeyTampered)
        )
    }

    @Test
    fun `swapped nonce order produces different session key`() {
        val aliceIdentity = generateIdentityKeypair()
        val bobIdentity = generateIdentityKeypair()
        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)
        val bobNonce = sessionManager.generateNonce()

        val saltAB = aliceSession.sessionNonce + bobNonce
        val keyAB = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = aliceSession.sessionKey,
            salt = saltAB,
            info = "Meshify-Session-v2"
        )

        val saltBA = bobNonce + aliceSession.sessionNonce
        val keyBA = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = aliceSession.sessionKey,
            salt = saltBA,
            info = "Meshify-Session-v2"
        )

        Assert.assertFalse(
            "Reversed nonce order must produce different session key",
            keyAB.contentEquals(keyBA)
        )
    }

    @Test(expected = Exception::class)
    fun `zero-length ephemeral private key throws on finalize`() {
        val aliceIdentity = generateIdentityKeypair()
        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)

        sessionManager.finalizeSessionKey(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralPrivateKey = ByteArray(0),
            myNonce = aliceSession.sessionNonce
        )
    }

    @Test
    fun `ephemeral private key can be zeroed`() {
        val aliceIdentity = generateIdentityKeypair()
        val aliceSession = sessionManager.createEphemeralSession(aliceIdentity.public.encoded)

        sessionManager.zeroPrivateKey(aliceSession.ephemeralPrivateKey)

        val zeroedKey = aliceSession.ephemeralPrivateKey
        Assert.assertTrue(
            "Private key should be zeroed after use",
            zeroedKey.all { it == 0.toByte() }
        )
    }

    @Test
    fun `ephemeral keys are different for each session`() {
        val peerIdentity = generateIdentityKeypair()
        val session1 = sessionManager.createEphemeralSession(peerIdentity.public.encoded)
        val session2 = sessionManager.createEphemeralSession(peerIdentity.public.encoded)

        Assert.assertFalse(
            "Ephemeral keys must be unique per session",
            session1.ephemeralPublicKey.contentEquals(session2.ephemeralPublicKey)
        )

        Assert.assertFalse(
            "Session nonces must be unique per session",
            session1.sessionNonce.contentEquals(session2.sessionNonce)
        )
    }

    @Test
    fun `HKDF with same inputs produces same output`() {
        val sharedSecret = ByteArray(32) { 0x42 }
        val salt = ByteArray(16) { 0x55 }
        val info = "test-info"

        val key1 = HkdfKeyDerivation.deriveSessionKey(sharedSecret, salt, info)
        val key2 = HkdfKeyDerivation.deriveSessionKey(sharedSecret, salt, info)

        Assert.assertArrayEquals(
            "HKDF must be deterministic",
            key1,
            key2
        )
    }

    @Test
    fun `HKDF with different info produces different keys`() {
        val sharedSecret = ByteArray(32) { 0x42 }
        val salt = ByteArray(16) { 0x55 }

        val key1 = HkdfKeyDerivation.deriveSessionKey(sharedSecret, salt, "info1")
        val key2 = HkdfKeyDerivation.deriveSessionKey(sharedSecret, salt, "info2")

        Assert.assertFalse(
            "Different info strings must produce different keys",
            key1.contentEquals(key2)
        )
    }

    @Test
    fun `HKDF with empty salt uses default zeros per RFC 5869`() {
        // Per RFC 5869 Section 3.1: empty salt should be treated as "not provided"
        // and use default HashLen zeros (32 bytes for SHA-256)
        val hkdf = HkdfKeyDerivation
        val ikm = ByteArray(32) { 0x42 }

        // Should NOT throw, should use default 32-byte zeros
        val key = hkdf.deriveKey(ikm, salt = ByteArray(0))

        Assert.assertEquals(32, key.size)
        // Should produce same output as explicitly passing 32 zeros
        val expectedKey = hkdf.deriveKey(ikm, salt = ByteArray(32))
        Assert.assertArrayEquals(expectedKey, key)
    }

    @Test
    fun `HKDF output is always 32 bytes`() {
        val testCases = listOf(
            ByteArray(1) { 1 } to "tiny",
            ByteArray(32) { 0x42 } to "normal",
            ByteArray(256) { 0xFF.toByte() } to "large"
        )

        testCases.forEach { (sharedSecret, label) ->
            val key = HkdfKeyDerivation.deriveSessionKey(sharedSecret, ByteArray(16), label)

            Assert.assertEquals(
                "Output must be 32 bytes for input '$label'",
                32,
                key.size
            )
        }
    }
}
