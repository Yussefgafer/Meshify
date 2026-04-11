package com.p2p.meshify.core.common.security

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * Simple peer ID provider using a random UUID stored in SharedPreferences.
 *
 * Replaces the previous Keystore-based [PeerIdentityManager] with a lightweight,
 * crash-free implementation. The peer ID is generated once on first launch and
 * persisted across app restarts.
 *
 * This is a plaintext-only identity system — no cryptographic operations,
 * no signatures, no certificates. The peer ID is an opaque identifier
 * used solely for P2P LAN discovery and message routing.
 */
class SimplePeerIdProvider(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the persistent peer ID, generating a new UUID if this is the first launch.
     */
    fun getPeerId(): String {
        val existing = prefs.getString(KEY_PEER_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_PEER_ID, newId) }
        return newId
    }

    /**
     * Resets the peer ID. A new UUID will be generated on the next [getPeerId] call.
     * Use this only for testing or factory-reset scenarios.
     */
    fun resetPeerId() {
        prefs.edit { remove(KEY_PEER_ID) }
    }

    companion object {
        private const val PREFS_NAME = "com.p2p.meshify.peer_id"
        private const val KEY_PEER_ID = "peer_id"
    }
}
