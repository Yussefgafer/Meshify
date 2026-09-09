package com.p2p.meshify.core.crypto

import android.content.Context
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.hybrid.HybridConfig
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CryptoModuleProvideTest {

    private lateinit var testDir: File

    @Before
    fun setUp() {
        HybridConfig.register()
        testDir = File.createTempFile("meshify_crypto_module_", "_${UUID.randomUUID()}").apply {
            deleteOnExit()
            delete()
        }
    }

    private fun mockContext(): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getFilesDir() } returns testDir
        return ctx
    }

    @Test
    fun provideDeviceKeysetManager_returnsNonNullAndUsable() {
        val manager = CryptoModule.provideDeviceKeysetManager(mockContext())
        assertNotNull(manager)
        assertNotNull(manager.getPrivateKeysetHandle())
        assertTrue(manager.getPublicKeyBase64().isNotEmpty())
    }

    @Test
    fun providePeerPublicKeyStore_returnsNonNullAndFunctional() {
        val store = CryptoModule.providePeerPublicKeyStore()
        assertNotNull(store)
        assertTrue(store.getIfKnown("nope") == null)
    }

    @Test
    fun directObjectModuleCalls_createNewInstances_eachTime() {
        // Known limitation: calling the @Provides factory methods directly on CryptoModule
        // creates new instances every call. Hilt @Singleton scoping is enforced by the
        // Hilt component graph, not by these plain object methods, and cannot be verified
        // in a unit test without a Hilt test runner / Robolectric @HiltAndroidTest.
        val a = CryptoModule.providePeerPublicKeyStore()
        val b = CryptoModule.providePeerPublicKeyStore()
        assertTrue(a !== b)
        val m1 = CryptoModule.provideDeviceKeysetManager(mockContext())
        val m2 = CryptoModule.provideDeviceKeysetManager(mockContext())
        assertTrue(m1 !== m2)
    }

    @Test
    fun provideMessageCipher_returnsTinkMessageCipher_wiredCorrectly() {
        val ctx = mockContext()
        val manager = CryptoModule.provideDeviceKeysetManager(ctx)
        val store = CryptoModule.providePeerPublicKeyStore()
        val cipher: MessageCipher = CryptoModule.provideMessageCipher(manager, store)
        assertNotNull(cipher)
        assertTrue(cipher is TinkMessageCipher)
        assertTrue(cipher.getPublicKeyBase64().isNotEmpty())
        assertTrue(cipher.getPublicKeyBase64() == manager.getPublicKeyBase64())
    }

    @Test
    fun semanticSingletonSafe_sameManagerInstance_stablePublicKeyAndRoundTrip() {
        val ctx = mockContext()
        val manager = CryptoModule.provideDeviceKeysetManager(ctx)
        val first = manager.getPublicKeyBase64()
        val second = manager.getPublicKeyBase64()
        assertEquals(first, second)

        val pub = manager.getPublicKeysetHandle()
        val priv = manager.getPrivateKeysetHandle()
        val plaintext = "singleton-semantics".toByteArray()
        val ciphertext = pub.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
            .encrypt(plaintext, ByteArray(0))
        val decrypted = priv.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
            .decrypt(ciphertext, ByteArray(0))
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun semanticSingletonSafe_samePeerStoreInstance_storeThenAwaitConsistent() = runTest {
        // This exercises the same-store instance path; Hilt-scoped sharing is assumed
        // from the module declaration and not unit-testable here.
        val store = CryptoModule.providePeerPublicKeyStore()
        val publicKeyBase64 = managerPublicKeyBase64FromNewManager()
        store.store("hilt_singleton_proxy", publicKeyBase64)
        val awaited = store.awaitKey("hilt_singleton_proxy", timeoutMs = 1000L)
        assertNotNull(awaited)
        val reexport = exportKeyBase64(awaited!!)
        assertEquals(publicKeyBase64, reexport)
    }

    @Test
    fun provideMessageCipher_delegatesToSameManagerAndStore_instances() {
        val ctx = mockContext()
        val manager = CryptoModule.provideDeviceKeysetManager(ctx)
        val store = CryptoModule.providePeerPublicKeyStore()
        val cipher1 = CryptoModule.provideMessageCipher(manager, store)
        val cipher2 = CryptoModule.provideMessageCipher(manager, store)
        assertTrue(cipher1 !== cipher2)
        assertTrue(cipher1.getPublicKeyBase64() == cipher2.getPublicKeyBase64())
    }

    private fun managerPublicKeyBase64FromNewManager(): String {
        val ctx = mockContext()
        return CryptoModule.provideDeviceKeysetManager(ctx).getPublicKeyBase64()
    }

    private fun exportKeyBase64(handle: com.google.crypto.tink.KeysetHandle): String {
        val os = ByteArrayOutputStream()
        CleartextKeysetHandle.write(
            handle,
            JsonKeysetWriter.withOutputStream(os)
        )
        return Base64.getEncoder().encodeToString(os.toByteArray())
    }
}
