package com.p2p.meshify.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.PayloadSerializer
import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Pooled Socket wrapper with creation timestamp for idle cleanup.
 */
private data class PooledSocket(
    val socket: Socket,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis()
)

/**
 * Robust Socket Manager with Connection Pooling and Cleanup.
 * Fixes Memory Leaks and Thread Safety issues.
 */
class SocketManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _incomingPayloads = MutableSharedFlow<Pair<String, Payload>>(extraBufferCapacity = 64)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    private var serverSocket: ServerSocket? = null

    // Thread-safe map for active connections with PooledSocket
    private val activeConnections = ConcurrentHashMap<String, PooledSocket>()

    @Volatile
    private var isRunning = false

    // Dedicated scope for connection management - prevents memory leaks
    private val connectionScope = CoroutineScope(ioDispatcher + SupervisorJob())

    // Cleanup job for removing idle sockets (5 minutes idle timeout)
    private var cleanupJob: Job? = null

    companion object {
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L // 1 minute
    }

    /**
     * Starts listening for incoming connections.
     */
    suspend fun startListening() = withContext(ioDispatcher) {
        if (isRunning) return@withContext
        isRunning = true

        // Start cleanup job for idle sockets
        cleanupJob = connectionScope.launch {
            while (isRunning) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupIdleSockets()
            }
        }

        try {
            Logger.i("SocketManager -> Binding ServerSocket to port ${AppConfig.DEFAULT_PORT}")
            serverSocket = ServerSocket(AppConfig.DEFAULT_PORT).apply {
                reuseAddress = true
            }
            Logger.i("SocketManager -> Listening...")

            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    // Set timeouts to prevent hanging indefinitely
                    clientSocket.soTimeout = 30000 // 30s read timeout

                    val address = clientSocket.inetAddress.hostAddress
                    Logger.d("SocketManager -> Accepted connection from $address")
                    handleIncomingConnection(clientSocket)
                } catch (e: Exception) {
                    if (isRunning) Logger.e("SocketManager -> Accept Error", e)
                }
            }
        } catch (e: Exception) {
            Logger.e("SocketManager -> Fatal Server Error", e)
        } finally {
            stopListening()
        }
    }

    /**
     * Cleans up idle sockets that haven't been used for more than 5 minutes.
     */
    private fun cleanupIdleSockets() {
        val now = System.currentTimeMillis()
        val iterator = activeConnections.iterator()
        var cleanedCount = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pooledSocket = entry.value
            val idleTime = now - pooledSocket.lastUsedAt

            if (idleTime > IDLE_TIMEOUT_MS) {
                try {
                    Logger.d("SocketManager -> Cleaning up idle socket: ${entry.key} (idle for ${idleTime / 1000}s)")
                    pooledSocket.socket.close()
                    iterator.remove()
                    cleanedCount++
                } catch (e: Exception) {
                    Logger.e("SocketManager -> Error closing idle socket: ${entry.key}", e)
                }
            }
        }

        if (cleanedCount > 0) {
            Logger.d("SocketManager -> Cleaned up $cleanedCount idle sockets")
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        connectionScope.launch {
            val address = socket.inetAddress.hostAddress ?: "unknown"
            try {
                // Wrap in PooledSocket
                val pooledSocket = PooledSocket(socket)
                activeConnections[address] = pooledSocket

                pooledSocket.socket.use { client ->
                    val inputStream = DataInputStream(client.getInputStream())
                    while (isRunning && !client.isClosed) {
                        try {
                            // Read length first
                            val length = inputStream.readInt()
                            if (length <= 0 || length > AppConfig.MAX_PAYLOAD_SIZE_BYTES) {
                                Logger.e("SocketManager -> Invalid payload length from $address: $length")
                                break
                            }

                            val bytes = ByteArray(length)
                            inputStream.readFully(bytes)

                            // Update last used timestamp
                            pooledSocket.lastUsedAt = System.currentTimeMillis()

                            val payload = PayloadSerializer.deserialize(bytes)
                            _incomingPayloads.emit(address to payload)

                        } catch (e: EOFException) {
                            // Normal closure
                            Logger.d("SocketManager -> Connection closed normally: $address")
                            break
                        } catch (e: Exception) {
                            Logger.e("SocketManager -> Read Error from $address", e)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("SocketManager -> Connection Error $address", e)
            } finally {
                Logger.d("SocketManager -> Connection cleanup: $address")
                activeConnections.remove(address)
            }
        }
    }

    /**
     * Sends a payload to a target address.
     * Uses Connection Pooling to reuse sockets.
     */
    suspend fun sendPayload(targetAddress: String, payload: Payload): Result<Unit> = withContext(ioDispatcher) {
        var pooledSocket = activeConnections[targetAddress]

        try {
            // Check if existing socket is valid
            if (pooledSocket == null || pooledSocket.socket.isClosed || !pooledSocket.socket.isConnected) {
                Logger.d("SocketManager -> Opening new connection to $targetAddress")
                // Close old if exists just in case
                pooledSocket?.socket?.close()

                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, AppConfig.DEFAULT_PORT), 5000) // 5s Connect Timeout
                socket.soTimeout = 30000 // 30s Read Timeout
                socket.keepAlive = true

                pooledSocket = PooledSocket(socket)
                activeConnections[targetAddress] = pooledSocket
            }

            val outputStream = DataOutputStream(pooledSocket.socket.getOutputStream())
            val bytes = PayloadSerializer.serialize(payload)

            // Write length then data
            outputStream.writeInt(bytes.size)
            outputStream.write(bytes)
            outputStream.flush()

            // Update last used timestamp
            pooledSocket.lastUsedAt = System.currentTimeMillis()

            return@withContext Result.success(Unit)

        } catch (e: Exception) {
            Logger.e("SocketManager -> Send Failed to $targetAddress", e)
            // Cleanup broken connection
            try { pooledSocket?.socket?.close() } catch (ex: Exception) {}
            activeConnections.remove(targetAddress)
            return@withContext Result.failure(e)
        }
    }

    fun stopListening() {
        if (!isRunning) return
        Logger.i("SocketManager -> Stopping...")
        isRunning = false

        // Cancel cleanup job
        cleanupJob?.cancel()
        cleanupJob = null

        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null

        // Cancel all connection coroutines
        connectionScope.cancel()

        // Cleanup all active connections
        val iterator = activeConnections.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try { entry.value.socket.close() } catch (e: Exception) {}
            iterator.remove()
        }
        Logger.i("SocketManager -> Stopped successfully")
    }
}
