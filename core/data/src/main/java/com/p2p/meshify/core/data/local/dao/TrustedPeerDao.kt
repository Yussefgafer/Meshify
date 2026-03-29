package com.p2p.meshify.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.p2p.meshify.core.data.local.entity.TrustedPeerEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for TOFU (Trust On First Use) peer store.
 */
@Dao
interface TrustedPeerDao {
    
    @Query("SELECT * FROM trusted_peers ORDER BY lastSeenAt DESC")
    fun getAllTrustedPeers(): Flow<List<TrustedPeerEntity>>
    
    @Query("SELECT * FROM trusted_peers WHERE peerId = :peerId")
    suspend fun getPeerById(peerId: String): TrustedPeerEntity?
    
    @Query("SELECT * FROM trusted_peers WHERE peerId = :peerId")
    fun getPeerByIdFlow(peerId: String): Flow<TrustedPeerEntity?>
    
    @Query("SELECT * FROM trusted_peers WHERE publicKeyHex = :publicKeyHex")
    suspend fun getPeerByPublicKey(publicKeyHex: String): TrustedPeerEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: TrustedPeerEntity)
    
    @Update
    suspend fun updatePeer(peer: TrustedPeerEntity)
    
    @Delete
    suspend fun deletePeer(peer: TrustedPeerEntity)
    
    @Query("DELETE FROM trusted_peers WHERE peerId = :peerId")
    suspend fun deletePeerById(peerId: String)
    
    @Query("UPDATE trusted_peers SET trustLevel = :trustLevel, lastSeenAt = :lastSeenAt WHERE peerId = :peerId")
    suspend fun updateTrustLevel(peerId: String, trustLevel: String, lastSeenAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE trusted_peers SET lastSeenAt = :lastSeenAt WHERE peerId = :peerId")
    suspend fun updateLastSeen(peerId: String, lastSeenAt: Long)
    
    @Query("SELECT COUNT(*) FROM trusted_peers")
    suspend fun getTrustedPeerCount(): Int
    
    @Query("SELECT * FROM trusted_peers WHERE trustLevel = 'REJECTED'")
    fun getAllRejectedPeers(): Flow<List<TrustedPeerEntity>>
    
    @Query("DELETE FROM trusted_peers")
    suspend fun deleteAllPeers()
}
