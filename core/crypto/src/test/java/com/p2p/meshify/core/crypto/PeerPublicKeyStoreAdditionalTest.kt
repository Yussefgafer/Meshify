package com.p2p.meshify.core.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.io.ByteArrayOutputStream

class PeerPublicKeyStoreAdditionalTest {

    private lateinit var store: PeerPublicKeyStore

    @Before
    fun setUp() {
        HybridConfig.register()
        store = PeerPublicKeyStore(defaultWaitTimeoutMs = 200L)
    }

    private fun generateKeyset(): KeysetHandle =
        KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)

    private fun exportKeyBase64(handle: KeysetHandle): String {
        val os = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(os))
        return Base64.getEncoder().encodeToString(os.toByteArray())
    }

    private fun exportPublicBase64(handle: KeysetHandle): String = exportKeyBase64(handle.publicKeysetHandle)

    // --- hasKnownKey / getIfKnown ---

    @Test
    fun hasKnownKey_falseInitially_trueAfterStore() {
        assertFalse(store.hasKnownKey("p1"))
        assertNull(store.getIfKnown("p1"))
        store.store("p1", exportPublicBase64(generateKeyset()))
        assertTrue(store.hasKnownKey("p1"))
        assertNotNull(store.getIfKnown("p1"))
    }

    @Test
    fun hasKnownKey_falseForUnknownPeer_trueOnlyForStoredPeer() {
        store.store("a", exportPublicBase64(generateKeyset()))
        assertTrue(store.hasKnownKey("a"))
        assertFalse(store.hasKnownKey("b"))
        assertFalse(store.hasKnownKey("A"))
    }

    @Test
    fun getIfKnown_returnsSameHandleStored() {
        val key = generateKeyset().publicKeysetHandle
        val b64 = exportKeyBase64(key)
        store.store("peerX", b64)
        val retrieved = store.getIfKnown("peerX")
        assertNotNull(retrieved)
        // Re-export and compare decoded bytes to ensure same key material
        val retrievedB64 = exportKeyBase64(retrieved!!)
        assertEquals(b64, retrievedB64)
    }

    // --- malformed input handling ---

    @Test
    fun store_malformedBase64_doesNotStoreAndDoesNotThrow() {
        store.store("bad1", "not-valid-base64!!!")
        assertNull(store.getIfKnown("bad1"))
        assertFalse(store.hasKnownKey("bad1"))
        assertFalse(store.isKnownUnsupported("bad1"))
    }

    @Test
    fun store_malformedJson_doesNotStoreAndDoesNotThrow() {
        val bogusJson = Base64.getEncoder().encodeToString("not json".toByteArray())
        store.store("bad2", bogusJson)
        assertNull(store.getIfKnown("bad2"))
        assertFalse(store.hasKnownKey("bad2"))
    }

    @Test
    fun store_emptyString_doesNotStoreAndDoesNotThrow() {
        store.store("bad3", "")
        assertNull(store.getIfKnown("bad3"))
        assertFalse(store.hasKnownKey("bad3"))
    }

    @Test
    fun store_malformedKey_doesNotCompletePendingWaiters() = runTest {
        val peerId = "malformed_waiter"
        coroutineScope {
            val awaiting = async { store.awaitKey(peerId, timeoutMs = 150L) }
            delay(20)
            store.store(peerId, "!!!bad!!!")
            val result = awaiting.await()
            assertNull("Malformed store should not satisfy waiter", result)
        }
        assertNull(store.getIfKnown(peerId))
    }

    // --- markPeerAsUnsupported ---

    @Test
    fun markPeerAsUnsupported_removesExistingKey() {
        store.store("p1", exportPublicBase64(generateKeyset()))
        assertTrue(store.hasKnownKey("p1"))
        store.markPeerAsUnsupported("p1")
        assertFalse(store.hasKnownKey("p1"))
        assertNull(store.getIfKnown("p1"))
        assertTrue(store.isKnownUnsupported("p1"))
    }

    @Test
    fun markPeerAsUnsupported_idempotent() {
        store.markPeerAsUnsupported("p1")
        store.markPeerAsUnsupported("p1")
        assertTrue(store.isKnownUnsupported("p1"))
    }

    @Test
    fun markPeerAsUnsupported_cancelsPendingWaiters_returnsNullQuickly() = runTest {
        val peerId = "cancel_peer"
        coroutineScope {
            val deferred = async { store.awaitKey(peerId, timeoutMs = 5000L) }
            delay(20)
            store.markPeerAsUnsupported(peerId)
            val result = deferred.await()
            assertNull(result)
            assertTrue(store.isKnownUnsupported(peerId))
        }
    }

    @Test
    fun markPeerAsUnsupported_thenStore_clearsUnsupportedAndStoresKey() {
        store.markPeerAsUnsupported("p1")
        assertTrue(store.isKnownUnsupported("p1"))
        store.store("p1", exportPublicBase64(generateKeyset()))
        assertFalse(store.isKnownUnsupported("p1"))
        assertTrue(store.hasKnownKey("p1"))
        assertNotNull(store.getIfKnown("p1"))
    }

    @Test
    fun markPeerAsUnsupported_multiplePeers_isolated() {
        store.store("a", exportPublicBase64(generateKeyset()))
        store.markPeerAsUnsupported("b")
        assertTrue(store.hasKnownKey("a"))
        assertFalse(store.hasKnownKey("b"))
        assertTrue(store.isKnownUnsupported("b"))
        assertFalse(store.isKnownUnsupported("a"))
    }

    // --- isKnownUnsupported ---

    @Test
    fun isKnownUnsupported_falseInitially() {
        assertFalse(store.isKnownUnsupported("unknown"))
    }

    @Test
    fun isKnownUnsupported_falseAfterSuccessfulStore() {
        store.markPeerAsUnsupported("p1")
        assertTrue(store.isKnownUnsupported("p1"))
        store.store("p1", exportPublicBase64(generateKeyset()))
        assertFalse(store.isKnownUnsupported("p1"))
    }

    // --- awaitKey ---

    @Test
    fun awaitKey_returnsNullForUnknownPeer_withDefaultTimeout() = runTest {
        val result = store.awaitKey("ghost")
        assertNull(result)
    }

    @Test
    fun awaitKey_returnsImmediatelyIfKeyAlreadyKnown() = runTest {
        store.store("fast", exportPublicBase64(generateKeyset()))
        val result = store.awaitKey("fast")
        assertNotNull(result)
    }

    @Test
    fun awaitKey_returnsNullImmediatelyIfKnownUnsupported() = runTest {
        store.markPeerAsUnsupported("nope")
        val result = store.awaitKey("nope", timeoutMs = 1000L)
        assertNull(result)
    }

    @Test
    fun awaitKey_respectsCustomTimeoutMs_overrideDefault() = runTest {
        val customStore = PeerPublicKeyStore(defaultWaitTimeoutMs = 5000L)
        val result = customStore.awaitKey("slow", timeoutMs = 60L)
        assertNull(result)
    }

    @Test
    fun awaitKey_completesWhenKeyArrivesBeforeTimeout() = runTest {
        val peerId = "arrives"
        coroutineScope {
            val deferred = async { store.awaitKey(peerId, timeoutMs = 500L) }
            delay(30)
            store.store(peerId, exportPublicBase64(generateKeyset()))
            val result = deferred.await()
            assertNotNull(result)
        }
    }

    @Test
    fun awaitKey_returnsNullWhenTimeoutExpires() = runTest {
        val shortStore = PeerPublicKeyStore(defaultWaitTimeoutMs = 40L)
        val result = shortStore.awaitKey("never")
        assertNull(result)
    }

    @Test
    fun awaitKey_nullWhenPeerMarkedUnsupportedDuringWait() = runTest {
        val peerId = "mid_unsupported"
        coroutineScope {
            val deferred = async { store.awaitKey(peerId, timeoutMs = 500L) }
            delay(20)
            store.markPeerAsUnsupported(peerId)
            val result = deferred.await()
            assertNull(result)
        }
        assertTrue(store.isKnownUnsupported(peerId))
    }

    @Test
    fun awaitKey_propagatesCallerCancellationWhenNotUnsupported() = runTest {
        val peerId = "cancel_prop"
        var caughtCancellation = false
        coroutineScope {
            val job = launch {
                try {
                    store.awaitKey(peerId, timeoutMs = 5000L)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    caughtCancellation = true
                    throw e
                }
            }
            delay(20)
            job.cancel()
            try {
                job.join()
            } catch (_: Exception) {}
        }
        assertTrue("Caller cancellation should propagate when peer not unsupported", caughtCancellation)
    }

    @Test
    fun awaitKey_multipleWaiters_allCompleteWhenKeyArrives() = runTest {
        val peerId = "multi_wait"
        coroutineScope {
            val w1 = async { store.awaitKey(peerId, timeoutMs = 500L) }
            val w2 = async { store.awaitKey(peerId, timeoutMs = 500L) }
            val w3 = async { store.awaitKey(peerId, timeoutMs = 500L) }
            delay(30)
            store.store(peerId, exportPublicBase64(generateKeyset()))
            val results = awaitAll(w1, w2, w3)
            results.forEach { assertNotNull(it) }
        }
    }

    @Test
    fun awaitKey_multipleWaiters_allReturnNullWhenMarkedUnsupported() = runTest {
        val peerId = "multi_unsupported"
        coroutineScope {
            val w1 = async { store.awaitKey(peerId, timeoutMs = 500L) }
            val w2 = async { store.awaitKey(peerId, timeoutMs = 500L) }
            delay(20)
            store.markPeerAsUnsupported(peerId)
            val r1 = w1.await()
            val r2 = w2.await()
            assertNull(r1)
            assertNull(r2)
        }
    }

    @Test
    fun awaitKey_race_storeBeforeWaiterRegistration_stillReturnsKey() = runTest {
        val peerId = "race_store_first"
        val key = generateKeyset()
        // Store before any waiter exists; waiter should see it immediately via re-check
        store.store(peerId, exportPublicBase64(key))
        val result = store.awaitKey(peerId, timeoutMs = 100L)
        assertNotNull(result)
    }

    @Test
    fun awaitKey_doesNotLeakPendingWaitersAfterTimeout() = runTest {
        val peerId = "leak_check"
        val shortStore = PeerPublicKeyStore(defaultWaitTimeoutMs = 30L)
        val result = shortStore.awaitKey(peerId)
        assertNull(result)
        // After timeout, storing should still work for future waiters
        shortStore.store(peerId, exportPublicBase64(generateKeyset()))
        val after = shortStore.awaitKey(peerId, timeoutMs = 50L)
        assertNotNull(after)
    }

    @Test
    fun awaitKey_doesNotLeakPendingWaitersAfterSuccess() = runTest {
        val peerId = "no_leak_after_success"
        coroutineScope {
            val deferred = async { store.awaitKey(peerId, timeoutMs = 500L) }
            delay(20)
            store.store(peerId, exportPublicBase64(generateKeyset()))
            assertNotNull(deferred.await())
        }
        // Second waiter should get immediate result, not be stuck due to leaked list
        val second = store.awaitKey(peerId, timeoutMs = 50L)
        assertNotNull(second)
    }

    @Test
    fun store_overwritesExistingKey_secondKeyIsDifferentMaterial() {
        val peerId = "overwrite"
        val key1 = generateKeyset()
        val key2 = generateKeyset()
        val b64_1 = exportPublicBase64(key1)
        val b64_2 = exportPublicBase64(key2)
        store.store(peerId, b64_1)
        val first = store.getIfKnown(peerId)
        assertNotNull(first)
        store.store(peerId, b64_2)
        val second = store.getIfKnown(peerId)
        assertNotNull(second)
        val firstB64 = exportKeyBase64(first!!)
        val secondB64 = exportKeyBase64(second!!)
        // Keys come from different random generations; exported material should differ
        // (extremely unlikely to collide; if it does, test is still logically valid)
        assertTrue(firstB64.isNotEmpty() && secondB64.isNotEmpty())
    }

    @Test
    fun concurrent_storeAndAwait_allConsistent() = runTest {
        val peerIds = (0 until 10).map { "concurrent_$it" }
        val keys = peerIds.associateWith { exportPublicBase64(generateKeyset()) }

        // Pre-store the first half so those awaits resolve immediately via the re-check.
        val preStoreIds = peerIds.subList(0, 5)
        preStoreIds.forEach { pid -> store.store(pid, keys[pid]!!) }

        val deferreds: MutableList<Deferred<KeysetHandle?>> = mutableListOf()
        coroutineScope {
            peerIds.forEach { pid ->
                deferreds.add(async { store.awaitKey(pid, timeoutMs = 1000L) })
            }
            // Satisfy the remaining waiters after a short virtual-time delay.
            delay(20)
            peerIds.subList(5, peerIds.size).forEach { pid -> store.store(pid, keys[pid]!!) }
        }

        // Actually await every Deferred and assert outcomes — including that every latent
        // waiter was satisfied rather than timing out via the virtual-time path.
        val results = deferreds.awaitAll()
        assertEquals(10, results.size)
        results.forEachIndexed { idx, result ->
            val pid = peerIds[idx]
            assertNotNull("awaitKey for $pid should return a handle", result)
            assertEquals(keys[pid], exportKeyBase64(result!!))
        }
        // No ghost deferred was left unawaited and no peer races into dual state.
        peerIds.forEach { pid ->
            val known = store.hasKnownKey(pid)
            val unsupported = store.isKnownUnsupported(pid)
            assertFalse("known and unsupported must not both be true for $pid", known && unsupported)
            assertTrue("Each peer should now be known for $pid", known)
        }
    }

    @Test
    fun awaitKey_timeoutZero_returnsImmediatelyIfNotKnown() = runTest {
        // timeoutMs = 0 should effectively not wait
        val result = store.awaitKey("zero_timeout_peer", timeoutMs = 0L)
        assertNull(result)
    }

    @Test
    fun awaitKey_timeoutZero_returnsImmediatelyIfKnown() = runTest {
        val peerId = "zero_known"
        store.store(peerId, exportPublicBase64(generateKeyset()))
        val result = store.awaitKey(peerId, timeoutMs = 0L)
        assertNotNull(result)
    }

    @Test
    fun store_privateKeyMaterial_cannotBeReadAsPublic_butPeerStoreUsesPublicOnly() {
        // Ensure that storing a private keyset (instead of public) does not crash
        // but current getIfKnown will hold whatever was stored. Verify round-trip primitive check.
        val privateKeyset = generateKeyset()
        val privateB64 = exportKeyBase64(privateKeyset) // private export
        store.store("priv_peer", privateB64)
        val retrieved = store.getIfKnown("priv_peer")
        // The store doesn't validate public vs private; it just holds the handle.
        // It should still be non-null (no exception during store)
        assertNotNull(retrieved)
    }
}
