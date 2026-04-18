package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Connection pool manager with lifecycle handling.
 * 
 * Responsibilities:
 * - Manage active connections with thread-safe operations
 * - Pool size limiting with semaphore
 * - Idle connection cleanup
 * - Connection pre-warming
 * 
 * Thread Safety:
 * - ConcurrentHashMap for active connections
 * - Per-connection Mutex for fine-grained locking
 * - Semaphore for pool size limiting
 */
class ConnectionPool {
    
    companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L // 1 minute
        private const val MAX_POOL_SIZE = 100 // Increased from 50 to support more concurrent connections
    }
    
    // Thread-safe map for active connections
    private val activeConnections = ConcurrentHashMap<String, PooledSocket>()
    
    // Per-connection locks to avoid global contention
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()
    
    // Semaphore to limit max pool size
    private val poolSemaphore = Semaphore(MAX_POOL_SIZE)
    
    // Known peers for pre-warming
    private val knownPeers = ConcurrentHashMap<String, Long>()
    
    /**
     * Gets or creates a per-connection Mutex.
     */
    fun getOrCreateConnectionLock(peerId: String): Mutex {
        return connectionLocks.computeIfAbsent(peerId) { Mutex() }
    }
    
    /**
     * Gets an existing connection or null if not found.
     */
    fun getConnection(peerId: String): Socket? {
        return activeConnections[peerId]?.socket
    }
    
    /**
     * Checks if a connection exists and is valid.
     */
    fun hasValidConnection(peerId: String): Boolean {
        val pooledSocket = activeConnections[peerId]
        return pooledSocket?.socket?.isConnected == true && !pooledSocket.socket.isClosed
    }
    
    /**
     * Adds a new connection to the pool.
     * 
     * @param peerId Peer identifier
     * @param socket Socket to add
     * @return true if added successfully, false if pool is full
     */
    fun addConnection(peerId: String, socket: Socket): Boolean {
        // Check pool size before adding
        if (!poolSemaphore.tryAcquire()) {
            Logger.w("ConnectionPool -> Pool full ($MAX_POOL_SIZE), rejecting connection for $peerId")
            return false
        }
        
        val pooledSocket = PooledSocket(socket)
        activeConnections[peerId] = pooledSocket
        Logger.d("ConnectionPool -> Added connection for $peerId")
        return true
    }
    
    /**
     * Removes a connection from the pool.
     *
     * @param peerId Peer identifier
     * @param closeSocket If true, closes the socket
     * @param releasePermit If true, releases the semaphore permit (should be false if permit wasn't acquired)
     */
    fun removeConnection(peerId: String, closeSocket: Boolean = true, releasePermit: Boolean = true) {
        activeConnections.remove(peerId)?.let { pooledSocket ->
            if (closeSocket) {
                try {
                    pooledSocket.socket.close()
                    Logger.d("ConnectionPool -> Removed and closed connection for $peerId")
                } catch (e: Exception) {
                    Logger.e("ConnectionPool -> Failed to close socket for $peerId", e)
                }
            } else {
                Logger.d("ConnectionPool -> Removed connection for $peerId (not closing)")
            }
            if (releasePermit) {
                poolSemaphore.release()
            }
            cleanupConnectionLock(peerId)
        }
    }
    
    /**
     * Updates the last used timestamp for a connection.
     */
    fun updateLastUsed(peerId: String) {
        activeConnections[peerId]?.lastUsedAt = System.currentTimeMillis()
    }
    
    /**
     * Marks a connection as in use or not in use.
     */
    fun setConnectionInUse(peerId: String, inUse: Boolean) {
        activeConnections[peerId]?.isInUse = inUse
    }
    
    /**
     * Cleans up idle connections that haven't been used recently.
     * Skips connections that are currently in use.
     * 
     * @return Number of connections cleaned up
     */
    fun cleanupIdleConnections(): Int {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        // First pass: collect keys to remove
        for ((key, pooledSocket) in activeConnections) {
            val idleTime = now - pooledSocket.lastUsedAt
            if (idleTime > IDLE_TIMEOUT_MS && !pooledSocket.isInUse) {
                toRemove.add(key)
            }
        }
        
        // Second pass: remove and close
        var cleanedCount = 0
        toRemove.forEach { key ->
            removeConnection(key, closeSocket = true)
            cleanedCount++
        }
        
        if (cleanedCount > 0) {
            Logger.d("ConnectionPool -> Cleaned up $cleanedCount idle connections")
        }
        
        return cleanedCount
    }
    
    /**
     * Pre-warms a connection to a peer.
     * 
     * @param peerAddress Peer IP address
     * @param socketFactory SocketFactory for creating sockets
     * @return true if pre-warmed successfully
     */
    suspend fun preWarmConnection(peerAddress: String, socketFactory: SocketFactory): Boolean {
        // Only pre-warm if not already connected
        if (activeConnections.containsKey(peerAddress)) {
            Logger.d("ConnectionPool -> Already connected to $peerAddress, skipping pre-warm")
            return true
        }
        
        Logger.d("ConnectionPool -> Pre-warming connection to $peerAddress")
        
        try {
            val socket = socketFactory.createClientSocket(peerAddress)
            
            if (!poolSemaphore.tryAcquire()) {
                socket.close()
                Logger.w("ConnectionPool -> Pool full, skipping pre-warm for $peerAddress")
                return false
            }
            
            val pooledSocket = PooledSocket(socket)
            activeConnections[peerAddress] = pooledSocket
            Logger.d("ConnectionPool -> Pre-warmed connection to $peerAddress")
            return true
        } catch (e: Exception) {
            Logger.e("ConnectionPool -> Failed to pre-warm connection to $peerAddress", e)
            return false
        }
    }
    
    /**
     * Registers a known peer for potential pre-warming.
     */
    fun registerKnownPeer(peerId: String) {
        knownPeers[peerId] = System.currentTimeMillis()
    }
    
    /**
     * Removes a known peer.
     */
    fun removeKnownPeer(peerId: String) {
        knownPeers.remove(peerId)
    }
    
    /**
     * Gets all active connection keys for iteration.
     * Internal use only — returns map of PooledSocket for pool management.
     */
    internal fun getActiveConnections(): Map<String, PooledSocket> {
        return activeConnections.toMap()
    }
    
    /**
     * Gets the number of active connections.
     */
    fun getActiveConnectionCount(): Int = activeConnections.size
    
    /**
     * Gets the number of available permits in the pool.
     */
    fun getAvailablePermits(): Int = poolSemaphore.availablePermits()
    
    /**
     * Cleans up a connection lock to prevent memory leak.
     */
    private fun cleanupConnectionLock(peerId: String) {
        connectionLocks.remove(peerId)
    }
    
    /**
     * Clears all connections (for shutdown).
     */
    fun clearAll() {
        // FIX: Use toList() to create a snapshot before modifying to avoid any potential
        // issues with iterator.remove() during concurrent access
        val entries = activeConnections.toList()
        val activeCount = entries.size
        entries.forEach { (key, pooledSocket) ->
            try {
                pooledSocket.socket.close()
            } catch (e: Exception) {
                Logger.e("ConnectionPool -> Failed to close socket: $key", e)
            }
        }
        // FIX: Drain and release all permits to prevent permit leak on shutdown
        activeConnections.clear()
        // Release permits for all closed connections
        repeat(activeCount) {
            poolSemaphore.release()
        }
        connectionLocks.clear()
        knownPeers.clear()
        Logger.d("ConnectionPool -> Cleared all connections, released $activeCount permits")
    }
}
