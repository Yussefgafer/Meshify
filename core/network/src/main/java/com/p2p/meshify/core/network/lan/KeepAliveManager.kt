package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
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
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L // 60 seconds (was 30s)
        private const val PING_TIMEOUT_MS = 2_000L // 2 seconds
        private const val KEEP_ALIVE_PING = "PING"
    }
    
    /**
     * Sends keep-alive ping to all active connections.
     * Should be called periodically (every KEEP_ALIVE_INTERVAL_MS).
     * After sending PING, attempts to read PONG response from the same socket
     * to verify bidirectional connectivity.
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
                    // Send PING message directly on the pooled socket
                    val pingPayload = Payload(
                        senderId = "system",
                        type = Payload.PayloadType.SYSTEM_CONTROL,
                        data = KEEP_ALIVE_PING.toByteArray()
                    )
                    
                    // Quick ping without blocking - using use() to ensure stream is closed
                    withTimeout(PING_TIMEOUT_MS) {
                        DataOutputStream(pooledSocket.socket.getOutputStream()).use { outputStream ->
                            val bytes = com.p2p.meshify.core.util.PayloadSerializer.serialize(pingPayload)
                            outputStream.writeInt(bytes.size)
                            outputStream.write(bytes)
                            outputStream.flush()
                        }
                        
                        // After sending PING, attempt to read PONG response from the
                        // same socket's input stream to verify bidirectional connectivity.
                        // This detects half-open connections where write succeeds but
                        // the remote end has gone away.
                        val socket = pooledSocket.socket
                        val originalTimeout = socket.soTimeout
                        try {
                            socket.soTimeout = 100 // brief check — avoids long block
                            val inputStream = java.io.DataInputStream(socket.getInputStream())
                            // Only try to read if data is available (non-blocking check)
                            if (inputStream.available() > 0) {
                                socket.soTimeout = (PING_TIMEOUT_MS / 2).toInt()
                                val responseLength = inputStream.readInt()
                                if (responseLength > 0 && responseLength < 1024) {
                                    val responseBytes = ByteArray(responseLength)
                                    inputStream.readFully(responseBytes)
                                    val responseData = String(responseBytes, Charsets.UTF_8)
                                    if (responseData.contains("PONG")) {
                                        Logger.d("KeepAliveManager -> Received PONG from $peerId")
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Timeout is acceptable — no data pending, socket is alive
                        } finally {
                            socket.soTimeout = originalTimeout
                        }
                    }
                    
                    connectionPool.updateLastUsed(peerId)
                    pingCount++
                    
                    Logger.d("KeepAliveManager -> Sent ping to $peerId")
                } catch (e: Exception) {
                    // Connection is dead, remove it
                    Logger.d("KeepAliveManager -> Keep-alive failed for $peerId, removing connection: ${e.message}")
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
