package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload

/**
 * Keep-alive manager for maintaining active connections.
 * 
 * Responsibilities:
 * - Send periodic ping messages to active connections
 * - Detect and remove dead connections
 * - Track connection health
 * 
 * Keep-Alive Protocol:
 * - Sends PING message every 60 seconds via sendPayload only
 * - PONG responses are handled by the normal receive path, never here
 * - Removes connection if the PING send fails
 */
class KeepAliveManager(
    private val connectionPool: ConnectionPool,
    // Real device id used as PING senderId so the remote peer can route its
    // PONG reply back via its peerMap. Set by LanTransportImpl at startup;
    // a hardcoded value would poison remote peer maps with a ghost entry.
    @Volatile internal var senderId: String = "",
    private val sendPayload: suspend (String, Payload) -> Result<Unit>
) {
    
    companion object {
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L // 60 seconds (was 30s)
        private const val KEEP_ALIVE_PING = "PING"
    }
    
    /**
     * Sends keep-alive pings to recently-active connections.
     * Should be called periodically (every KEEP_ALIVE_INTERVAL_MS).
     *
     * PINGs go exclusively through [sendPayload] (per-address mutex, correct
     * framing). This class must never read from or write to pooled sockets
     * directly: a direct read steals real frames from the reader loop, and an
     * unmutexed write interleaves with in-flight framed sends.
     *
     * @return Number of pings sent successfully
     */
    suspend fun sendKeepAlivePings(): Int {
        var pingCount = 0

        for ((peerId, pooledSocket) in connectionPool.getActiveConnections()) {
            // Only ping connections used recently (within half of idle timeout)
            val idleTime = System.currentTimeMillis() - pooledSocket.lastUsedAt
            val halfIdleTimeout = ConnectionPool.IDLE_TIMEOUT_MS / 2
            if (idleTime >= halfIdleTimeout) continue

            val pingPayload = Payload(
                senderId = senderId,
                type = Payload.PayloadType.SYSTEM_CONTROL,
                data = KEEP_ALIVE_PING.toByteArray()
            )

            val result = try {
                sendPayload(peerId, pingPayload)
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (result.isSuccess) {
                connectionPool.updateLastUsed(peerId)
                pingCount++
                Logger.d("KeepAliveManager -> Sent ping to $peerId")
            } else {
                // Send failed — treat as dead and drop the connection
                Logger.d("KeepAliveManager -> Keep-alive failed for $peerId, removing connection: ${result.exceptionOrNull()?.message}")
                connectionPool.removePooledSocket(peerId, pooledSocket, closeSocket = true)
            }
        }

        if (pingCount > 0) {
            Logger.d("KeepAliveManager -> Sent $pingCount pings")
        }

        return pingCount
    }
    
    /**
     * Gets keep-alive interval in milliseconds.
     */
    fun getKeepAliveInterval(): Long = KEEP_ALIVE_INTERVAL_MS
}
