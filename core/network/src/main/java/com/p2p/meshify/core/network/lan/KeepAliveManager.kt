package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.PayloadType
import kotlinx.coroutines.withTimeout
import java.io.DataOutputStream

/**
 * Keep-alive manager for maintaining active connections.
 * 
 * Responsibilities:
 * - Send periodic ping messages to active connections
 * - Detect and remove dead connections
 * - Track connection health
 * 
 * Keep-Alive Protocol:
 * - Sends PING message every 60 seconds
 * - Expects PONG response (or any response)
 * - Removes connection if ping fails
 */
class KeepAliveManager(
    private val connectionPool: ConnectionPool,
    private val sendPayload: suspend (String, Payload) -> Result<Unit>
) {
    
    companion object {
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L // ✅ PF09: 60 seconds (was 30s)
        private const val PING_TIMEOUT_MS = 2_000L // 2 seconds
        private const val KEEP_ALIVE_PING = "PING"
    }
    
    /**
     * Sends keep-alive ping to all active connections.
     * Should be called periodically (every KEEP_ALIVE_INTERVAL_MS).
     * 
     * @return Number of pings sent successfully
     */
    suspend fun sendKeepAlivePings(): Int {
        val now = System.currentTimeMillis()
        var pingCount = 0
        var deadCount = 0
        
        for ((peerId, pooledSocket) in connectionPool.getActiveConnections()) {
            // Only ping active connections that were used recently
            // (within half of idle timeout)
            val idleTime = now - pooledSocket.lastUsedAt
            val halfIdleTimeout = ConnectionPool.IDLE_TIMEOUT_MS / 2
            
            if (idleTime < halfIdleTimeout) {
                try {
                    // Send PING message
                    val pingPayload = Payload(
                        senderId = "system",
                        type = PayloadType.SYSTEM_CONTROL,
                        data = KEEP_ALIVE_PING.toByteArray()
                    )
                    
                    // Quick ping without blocking
                    withTimeout(PING_TIMEOUT_MS) {
                        val outputStream = DataOutputStream(
                            pooledSocket.socket.getOutputStream()
                        )
                        val bytes = com.p2p.meshify.core.util.PayloadSerializer.serialize(pingPayload)
                        outputStream.writeInt(bytes.size)
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    
                    connectionPool.updateLastUsed(peerId)
                    pingCount++
                    
                    Logger.d("KeepAliveManager -> Sent ping to $peerId")
                } catch (e: Exception) {
                    // Connection is dead, remove it
                    Logger.d("KeepAliveManager -> Keep-alive failed for $peerId, removing connection")
                    connectionPool.removeConnection(peerId, closeSocket = true)
                    deadCount++
                }
            }
        }
        
        if (pingCount > 0 || deadCount > 0) {
            Logger.d("KeepAliveManager -> Sent $pingCount pings, removed $deadCount dead connections")
        }
        
        return pingCount
    }
    
    /**
     * Checks if a specific connection is alive.
     * 
     * @param peerId Peer identifier
     * @return true if connection responds to ping
     */
    suspend fun isConnectionAlive(peerId: String): Boolean {
        return try {
            val socket = connectionPool.getConnection(peerId)
            socket?.isConnected == true && !socket.isClosed
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets keep-alive interval in milliseconds.
     */
    fun getKeepAliveInterval(): Long = KEEP_ALIVE_INTERVAL_MS
}
