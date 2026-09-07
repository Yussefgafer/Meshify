package com.p2p.meshify.core.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PeerPublicKeyStore(
    private val defaultWaitTimeoutMs: Long = AppConfig.PEER_KEY_WAIT_TIMEOUT_MS
) {

    private val knownKeys = ConcurrentHashMap<String, KeysetHandle>()
    private val pendingWaiters = ConcurrentHashMap<String, CopyOnWriteArrayList<CompletableDeferred<KeysetHandle>>>()
    private val knownUnsupportedPeers = ConcurrentHashMap.newKeySet<String>()
    private val peerLocks = ConcurrentHashMap<String, ReentrantLock>()

    private fun peerLock(peerId: String) = peerLocks.getOrPut(peerId) { ReentrantLock() }

    /** Store incoming public key from Handshake */
    fun store(peerId: String, publicKeyBase64: String) {
        peerLock(peerId).withLock {
            try {
                val bytes = Base64.getDecoder().decode(publicKeyBase64)
                val handle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(bytes))
                knownUnsupportedPeers.remove(peerId)
                knownKeys[peerId] = handle

                pendingWaiters.remove(peerId)?.forEach { waiter ->
                    if (!waiter.isCompleted) waiter.complete(handle)
                }
            } catch (e: Exception) {
                Logger.e("PeerPublicKeyStore → Malformed public key from $peerId, ignoring", e, tag = "Crypto")
            }
        }
    }

    /** Direct non-blocking lookup */
    fun getIfKnown(peerId: String): KeysetHandle? = knownKeys[peerId]

    /** True when a public key is already stored for this peer. */
    fun hasKnownKey(peerId: String): Boolean = knownKeys.containsKey(peerId)

    /** Explicitly marks peer as not supporting encryption (e.g. Handshake with null publicKeyBase64) */
    fun markPeerAsUnsupported(peerId: String) {
        peerLock(peerId).withLock {
            knownUnsupportedPeers.add(peerId)
            knownKeys.remove(peerId)
            pendingWaiters.remove(peerId)?.forEach { it.cancel() }
        }
    }

    fun isKnownUnsupported(peerId: String): Boolean = peerLock(peerId).withLock {
        peerId in knownUnsupportedPeers
    }

    /**
     * Waits up to timeoutMs for the peer's public key if not yet known.
     * Returns null if timeout expires or peer is unsupported.
     */
    suspend fun awaitKey(peerId: String, timeoutMs: Long = defaultWaitTimeoutMs): KeysetHandle? {
        if (isKnownUnsupported(peerId)) {
            return null
        }

        knownKeys[peerId]?.let { return it }

        val deferred = CompletableDeferred<KeysetHandle>()
        val list = pendingWaiters.getOrPut(peerId) { CopyOnWriteArrayList() }
        list.add(deferred)

        // Re-check to avoid race condition where store() occurred before registering waiter
        knownKeys[peerId]?.let {
            list.remove(deferred)
            if (list.isEmpty()) pendingWaiters.remove(peerId, list)
            return it
        }

        if (isKnownUnsupported(peerId)) {
            list.remove(deferred)
            if (list.isEmpty()) pendingWaiters.remove(peerId, list)
            return null
        }

        return withTimeoutOrNull(timeoutMs) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                // The waiter may have been cancelled by markPeerAsUnsupported()
                // when the peer was marked unsupported during our wait — this is
                // a normal null-result case, not a caller cancellation. A genuine
                // caller cancellation (scope cancelled) re-throws.
                if (isKnownUnsupported(peerId)) null else throw e
            } finally {
                list.remove(deferred)
                if (list.isEmpty()) pendingWaiters.remove(peerId, list)
            }
        }
    }
}
