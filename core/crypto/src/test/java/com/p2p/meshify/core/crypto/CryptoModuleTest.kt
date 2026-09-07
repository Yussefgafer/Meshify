package com.p2p.meshify.core.crypto

import android.content.Context
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class DeviceKeysetManagerTest {

    private lateinit var manager: DeviceKeysetManager
    private lateinit var testDir: File

    @Before
    fun setUp() {
        HybridConfig.register()
        testDir = File.createTempFile("meshify_crypto_", "_${UUID.randomUUID()}").apply {
            deleteOnExit()
            delete()
        }
        val context = mockk<Context>(relaxed = true)
        every { context.getFilesDir() } returns testDir
        manager = DeviceKeysetManager(context)
    }

    @Test
    fun getPublicKeyBase64_returnsNonEmptyString() {
        val publicKey = manager.getPublicKeyBase64()
        assertTrue("Public key should not be empty", publicKey.isNotEmpty())
    }

    @Test
    fun getPrivateKeysetHandle_returnsNonNull() {
        val handle = manager.getPrivateKeysetHandle()
        assertNotNull(handle)
    }

    @Test
    fun publicAndPrivateHandlesAreConsistent() {
        val privateKeyHandle = manager.getPrivateKeysetHandle()
        val publicKeyHandle = manager.getPublicKeysetHandle()

        val plaintext = "hello meshify".toByteArray()
        val encrypted = publicKeyHandle.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))

        val decrypted = privateKeyHandle.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(encrypted, ByteArray(0))

        assertEquals(String(plaintext), String(decrypted))
    }

    @Test
    fun publicAndPrivateHandlesAreDifferent() {
        val privateKeyHandle = manager.getPrivateKeysetHandle()
        val publicKeyHandle = manager.getPublicKeysetHandle()
        assert(privateKeyHandle !== publicKeyHandle)
    }
}

class PeerPublicKeyStoreTest {

    private lateinit var store: PeerPublicKeyStore

    @Before
    fun setUp() {
        HybridConfig.register()
        store = PeerPublicKeyStore(defaultWaitTimeoutMs = 100L)
    }

    @Test
    fun storeAndGetIfKnown_returnsPublicKey() {
        val peerId = "peer1"
        val publicKey = generateKeyset()
        val publicKeyBase64 = exportKeyBase64(publicKey)

        store.store(peerId, publicKeyBase64)

        val retrieved = store.getIfKnown(peerId)
        assertNotNull(retrieved)
    }

    @Test
    fun awaitKey_returnsNullForUnknownPeer() = runTest {
        val result = store.awaitKey("unknown_peer")
        assertNull(result)
    }

    @Test
    fun awaitKey_returnsImmediatelyWhenKeyKnown() = runTest {
        val peerId = "peer1"
        val publicKey = generateKeyset()
        val publicKeyBase64 = exportKeyBase64(publicKey)

        store.store(peerId, publicKeyBase64)

        val result = store.awaitKey(peerId)
        assertNotNull(result)
    }

    @Test
    fun awaitKey_completesWhenKeyArrivesDuringWait() = runTest {
        val peerId = "peer2"

        coroutineScope {
            val deferredResult = async { store.awaitKey(peerId) }
            delay(50)

            val publicKey = generateKeyset()
            store.store(peerId, exportKeyBase64(publicKey))

            val result = deferredResult.await()
            assertNotNull(result)
        }
    }

    @Test
    fun markPeerAsUnsupported_preventsEncryption() = runTest {
        val peerId = "unsupported_peer"
        store.markPeerAsUnsupported(peerId)

        assertTrue(store.isKnownUnsupported(peerId))
        assertNull(store.awaitKey(peerId))
    }

    @Test
    fun getIfKnown_returnsNullForUnknownPeer() {
        val result = store.getIfKnown("unknown")
        assertNull(result)
    }

    @Test
    fun store_overwritesExistingKey() {
        val peerId = "peer1"
        val key1 = generateKeyset()
        val key2 = generateKeyset()
        store.store(peerId, exportKeyBase64(key1))
        store.store(peerId, exportKeyBase64(key2))

        val retrieved = store.getIfKnown(peerId)
        assertNotNull(retrieved)
    }

    @Test
    fun awaitKey_timeouts_whenKeyNeverArrives() = runTest {
        val storeWithTimeout = PeerPublicKeyStore(defaultWaitTimeoutMs = 50L)
        val result = storeWithTimeout.awaitKey("never_arrives")
        assertNull(result)
    }

    @Test
    fun concurrent_storeAndMarkUnsupported_consistentState() = runTest {
        val peerId = "race_peer"
        val publicKey = generateKeyset()
        val publicKeyBase64 = exportKeyBase64(publicKey)
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

        coroutineScope {
            val markJob = launch {
                ready.await()
                store.markPeerAsUnsupported(peerId)
            }

            val storeJob = launch {
                ready.await()
                store.store(peerId, publicKeyBase64)
            }

            ready.complete(Unit)
            markJob.join()
            storeJob.join()
        }

        val keyExists = store.getIfKnown(peerId) != null
        val unsupported = store.isKnownUnsupported(peerId)
        assertTrue("State must be consistent: either key is stored or peer is unsupported, not both",
            keyExists || unsupported)
        assertFalse("Key and unsupported flag must not both be true",
            keyExists && unsupported)
    }

    private fun generateKeyset(): com.google.crypto.tink.KeysetHandle {
        return com.google.crypto.tink.KeysetHandle.generateNew(
            com.google.crypto.tink.hybrid.HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
        )
    }

    private fun exportKeyBase64(handle: com.google.crypto.tink.KeysetHandle): String {
        val os = java.io.ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(os))
        val bytes = os.toByteArray()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}

class TinkMessageCipherTest {

    private lateinit var cipher: TinkMessageCipher
    private lateinit var localKeysetManager: DeviceKeysetManager
    private lateinit var store: PeerPublicKeyStore
    private lateinit var testDir: File
    private val TEST_PEER_ID = "test_peer"

    @Before
    fun setUp() {
        HybridConfig.register()
        testDir = File.createTempFile("meshify_crypto_", "_${UUID.randomUUID()}").apply {
            deleteOnExit()
            delete()
        }
        val context = mockk<Context>(relaxed = true)
        every { context.getFilesDir() } returns testDir
        localKeysetManager = DeviceKeysetManager(context)
        val publicKeyBase64 = localKeysetManager.getPublicKeyBase64()
        store = PeerPublicKeyStore(defaultWaitTimeoutMs = 100L)
        store.store(TEST_PEER_ID, publicKeyBase64)
        cipher = TinkMessageCipher(localKeysetManager, store)
    }

    @Test
    fun encryptAndDecrypt_roundtrip() = runTest {
        val peerId = TEST_PEER_ID
        val plaintext = "Hello Meshify! 🔐".toByteArray()
        val result = cipher.encryptFor(peerId, plaintext)

        assertTrue(result is EncryptResult.Success)
        val ciphertext = (result as EncryptResult.Success).ciphertext
        val decrypted = cipher.decrypt(ciphertext)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptFor_unsupportedPeer_returnsDoesNotSupportEncryption() = runTest {
        val peerId = "unsupported"
        store.markPeerAsUnsupported(peerId)

        val result = cipher.encryptFor(peerId, "test".toByteArray())
        assertSame(EncryptResult.PeerDoesNotSupportEncryption, result)
    }

    @Test
    fun encryptFor_unknownPeer_returnsPublicKeyUnavailable() = runTest {
        val result = cipher.encryptFor("unknown_peer", "test".toByteArray())
        assertSame(EncryptResult.PublicKeyUnavailable, result)
    }

    @Test
    fun decrypt_withWrongKey_throwsException() {
        // Encrypt with a different key pair
        val otherKeyset = generateKeyset()
        val encrypted = otherKeyset.publicKeysetHandle
            .getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt("test".toByteArray(), ByteArray(0))

        // Decrypt with the device's key (which is different)
        try {
            cipher.decrypt(encrypted)
            assertTrue("Should have thrown", false)
        } catch (e: CryptoDecryptionException) {
            // Expected
        }
    }

    @Test
    fun encryptAndDecrypt_withEmptyMessage() = runTest {
        val peerId = TEST_PEER_ID
        val plaintext = ByteArray(0)
        val result = cipher.encryptFor(peerId, plaintext)

        assertTrue(result is EncryptResult.Success)
        val ciphertext = (result as EncryptResult.Success).ciphertext
        val decrypted = cipher.decrypt(ciphertext)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptAndDecrypt_withLongMessage() = runTest {
        val peerId = TEST_PEER_ID
        val plaintext = "A".repeat(10000).toByteArray()
        val result = cipher.encryptFor(peerId, plaintext)

        assertTrue(result is EncryptResult.Success)
        val ciphertext = (result as EncryptResult.Success).ciphertext
        val decrypted = cipher.decrypt(ciphertext)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    private fun generateKeyset(): com.google.crypto.tink.KeysetHandle {
        return com.google.crypto.tink.KeysetHandle.generateNew(
            com.google.crypto.tink.hybrid.HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
        )
    }

    private fun exportKeyBase64(handle: com.google.crypto.tink.KeysetHandle): String {
        val os = java.io.ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(os))
        val bytes = os.toByteArray()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}
