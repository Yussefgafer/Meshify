package com.p2p.meshify.core.common.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent storage for ECDH-derived session keys using EncryptedSharedPreferences.
 *
 * Security properties:
 * - Session keys encrypted at rest using Android Keystore-backed MasterKey
 * - Automatic cleanup of expired sessions (24-hour lifetime)
 * - TOFU validation via stored peer identity public keys
 * - Thread-safe access via ConcurrentHashMap cache layer
 *
 * Storage format:
 * - Session keys: "session_key:{peerId}" → hex-encoded 32-byte key
 * - Peer public keys: "peer_pubkey:{peerId}" → hex-encoded X.509 DER
 * - Creation timestamps: "session_timestamp:{peerId}" → Unix timestamp (ms)
 */
class EncryptedSessionKeyStore(context: Context) {

    private val sharedPreferences: SharedPreferences

    /** In-memory cache for fast access (backed by persistent storage) */
    private val sessionCache = ConcurrentHashMap<String, SessionKeyInfo>()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val PREFS_NAME = "encrypted_session_keys"

        // Maximum session lifetime (24 hours)
        private const val MAX_SESSION_AGE_MS = 24 * 60 * 60 * 1000L

        // Maximum number of cached sessions in memory
        private const val MAX_CACHE_SIZE = 1000

        // Periodic cleanup interval (1 hour)
        private const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L

        private const val KEY_PREFIX_SESSION = "session_key:"
        private const val KEY_PREFIX_PEER_PUBKEY = "peer_pubkey:"
        private const val KEY_PREFIX_TIMESTAMP = "session_timestamp:"
    }

    data class SessionKeyInfo(
        val sessionKey: ByteArray,
        val peerPublicKeyHex: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    init {
        // Initialize EncryptedSharedPreferences with hardware-backed MasterKey
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Load existing sessions into cache
        loadAllSessions()

        // Start periodic cleanup job
        startPeriodicCleanup()
    }

    /**
     * Store a derived session key for a peer.
     * @param peerId the peer's ID
     * @param sessionKey the 32-byte derived session key
     * @param peerPublicKeyHex the peer's public key in hex format (for TOFU)
     */
    fun putSessionKey(peerId: String, sessionKey: ByteArray, peerPublicKeyHex: String) {
        // Cleanup if cache is getting too large
        if (sessionCache.size >= MAX_CACHE_SIZE) {
            cleanup()
        }

        val now = System.currentTimeMillis()
        val info = SessionKeyInfo(
            sessionKey = sessionKey.copyOf(),
            peerPublicKeyHex = peerPublicKeyHex,
            createdAt = now
        )

        // Update in-memory cache
        sessionCache[peerId] = info

        // Persist to EncryptedSharedPreferences
        sharedPreferences.edit().apply {
            putString("${KEY_PREFIX_SESSION}$peerId", HexUtil.toHex(sessionKey))
            putString("${KEY_PREFIX_PEER_PUBKEY}$peerId", peerPublicKeyHex)
            putLong("${KEY_PREFIX_TIMESTAMP}$peerId", now)
            apply()
        }
    }

    /**
     * Get the session key for a peer.
     * @param peerId the peer's ID
     * @return SessionKeyInfo if session exists and is valid, null otherwise
     */
    fun getSessionKey(peerId: String): SessionKeyInfo? {
        val info = sessionCache[peerId] ?: loadSession(peerId) ?: return null

        // Check if session has expired
        if (System.currentTimeMillis() - info.createdAt > MAX_SESSION_AGE_MS) {
            removeSession(peerId)
            return null
        }

        return info
    }

    /**
     * Check if a session exists for a peer.
     * @param peerId the peer's ID
     * @return true if session exists and is valid
     */
    fun hasSession(peerId: String): Boolean {
        return getSessionKey(peerId) != null
    }

    /**
     * Validate that the peer's public key matches the cached one (TOFU check).
     * @param peerId the peer's ID
     * @param publicKeyHex the public key to validate
     * @return true if keys match, false if TOFU violation, null if no session exists
     */
    fun validatePeerPublicKey(peerId: String, publicKeyHex: String): Boolean? {
        val info = sessionCache[peerId] ?: loadSession(peerId)

        // No existing session - this is the first handshake (TOFU trust)
        if (info == null) return null

        // Compare keys - return true if match, false if violation
        return info.peerPublicKeyHex == publicKeyHex
    }

    /**
     * Clear session for a peer (e.g., on TOFU violation or user request).
     * @param peerId the peer's ID
     */
    fun clearSession(peerId: String) {
        removeSession(peerId)
    }

    /**
     * Clear all sessions (e.g., on identity reset).
     */
    fun clearAll() {
        sessionCache.clear()
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Get current cache size (for testing/monitoring).
     */
    fun size(): Int = sessionCache.size

    /**
     * Cleanup expired sessions from cache and storage.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        // Find expired sessions in cache
        for ((peerId, info) in sessionCache) {
            if (now - info.createdAt > MAX_SESSION_AGE_MS) {
                toRemove.add(peerId)
            }
        }

        // Remove expired sessions
        toRemove.forEach { peerId ->
            removeSession(peerId)
        }

        if (toRemove.isNotEmpty()) {
            Logger.d("EncryptedSessionKeyStore", "Cleaned up ${toRemove.size} expired sessions")
        }
    }

    /**
     * Load a single session from persistent storage into cache.
     */
    private fun loadSession(peerId: String): SessionKeyInfo? {
        val sessionKeyHex = sharedPreferences.getString("${KEY_PREFIX_SESSION}$peerId", null)
        val peerPublicKeyHex = sharedPreferences.getString("${KEY_PREFIX_PEER_PUBKEY}$peerId", null)
        val timestamp = sharedPreferences.getLong("${KEY_PREFIX_TIMESTAMP}$peerId", -1)

        if (sessionKeyHex == null || peerPublicKeyHex == null || timestamp == -1L) {
            return null
        }

        val sessionKey = try {
            sessionKeyHex.hexToByteArray()
        } catch (e: Exception) {
            Logger.e("Failed to decode session key for ${peerId.take(8)}...", e, "EncryptedSessionKeyStore")
            return null
        }

        val info = SessionKeyInfo(
            sessionKey = sessionKey,
            peerPublicKeyHex = peerPublicKeyHex,
            createdAt = timestamp
        )

        // Cache for faster subsequent access
        sessionCache[peerId] = info

        return info
    }

    /**
     * Load all sessions from persistent storage into cache.
     */
    private fun loadAllSessions() {
        sharedPreferences.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX_SESSION)) {
                val peerId = key.removePrefix(KEY_PREFIX_SESSION)
                loadSession(peerId)
            }
        }
    }

    /**
     * Remove a session from both cache and persistent storage.
     */
    private fun removeSession(peerId: String) {
        sessionCache.remove(peerId)
        sharedPreferences.edit().apply {
            remove("${KEY_PREFIX_SESSION}$peerId")
            remove("${KEY_PREFIX_PEER_PUBKEY}$peerId")
            remove("${KEY_PREFIX_TIMESTAMP}$peerId")
            apply()
        }
    }

    /**
     * Start periodic cleanup job.
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanup()
            }
        }
    }

    /**
     * Cleanup resources when store is no longer needed.
     * CRITICAL FIX: Cancel scope BEFORE saving to prevent immortal coroutine
     */
    fun destroy() {
        // CRITICAL: Cancel the scope first to stop cleanup loop
        scope.cancel()
        
        // Then save any pending changes synchronously
        try {
            sharedPreferences.edit().apply()
        } catch (e: Exception) {
            android.util.Log.e("EncryptedSessionKeyStore", "Failed to save on destroy", e)
        }
    }
}
