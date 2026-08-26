package com.p2p.meshify.core.network.ble

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BleConnectionPool"

/**
 * BLE connection pool manager.
 * 
 * BLE has stricter connection limits than TCP (~7 active connections).
 * This pool manages GATT server/client connections with proper lifecycle.
 */
class BleConnectionPool {

    companion object {
        private const val IDLE_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes (shorter than TCP)
        private const val CLEANUP_INTERVAL_MS = 30_000L // 30 seconds
    }

    // Active BLE connections: peerId -> BleConnectionState
    private val activeConnections = ConcurrentHashMap<String, BleConnectionState>()

    // Per-peer locks for thread-safe operations
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()

    // Track connection timestamps for idle cleanup
    private val connectionTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Gets or creates a per-connection Mutex.
     */
    fun getOrCreateConnectionLock(peerId: String): Mutex {
        return connectionLocks.computeIfAbsent(peerId) { Mutex() }
    }

    /**
     * Adds a new BLE connection to the pool.
     *
     * @param peerId Peer identifier (MAC address)
     * @param connectionType Type of connection (server or client)
     * @return true if added, false if pool is full
     */
    fun addConnection(peerId: String, connectionType: BleConnectionType): Boolean {
        synchronized(activeConnections) {
            if (activeConnections.size >= AppConfig.BLE_MAX_CONNECTIONS) {
                Logger.w("BLE Connection pool full (${AppConfig.BLE_MAX_CONNECTIONS}), rejecting $peerId", tag = TAG)
                return false
            }
            activeConnections[peerId] = BleConnectionState(
                type = connectionType,
                connectedAt = System.currentTimeMillis()
            )
            connectionTimestamps[peerId] = System.currentTimeMillis()
        }
        Logger.d("BLE Connection added: $peerId (${connectionType.name})", tag = TAG)
        return true
    }

    /**
     * Removes a connection from the pool.
     */
    fun removeConnection(peerId: String) {
        activeConnections.remove(peerId)?.let {
            connectionTimestamps.remove(peerId)
            cleanupConnectionLock(peerId)
            Logger.d("BLE Connection removed: $peerId", tag = TAG)
        }
    }

    /**
     * Checks if a peer is currently connected.
     */
    fun isConnected(peerId: String): Boolean {
        return activeConnections.containsKey(peerId)
    }

    /**
     * Gets all connected peer IDs.
     */
    fun getConnectedPeers(): Set<String> {
        return activeConnections.keys.toSet()
    }

    /**
     * Gets the number of active connections.
     */
    fun getActiveConnectionCount(): Int = activeConnections.size

    /**
     * Checks if the pool has room for more connections.
     */
    fun hasRoom(): Boolean = activeConnections.size < AppConfig.BLE_MAX_CONNECTIONS

    /**
     * Marks a peer active — refreshes its idle-timer so it is not evicted by cleanupIdleConnections.
     */
    fun markActive(peerId: String) {
        connectionTimestamps[peerId] = System.currentTimeMillis()
    }

    /**
     * Returns the last-activity timestamp for [peerId], or null if absent.
     * Used by callers that need to know whether a peer was recently active.
     */
    fun getLastActiveAt(peerId: String): Long? = connectionTimestamps[peerId]

    /**
     * Cleans up idle connections.
     *
     * @return Number of connections cleaned up
     */
    fun cleanupIdleConnections(): Int {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        // Iterate connectionTimestamps directly — its values are Long timestamps.
        // (activeConnections holds BleConnectionState, not timestamps.)
        for ((peerId, lastActive) in connectionTimestamps) {
            if (now - lastActive > IDLE_TIMEOUT_MS) {
                toRemove.add(peerId)
            }
        }

        toRemove.forEach { peerId ->
            removeConnection(peerId)
            Logger.d("BLE Cleaned idle connection: $peerId", tag = TAG)
        }

        return toRemove.size
    }

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
        val count = activeConnections.size
        activeConnections.clear()
        connectionTimestamps.clear()
        connectionLocks.clear()
        Logger.d("BLE Connection pool cleared ($count connections)", tag = TAG)
    }
}

/**
 * Types of BLE connections.
 */
enum class BleConnectionType {
    /** This device is acting as GATT Server for the peer */
    SERVER,
    /** This device is acting as GATT Client connected to the peer */
    CLIENT
}

/**
 * State of a BLE connection.
 */
data class BleConnectionState(
    val type: BleConnectionType,
    val connectedAt: Long
)
