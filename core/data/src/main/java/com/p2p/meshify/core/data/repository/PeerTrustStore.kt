package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.TrustedPeerDao
import com.p2p.meshify.core.data.local.entity.TrustedPeerEntity
import com.p2p.meshify.domain.security.model.TrustLevel
import com.p2p.meshify.domain.security.model.TrustedPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * TOFU (Trust On First Use) store for peer authentication.
 * 
 * Flow:
 * 1. First connection: store peer's public key fingerprint
 * 2. Subsequent connections: verify public key hasn't changed
 * 3. Key mismatch → SecurityException (possible MITM attack)
 * 4. User can manually verify fingerprint via QR code or SAS
 */
class PeerTrustStore(
    private val dao: TrustedPeerDao
) {
    
    /**
     * Get all trusted peers as Flow.
     */
    fun getAllTrustedPeers(): Flow<List<TrustedPeer>> =
        dao.getAllTrustedPeers().map { entities ->
            entities.map { it.toDomainModel() }
        }
    
    /**
     * Get a specific peer by ID.
     */
    suspend fun getPeerById(peerId: String): TrustedPeer? =
        dao.getPeerById(peerId)?.toDomainModel()
    
    /**
     * Trust On First Use — store peer's fingerprint on first connection.
     * If peer already exists, verify public key hasn't changed.
     * 
     * @throws SecurityException if peer's public key changed (TOFU violation)
     */
    suspend fun trustOnFirstUse(
        peerId: String,
        displayName: String,
        publicKeyHex: String
    ) {
        val existing = dao.getPeerById(peerId)
        
        if (existing != null) {
            // Peer seen before — verify key hasn't changed
            if (existing.publicKeyHex != publicKeyHex) {
                throw SecurityException(
                    "TOFU violation: peer $peerId changed public key. " +
                    "Expected ${existing.publicKeyHex.take(32)}... got ${publicKeyHex.take(32)}..."
                )
            }
            // Update last seen timestamp
            dao.updateLastSeen(peerId, System.currentTimeMillis())
        } else {
            // First time seeing this peer — store fingerprint
            dao.insertPeer(
                TrustedPeerEntity(
                    peerId = peerId,
                    displayName = displayName,
                    publicKeyHex = publicKeyHex,
                    firstSeenAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    trustLevel = TrustLevel.TOFU.name,
                    oobVerified = false
                )
            )
        }
    }
    
    /**
     * Mark peer as out-of-band verified (user confirmed fingerprint via QR/SAS).
     */
    suspend fun markAsOobVerified(peerId: String) {
        val peer = dao.getPeerById(peerId) ?: return
        dao.updatePeer(
            peer.copy(
                trustLevel = TrustLevel.OOB_VERIFIED.name,
                oobVerified = true
            )
        )
    }
    
    /**
     * Reject/block a peer.
     */
    suspend fun rejectPeer(peerId: String) {
        dao.updateTrustLevel(peerId, TrustLevel.REJECTED.name)
    }
    
    /**
     * Remove a peer from trust store.
     */
    suspend fun removePeer(peerId: String) {
        dao.deletePeerById(peerId)
    }
    
    /**
     * Get all rejected peers.
     */
    fun getAllRejectedPeers(): Flow<List<TrustedPeer>> =
        dao.getAllRejectedPeers().map { entities ->
            entities.map { it.toDomainModel() }
        }
    
    /**
     * Check if a peer is rejected.
     */
    suspend fun isPeerRejected(peerId: String): Boolean =
        dao.getPeerById(peerId)?.trustLevel == TrustLevel.REJECTED.name
    
    /**
     * Clear all trusted peers (use with caution).
     */
    suspend fun clearAllPeers() {
        dao.deleteAllPeers()
    }
    
    /**
     * Get trusted peer count.
     */
    suspend fun getPeerCount(): Int =
        dao.getTrustedPeerCount()
}
