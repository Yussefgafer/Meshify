package com.p2p.meshify.core.crypto

import android.content.Context
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.hybrid.HybridConfig
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceKeysetManagerAdditionalTest {

    private lateinit var testDir: File

    @Before
    fun setUp() {
        HybridConfig.register()
        testDir = File.createTempFile("meshify_crypto_add_", "_${UUID.randomUUID()}").apply {
            deleteOnExit()
            delete()
        }
    }

    private fun createManager(context: Context = mockkContext()): DeviceKeysetManager {
        return DeviceKeysetManager(context)
    }

    private fun mockkContext(): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getFilesDir() } returns testDir
        return ctx
    }

    @Test
    fun getPublicKeyBase64_isValidBase64AndJson() {
        val manager = createManager()
        val base64 = manager.getPublicKeyBase64()
        assertTrue(base64.isNotEmpty())
        val decoded = Base64.getDecoder().decode(base64)
        assertTrue(decoded.isNotEmpty())
        val json = String(decoded, Charsets.UTF_8)
        assertTrue("JSON should contain primaryKeyId or keyset", json.contains("primaryKeyId") || json.contains("key"))
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(decoded))
        assertNotNull(handle)
    }

    @Test
    fun getPublicKeyBase64_isStableAcrossMultipleCalls_fallbackConsistent() {
        val manager = createManager()
        val first = manager.getPublicKeyBase64()
        val second = manager.getPublicKeyBase64()
        assertEquals("Fallback keyset must be consistent within same manager instance", first, second)
        val decoded1 = Base64.getDecoder().decode(first)
        val decoded2 = Base64.getDecoder().decode(second)
        assertTrue(decoded1.contentEquals(decoded2))
    }

    @Test
    fun getPrivateKeysetHandle_isConsistentAcrossCalls() {
        val manager = createManager()
        val h1 = manager.getPrivateKeysetHandle()
        val h2 = manager.getPrivateKeysetHandle()
        assertTrue(h1 === h2 || h1.toString() == h2.toString())
        val plaintext = "consistency".toByteArray()
        val pub = manager.getPublicKeysetHandle()
        val encrypted = pub.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))
        val decrypted1 = h1.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(encrypted, ByteArray(0))
        val decrypted2 = h2.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(encrypted, ByteArray(0))
        assertTrue(plaintext.contentEquals(decrypted1))
        assertTrue(plaintext.contentEquals(decrypted2))
    }

    @Test
    fun getPrivateAndPublicHandles_areDistinctButCryptographicallyLinked() {
        val manager = createManager()
        val priv = manager.getPrivateKeysetHandle()
        val pub = manager.getPublicKeysetHandle()
        assertTrue(priv !== pub)
        val plaintext = "link-test".toByteArray()
        val ciphertext = pub.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))
        val decrypted = priv.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(ciphertext, ByteArray(0))
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun getPublicKeyBase64_reimportedHandleCanEncryptForPrivateToDecrypt() {
        val manager = createManager()
        val base64 = manager.getPublicKeyBase64()
        val decoded = Base64.getDecoder().decode(base64)
        val importedPub = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(decoded))
        val plaintext = "reimport".toByteArray()
        val ciphertext = importedPub.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))
        val decrypted = manager.getPrivateKeysetHandle()
            .getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(ciphertext, ByteArray(0))
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun hybridConfigRegister_repeatedCallDoesNotBreakFallbackKeyGeneration() {
        // On the JVM, `withSharedPref(...).withMasterKeyUri(...)` never reaches a real
        // Android Keystore; the constructor path falls back to an ephemeral in-memory
        // keyset. Verify the fallback branch still produces usable keys after repeated
        // HybridConfig.register() calls.
        HybridConfig.register()
        HybridConfig.register()
        val manager = createManager()
        assertTrue(manager.getPublicKeyBase64().isNotEmpty())
        val manager2 = createManager()
        assertTrue(manager2.getPublicKeyBase64().isNotEmpty())
        // TODO(instrumented): add an Android instrumentation test that builds
        // `AndroidKeysetManager.Builder().withSharedPref(...)` on a real device/emulator
        // and verifies the keystore-backed path persists across process restarts.
    }

    @Test
    fun fallbackPath_remainsUsableWhenContextThrows() {
        val badContext = mockk<Context>(relaxed = true)
        every { badContext.getFilesDir() } throws RuntimeException("filesDir unavailable")
        val manager = DeviceKeysetManager(badContext)
        val priv = manager.getPrivateKeysetHandle()
        assertNotNull(priv)
        val pub = manager.getPublicKeysetHandle()
        assertNotNull(pub)
        val plaintext = "fallback-usable".toByteArray()
        val ciphertext = pub.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))
        val decrypted = priv.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(ciphertext, ByteArray(0))
        assertTrue(plaintext.contentEquals(decrypted))
        val base64 = manager.getPublicKeyBase64()
        assertTrue(base64.isNotEmpty())
        val decoded = Base64.getDecoder().decode(base64)
        val reimported = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(decoded))
        assertNotNull(reimported)
    }

    @Test
    fun differentManagerInstances_haveDistinctFallbackIdentities() {
        val m1 = createManager()
        val m2 = createManager()
        val b64_1 = m1.getPublicKeyBase64()
        val b64_2 = m2.getPublicKeyBase64()
        assertTrue(b64_1.isNotEmpty())
        assertTrue(b64_2.isNotEmpty())
        assertTrue(
            "Distinct fallback managers should generate different keys",
            b64_1 != b64_2
        )

        val pt = "distinct".toByteArray()
        val pub1 = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(Base64.getDecoder().decode(b64_1)))
        val pub2 = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(Base64.getDecoder().decode(b64_2)))
        val ct1 = pub1.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java).encrypt(pt, ByteArray(0))
        val ct2 = pub2.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java).encrypt(pt, ByteArray(0))

        // Self-decrypt works for each manager
        assertTrue(pt.contentEquals(m1.getPrivateKeysetHandle().getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java).decrypt(ct1, ByteArray(0))))
        assertTrue(pt.contentEquals(m2.getPrivateKeysetHandle().getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java).decrypt(ct2, ByteArray(0))))

        // Cross-decrypt must fail: manager A cannot decrypt manager B's ciphertext
        var crossFailed = false
        try {
            m1.getPrivateKeysetHandle().getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
                .decrypt(ct2, ByteArray(0))
        } catch (_: Exception) {
            crossFailed = true
        }
        assertTrue("Cross-manager decryption should fail for distinct identities", crossFailed)
    }

    @Test
    fun getPublicKeyBase64_producesDifferentBytesThanPrivateSerialization() {
        val manager = createManager()
        val pubB64 = manager.getPublicKeyBase64()
        val pubBytes = Base64.getDecoder().decode(pubB64)
        val privStream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(manager.getPrivateKeysetHandle(), com.google.crypto.tink.JsonKeysetWriter.withOutputStream(privStream))
        val privBytes = privStream.toByteArray()
        assertTrue(pubBytes.isNotEmpty())
        assertTrue(privBytes.isNotEmpty())
        assertTrue(!pubBytes.contentEquals(privBytes))
    }
}
