package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.data.security.impl.EcdhSessionManager
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.data.security.impl.InMemoryNonceCache
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.domain.security.interfaces.NonceCache
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatRepositoryImplEncryptionTest {

    private lateinit var context: Context
    private lateinit var peerIdentity: PeerIdentityRepository
    private lateinit var ecdhSessionManager: EcdhSessionManager
    private lateinit var sessionKeyStore: EncryptedSessionKeyStore
    private lateinit var replayCache: NonceCache
    private lateinit var messageCrypto: MessageEnvelopeCrypto

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        peerIdentity = MockPeerIdentityRepository()
        ecdhSessionManager = EcdhSessionManager()
        sessionKeyStore = EncryptedSessionKeyStore(context)
        replayCache = InMemoryNonceCache(windowMs = 30_000L)
        messageCrypto = MessageEnvelopeCrypto(peerIdentity, replayCache)
    }

    private fun generateIdentityKeypair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        return kpg.generateKeyPair()
    }

    @Test
    fun `full encryption flow from Alice to Bob succeeds`() = runTest {
        val aliceIdentity = generateIdentityKeypair()
        val bobIdentity = generateIdentityKeypair()

        val aliceSession = ecdhSessionManager.createEphemeralSession(bobIdentity.public.encoded)

        val bobNonce = ecdhSessionManager.generateNonce()
        val bobSessionKey = ecdhSessionManager.deriveSessionKeyFromPeer(
            peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
            peerNonce = aliceSession.sessionNonce,
            myEphemeralKeyPair = bobIdentity,
            myNonce = bobNonce
        )

        val aliceFinalizedKey = ecdhSessionManager.finalizeSessionKey(
            peerEphemeralPubKeyBytes = bobIdentity.public.encoded,
            peerNonce = bobNonce,
            myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
            myNonce = aliceSession.sessionNonce
        )

        Assert.assertArrayEquals(
            "Alice and Bob must derive same session key",
            aliceFinalizedKey,
            bobSessionKey
        )

        val plaintext = "Hello Bob!".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "bob-id", aliceFinalizedKey)

        val decrypted = messageCrypto.decrypt(
            envelope = envelope,
            senderPublicKeyBytes = aliceIdentity.public.encoded,
            sessionKey = bobSessionKey
        )

        Assert.assertArrayEquals(
            "Bob must decrypt to original plaintext",
            plaintext,
            decrypted
        )
    }

    @Test
    fun `TOFU violation prevents session establishment`() = runTest {
        val peerId = "attacker-peer"
        val originalPubKeyHex = ByteArray(64) { 0x42 }.joinToString("") { "%02x".format(it) }
        sessionKeyStore.putSessionKey(peerId, ByteArray(32) { 0x55 }, originalPubKeyHex)

        val attackerPubKeyHex = ByteArray(64) { 0x66 }.joinToString("") { "%02x".format(it) }
        val tofuResult = sessionKeyStore.validatePeerPublicKey(peerId, attackerPubKeyHex)

        Assert.assertEquals(
            "TOFU violation must return false",
            false,
            tofuResult
        )

        val existingSession = sessionKeyStore.getSessionKey(peerId)
        Assert.assertEquals(
            "Original session key must be preserved",
            originalPubKeyHex,
            existingSession?.peerPublicKeyHex
        )
    }

    @Test
    fun `decryption failure discards message`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", sessionKey)

        val tamperedCiphertext = envelope.ciphertext.copyOf()
        if (tamperedCiphertext.isNotEmpty()) {
            tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 1).toByte()
        }
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention tampering or GCM",
            exception.message?.contains("tampered", ignoreCase = true) == true ||
            exception.message?.contains("GCM", ignoreCase = true) == true
        )
    }

    @Test
    fun `wrong session key fails decryption`() = runTest {
        val key1 = ByteArray(32) { 0x42 }
        val key2 = ByteArray(32) { 0x55 }
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", key1)

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = envelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = key2
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention GCM or tag",
            exception.message?.contains("GCM", ignoreCase = true) == true ||
            exception.message?.contains("tag", ignoreCase = true) == true
        )
    }

    @Test
    fun `replay attack is detected and rejected`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", sessionKey)

        runTest {
            messageCrypto.decrypt(
                envelope = envelope,
                senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                sessionKey = sessionKey
            )
        }

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = envelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention replay",
            exception.message?.contains("replay", ignoreCase = true) == true
        )
    }

    @Test
    fun `old message is rejected as stale`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Old message".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", sessionKey)

        val oldTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L)
        val oldEnvelope = envelope.copy(timestamp = oldTimestamp)

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = oldEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention old or stale",
            exception.message?.contains("old", ignoreCase = true) == true ||
            exception.message?.contains("stale", ignoreCase = true) == true ||
            exception.message?.contains("ago", ignoreCase = true) == true
        )
    }

    @Test
    fun `future message is rejected`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Future message".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", sessionKey)

        val futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L)
        val futureEnvelope = envelope.copy(timestamp = futureTimestamp)

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = futureEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention future",
            exception.message?.contains("future", ignoreCase = true) == true
        )
    }

    @Test
    fun `session key survives store destroy and recreate`() = runTest {
        val peerId = "persistence-peer"
        val sessionKey = ByteArray(32) { 0x42 }
        val publicKeyHex = ByteArray(64) { 0x55 }.joinToString("") { "%02x".format(it) }

        sessionKeyStore.putSessionKey(peerId, sessionKey, publicKeyHex)

        sessionKeyStore.destroy()
        val newStore = EncryptedSessionKeyStore(context)

        val retrieved = newStore.getSessionKey(peerId)
        Assert.assertNotNull("Session should persist", retrieved)
        Assert.assertArrayEquals(
            "Session key should match after restart",
            sessionKey,
            retrieved?.sessionKey
        )
    }

    @Test
    fun `encryption with invalid session key length throws`() = runTest {
        val wrongKey = ByteArray(16) { 0x42 }
        val plaintext = "Test".toByteArray(Charsets.UTF_8)

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.encrypt(plaintext, "recipient", wrongKey)
            }
        }

        Assert.assertTrue(
            "Exception should mention key size",
            exception.message?.contains("32", ignoreCase = true) == true ||
            exception.message?.contains("size", ignoreCase = true) == true
        )
    }

    @Test
    fun `decryption with invalid session key length throws`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Test".toByteArray(Charsets.UTF_8)
        val envelope = messageCrypto.encrypt(plaintext, "recipient", sessionKey)
        val wrongKey = ByteArray(16) { 0x55 }

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = envelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = wrongKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention key size",
            exception.message?.contains("32", ignoreCase = true) == true ||
            exception.message?.contains("size", ignoreCase = true) == true
        )
    }

    @Test
    fun `oversized message throws SecurityException`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = ByteArray(17 * 1024 * 1024) { 0x42 }

        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.encrypt(plaintext, "recipient", sessionKey)
            }
        }

        Assert.assertTrue(
            "Exception should mention size",
            exception.message?.contains("size", ignoreCase = true) == true ||
            exception.message?.contains("exceed", ignoreCase = true) == true
        )
    }

    @Test
    fun `signature binds message to sender`() = runTest {
        val aliceIdentity = generateIdentityKeypair()
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Hello from Alice".toByteArray(Charsets.UTF_8)

        val aliceCrypto = MessageEnvelopeCrypto(
            MockPeerIdentityRepository(aliceIdentity),
            InMemoryNonceCache()
        )
        val envelope = aliceCrypto.encrypt(plaintext, "bob", sessionKey)

        val charlieIdentity = generateIdentityKeypair()

        val signatureValid = peerIdentity.verifySignature(
            publicKeyBytes = charlieIdentity.public.encoded,
            challenge = envelope.nonce + envelope.ciphertext,
            signature = envelope.signature
        )

        Assert.assertFalse(
            "Signature should not verify with wrong sender key",
            signatureValid
        )
    }

    @Test
    fun `AAD binds message to recipient`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Secret".toByteArray(Charsets.UTF_8)
        val intendedRecipient = "bob"
        val envelope = messageCrypto.encrypt(plaintext, intendedRecipient, sessionKey)

        val tamperedEnvelope = envelope.copy(recipientId = "attacker")
        val exception = Assert.assertThrows(SecurityException::class.java) {
            runTest {
                messageCrypto.decrypt(
                    envelope = tamperedEnvelope,
                    senderPublicKeyBytes = peerIdentity.getPublicKeyBytes(),
                    sessionKey = sessionKey
                )
            }
        }

        Assert.assertTrue(
            "Exception should mention signature or tampering",
            exception.message?.contains("signature", ignoreCase = true) == true ||
            exception.message?.contains("tampering", ignoreCase = true) == true
        )
    }

    @Test
    fun `nonce is unique per message`() = runTest {
        val sessionKey = ByteArray(32) { 0x42 }
        val plaintext = "Test".toByteArray(Charsets.UTF_8)

        val envelope1 = messageCrypto.encrypt(plaintext, "recipient", sessionKey)
        val envelope2 = messageCrypto.encrypt(plaintext, "recipient", sessionKey)
        val envelope3 = messageCrypto.encrypt(plaintext, "recipient", sessionKey)

        Assert.assertFalse("Nonce 1 and 2 must differ", envelope1.nonce.contentEquals(envelope2.nonce))
        Assert.assertFalse("Nonce 2 and 3 must differ", envelope2.nonce.contentEquals(envelope3.nonce))
        Assert.assertFalse("Nonce 1 and 3 must differ", envelope1.nonce.contentEquals(envelope3.nonce))
    }

    private class MockPeerIdentityRepository(
        private val keyPair: KeyPair = run {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            kpg.generateKeyPair()
        }
    ) : PeerIdentityRepository {

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
            signature: ByteArray
        ): Boolean {
            return try {
                val pubKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
                val sig = java.security.Signature.getInstance("SHA256withECDSA")
                sig.initVerify(pubKey)
                sig.update(challenge)
                sig.verify(signature)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun resetIdentity() {}
    }
}
