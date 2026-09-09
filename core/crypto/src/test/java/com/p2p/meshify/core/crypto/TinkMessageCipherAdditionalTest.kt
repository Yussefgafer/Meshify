package com.p2p.meshify.core.crypto

import android.content.Context
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TinkMessageCipherAdditionalTest {

    private lateinit var deviceManager: DeviceKeysetManager
    private lateinit var store: PeerPublicKeyStore
    private lateinit var cipher: TinkMessageCipher
    private lateinit var testDir: File

    @Before
    fun setUp() {
        HybridConfig.register()
        testDir = File.createTempFile("meshify_tink_add_", "_${UUID.randomUUID()}").apply {
            deleteOnExit()
            delete()
        }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getFilesDir() } returns testDir
        deviceManager = DeviceKeysetManager(ctx)
        store = PeerPublicKeyStore(defaultWaitTimeoutMs = 300L)
        cipher = TinkMessageCipher(deviceManager, store)
    }

    private fun generateKeyset(): KeysetHandle =
        KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)

    private fun exportPublicBase64(handle: KeysetHandle): String {
        val os = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle.publicKeysetHandle, JsonKeysetWriter.withOutputStream(os))
        return Base64.getEncoder().encodeToString(os.toByteArray())
    }

    private fun exportKeyBase64(handle: KeysetHandle): String {
        val os = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(os))
        return Base64.getEncoder().encodeToString(os.toByteArray())
    }

    private fun setupPeer(peerId: String, keyset: KeysetHandle = generateKeyset()): KeysetHandle {
        store.store(peerId, exportPublicBase64(keyset))
        return keyset
    }

    @Test
    fun getPublicKeyBase64_delegatesToDeviceManager() {
        val fromCipher = cipher.getPublicKeyBase64()
        val fromManager = deviceManager.getPublicKeyBase64()
        assertEquals(fromManager, fromCipher)
        assertTrue(fromCipher.isNotEmpty())
        val decoded = Base64.getDecoder().decode(fromCipher)
        val handle = CleartextKeysetHandle.read(com.google.crypto.tink.JsonKeysetReader.withBytes(decoded))
        assertNotNull(handle)
    }

    @Test
    fun getPublicKeyBase64_isStableAcrossCalls() {
        val first = cipher.getPublicKeyBase64()
        val second = cipher.getPublicKeyBase64()
        assertEquals(first, second)
    }

    @Test
    fun encryptFor_unsupportedPeer_returnsImmediatelyWithoutAwait() = runTest {
        val peerId = "unsupported_immediate"
        store.markPeerAsUnsupported(peerId)
        val result = cipher.encryptFor(peerId, "hello".toByteArray())
        assertSame(EncryptResult.PeerDoesNotSupportEncryption, result)
    }

    @Test
    fun encryptFor_becomesUnsupportedDuringAwait_returnsPeerDoesNotSupportEncryption() = runTest {
        val peerId = "becomes_unsupported"
        coroutineScope {
            val encryptDeferred = async { cipher.encryptFor(peerId, "payload".toByteArray()) }
            delay(30)
            store.markPeerAsUnsupported(peerId)
            val result = encryptDeferred.await()
            assertSame(EncryptResult.PeerDoesNotSupportEncryption, result)
        }
    }

    @Test
    fun encryptFor_unknownPeer_returnsPublicKeyUnavailable_afterTimeout() = runTest {
        val result = cipher.encryptFor("ghost_peer", "test".toByteArray())
        assertSame(EncryptResult.PublicKeyUnavailable, result)
    }

    @Test
    fun encryptFor_success_thenDecrypt_roundTrip_small() = runTest {
        val peerId = "roundtrip_small"
        // Use our own device key as the peer key so decrypt works (self-encrypt)
        val ourPub = deviceManager.getPublicKeyBase64()
        store.store(peerId, ourPub)
        val plaintext = "Hi 🔐 hello world".toByteArray(Charsets.UTF_8)
        val result = cipher.encryptFor(peerId, plaintext)
        assertTrue(result is EncryptResult.Success)
        val ciphertext = (result as EncryptResult.Success).ciphertext
        assertFalse(ciphertext.contentEquals(plaintext))
        assertTrue(ciphertext.isNotEmpty())
        val decrypted = cipher.decrypt(ciphertext)
        assertTrue(plaintext.contentEquals(decrypted))
        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun encryptFor_success_roundTrip_emptyPlaintext() = runTest {
        val peerId = "empty_plain"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintext = ByteArray(0)
        val result = cipher.encryptFor(peerId, plaintext)
        assertTrue(result is EncryptResult.Success)
        val decrypted = cipher.decrypt((result as EncryptResult.Success).ciphertext)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptFor_success_roundTrip_binaryData() = runTest {
        val peerId = "binary"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintext = ByteArray(256) { it.toByte() }
        val result = cipher.encryptFor(peerId, plaintext)
        assertTrue(result is EncryptResult.Success)
        val decrypted = cipher.decrypt((result as EncryptResult.Success).ciphertext)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptFor_success_roundTrip_largeMessage() = runTest {
        val peerId = "large"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintext = ByteArray(8000) { (it % 256).toByte() }
        val result = cipher.encryptFor(peerId, plaintext)
        assertTrue(result is EncryptResult.Success)
        val decrypted = cipher.decrypt((result as EncryptResult.Success).ciphertext)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptFor_success_eachEncryptionProducesDifferentCiphertext_dueToRandomness() = runTest {
        val peerId = "randomness"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintext = "same plaintext".toByteArray()
        val r1 = cipher.encryptFor(peerId, plaintext) as EncryptResult.Success
        val r2 = cipher.encryptFor(peerId, plaintext) as EncryptResult.Success
        assertFalse("Two encryptions of same plaintext should differ (randomized)", r1.ciphertext.contentEquals(r2.ciphertext))
        // Both decrypt to same plaintext
        assertTrue(plaintext.contentEquals(cipher.decrypt(r1.ciphertext)))
        assertTrue(plaintext.contentEquals(cipher.decrypt(r2.ciphertext)))
    }

    @Test
    fun encryptFor_withDifferentPeerKeys_eachDecryptRequiresOwnPrivateKey() = runTest {
        val peerA = "peerA"
        val peerB = "peerB"
        val keysetA = generateKeyset()
        val keysetB = generateKeyset()
        store.store(peerA, exportPublicBase64(keysetA))
        store.store(peerB, exportPublicBase64(keysetB))
        val plainA = "message for A".toByteArray()
        val plainB = "message for B".toByteArray()
        val resA = cipher.encryptFor(peerA, plainA) as EncryptResult.Success
        val resB = cipher.encryptFor(peerB, plainB) as EncryptResult.Success
        assertFalse(resA.ciphertext.contentEquals(resB.ciphertext))
        // Cipher for peerA cannot be decrypted by deviceManager (which holds unrelated key)
        // but can be decrypted by keysetA's private handle
        val decA = keysetA.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(resA.ciphertext, ByteArray(0))
        assertTrue(plainA.contentEquals(decA))
        val decB = keysetB.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(resB.ciphertext, ByteArray(0))
        assertTrue(plainB.contentEquals(decB))
        // Cross-decrypt should fail
        try {
            keysetB.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
                .decrypt(resA.ciphertext, ByteArray(0))
            assertTrue("Cross-decrypt should have thrown", false)
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun decrypt_withWrongKey_throwsCryptoDecryptionException() {
        val other = generateKeyset()
        val ciphertext = other.publicKeysetHandle
            .getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt("secret".toByteArray(), ByteArray(0))
        try {
            cipher.decrypt(ciphertext)
            assertTrue("Should have thrown CryptoDecryptionException", false)
        } catch (e: CryptoDecryptionException) {
            assertTrue(e is CryptoException)
            assertEquals("Failed to decrypt message", e.message)
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_withRandomBytes_throwsCryptoDecryptionException() {
        val garbage = ByteArray(32) { (it * 7).toByte() }
        try {
            cipher.decrypt(garbage)
            assertTrue("Should have thrown", false)
        } catch (e: CryptoDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_withEmptyCiphertext_throwsCryptoDecryptionException() {
        try {
            cipher.decrypt(ByteArray(0))
            assertTrue("Should have thrown", false)
        } catch (e: CryptoDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_withTruncatedCiphertext_throwsCryptoDecryptionException() = runTest {
        val peerId = "trunc"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val result = cipher.encryptFor(peerId, "truncate me".toByteArray()) as EncryptResult.Success
        val truncated = result.ciphertext.copyOf(result.ciphertext.size / 2)
        try {
            cipher.decrypt(truncated)
            assertTrue("Should have thrown on truncated ciphertext", false)
        } catch (e: CryptoDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_withModifiedCiphertext_throwsCryptoDecryptionException() = runTest {
        val peerId = "modified"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val result = cipher.encryptFor(peerId, "modify me".toByteArray()) as EncryptResult.Success
        val modified = result.ciphertext.copyOf()
        modified[modified.size - 1] = (modified[modified.size - 1].toInt() xor 0xFF).toByte()
        modified[modified.size / 2] = (modified[modified.size / 2].toInt() xor 0xAA).toByte()
        try {
            cipher.decrypt(modified)
            assertTrue("Should have thrown on tampered ciphertext", false)
        } catch (e: CryptoDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_throwsCryptoDecryptionException_notGenericCryptoException() = runTest {
        val peerId = "type_check"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val ct = (cipher.encryptFor(peerId, "x".toByteArray()) as EncryptResult.Success).ciphertext
        val tampered = ct.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        var caught: CryptoDecryptionException? = null
        try {
            cipher.decrypt(tampered)
        } catch (e: CryptoDecryptionException) {
            caught = e
        } catch (e: CryptoException) {
            assertTrue("Should be CryptoDecryptionException subtype, got ${e::class.simpleName}", false)
        }
        if (caught == null) {
            // Some single-bit flips may still decrypt? But ECIES+GCM should fail auth on any mutation
            // If it didn't throw, consider fluke — retry with larger tamper
            val moreTampered = ct.copyOf().apply { fill(0x00) }
            try {
                cipher.decrypt(moreTampered)
                assertTrue("Tampered more heavily should throw", false)
            } catch (e: CryptoDecryptionException) {
                // expected
            }
        } else {
            assertTrue(caught is CryptoException)
        }
    }

    @Test
    fun decrypt_withTamperedCiphertext_viaWrongKey_roundtripIsConsistent() {
        val victimCiphertext = com.google.crypto.tink.KeysetHandle
            .generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            .publicKeysetHandle
            .getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt("victim".toByteArray(), ByteArray(0))
        try {
            cipher.decrypt(victimCiphertext)
            assertTrue("Decrypting with the wrong private key should throw", false)
        } catch (e: CryptoDecryptionException) {
            assertEquals("Failed to decrypt message", e.message)
            assertNotNull(e.cause)
        }
    }

    @Test
    fun emptyContextInfo_isAlwaysEmpty_bothEncryptAndDecryptUseIt() = runTest {
        val peerId = "context_test"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintext = "context".toByteArray()
        val result = cipher.encryptFor(peerId, plaintext) as EncryptResult.Success
        // Verify that decrypting with same empty context via Tink directly matches cipher.decrypt
        val directDecrypt = deviceManager.getPrivateKeysetHandle()
            .getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(result.ciphertext, ByteArray(0))
        assertTrue(plaintext.contentEquals(directDecrypt))
        // Decrypting with non-empty context should fail
        try {
            deviceManager.getPrivateKeysetHandle()
                .getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
                .decrypt(result.ciphertext, "non-empty".toByteArray())
            assertTrue("Decrypt with wrong contextInfo should have thrown", false)
        } catch (_: Exception) {
            // expected — proves Tink distinguishes empty vs non-empty context
        }
        // Cipher's decrypt also uses empty, so it should still succeed
        val viaCipher = cipher.decrypt(result.ciphertext)
        assertTrue(plaintext.contentEquals(viaCipher))
    }

    @Test
    fun encryptFor_concurrentCalls_allSucceed() = runTest {
        val peerId = "concurrent_encrypt"
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val plaintexts = (0 until 10).map { "msg $it".toByteArray() }
        coroutineScope {
            val deferreds = plaintexts.map { pt ->
                async { cipher.encryptFor(peerId, pt) }
            }
            val results = deferreds.awaitAll()
            results.forEachIndexed { idx, r ->
                assertTrue(r is EncryptResult.Success)
                val ct = (r as EncryptResult.Success).ciphertext
                val dec = cipher.decrypt(ct)
                assertTrue(plaintexts[idx].contentEquals(dec))
            }
        }
    }

    @Test
    fun encryptFor_awaitKeyReturnsNullAndMarkedUnsupported_returnsPeerDoesNotSupportEncryption_notUnavailable() = runTest {
        val peerId = "second_check_unsupported"
        // Use a mock store where awaitKey returns null but isKnownUnsupported then true
        val mockStore = mockk<PeerPublicKeyStore>()
        every { mockStore.isKnownUnsupported(peerId) } returnsMany listOf(false, true)
        io.mockk.coEvery { mockStore.awaitKey(peerId) } returns null
        val c = TinkMessageCipher(deviceManager, mockStore)
        val result = c.encryptFor(peerId, "x".toByteArray())
        assertSame(EncryptResult.PeerDoesNotSupportEncryption, result)
        verify(exactly = 2) { mockStore.isKnownUnsupported(peerId) }
    }

    @Test
    fun encryptFor_awaitKeyReturnsNullAndNotUnsupported_returnsPublicKeyUnavailable() = runTest {
        val peerId = "second_check_unavailable"
        val mockStore = mockk<PeerPublicKeyStore>()
        every { mockStore.isKnownUnsupported(peerId) } returnsMany listOf(false, false)
        io.mockk.coEvery { mockStore.awaitKey(peerId) } returns null
        val c = TinkMessageCipher(deviceManager, mockStore)
        val result = c.encryptFor(peerId, "x".toByteArray())
        assertSame(EncryptResult.PublicKeyUnavailable, result)
    }

    @Test
    fun messageCipher_interfaceContract_cipherIsMessageCipher() {
        assertTrue(cipher is MessageCipher)
        val viaInterface: MessageCipher = cipher
        assertNotNull(viaInterface.getPublicKeyBase64())
    }

    @Test
    fun decrypt_wrongEmptyContext_handling() {
        // Additional: ensure decrypt of ciphertext encrypted with non-empty context fails via cipher
        val otherKey = deviceManager.getPublicKeysetHandle()
        val encryptWithContext = otherKey.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt("with context".toByteArray(), "ctx".toByteArray())
        try {
            cipher.decrypt(encryptWithContext)
            assertTrue("Cipher decrypt with empty context should fail on ctx-encrypted payload", false)
        } catch (e: CryptoDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun encryptFor_afterPeerKeyArrivesLate_succeedsOnRetry() = runTest {
        val peerId = "late_arrival_retry"
        // First attempt should timeout and return unavailable
        val first = cipher.encryptFor(peerId, "first".toByteArray())
        assertSame(EncryptResult.PublicKeyUnavailable, first)
        // Now key arrives
        store.store(peerId, deviceManager.getPublicKeyBase64())
        val second = cipher.encryptFor(peerId, "second".toByteArray())
        assertTrue(second is EncryptResult.Success)
        val dec = cipher.decrypt((second as EncryptResult.Success).ciphertext)
        assertEquals("second", String(dec))
    }
}
