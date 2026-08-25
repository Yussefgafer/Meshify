package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.PayloadSerializer
import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Robust Socket Manager with Connection Pooling and Cleanup.
 * 
 * Orchestrates three specialized components:
 * - [SocketFactory]: Socket creation and configuration
 * - [ConnectionPool]: Connection lifecycle management
 * - [KeepAliveManager]: Keep-alive ping logic
 * 
 * Thread Safety:
 * - Uses per-connection Mutex for fine-grained locking
 * - Semaphore limits pool size to prevent resource exhaustion
 * - All critical sections protected by mutex
 * 
 * Features:
 * - Connection pooling with idle cleanup
 * - Keep-alive ping to maintain connections
 * - Pre-warming for known peers
 * - Comprehensive error handling with Result<T>
 */
class SocketManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _incomingPayloads = MutableSharedFlow<Pair<String, Payload>>(extraBufferCapacity = 64)
    val incomingPayloads = _incomingPayloads.asSharedFlow()
    
    // Specialized components
    private val socketFactory = SocketFactory()
    private val connectionPool = ConnectionPool()
    private var keepAliveManager: KeepAliveManager? = null
    
    private var serverSocket: ServerSocket? = null
    
    @Volatile
    private var isRunning = false
    
    // Dedicated scope for connection management — recreated by
    // startListening() because a cancelled scope no-ops every launch
    private var connectionScope = CoroutineScope(ioDispatcher + SupervisorJob())
    
    // Cleanup job for removing idle sockets
    private var cleanupJob: Job? = null
    
    // Keep-alive ping job
    private var keepAliveJob: Job? = null
    
    companion object {
        private const val CLEANUP_INTERVAL_MS = 60_000L // 1 minute
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L // 60 seconds (was 30s) - reduce network overhead by 50%
        private const val CONNECT_TIMEOUT_MS = 5000L // 5s
        private const val READ_TIMEOUT_MS = 120_000L // 120s — must stay above KEEP_ALIVE_INTERVAL_MS so quiet periods between pings never kill accepted sockets
        private const val WRITE_TIMEOUT_MS = 5000L // 5s
    }
    
    init {
        // Initialize keep-alive manager with sendPayload reference
        keepAliveManager = KeepAliveManager(connectionPool) { peerId, payload ->
            sendPayload(peerId, payload)
        }
    }

    /**
     * Sets the device id used as senderId for keep-alive PING frames so the
     * remote peer can route its PONG reply back to us. Must be called before
     * startListening().
     */
    fun setKeepAliveSenderId(senderId: String) {
        keepAliveManager?.senderId = senderId
    }
    
    /**
     * Starts listening for incoming connections.
     */
    suspend fun startListening() = withContext(ioDispatcher) {
        if (isRunning) return@withContext
        isRunning = true
        connectionScope = CoroutineScope(ioDispatcher + SupervisorJob())

        // Start cleanup job for idle sockets
        cleanupJob = connectionScope.launch {
            while (isRunning) {
                delay(CLEANUP_INTERVAL_MS)
                connectionPool.cleanupIdleConnections()
            }
        }
        
        // Start keep-alive ping job for active connections
        keepAliveJob = connectionScope.launch {
            while (isRunning) {
                delay(keepAliveManager?.getKeepAliveInterval() ?: KEEP_ALIVE_INTERVAL_MS)
                keepAliveManager?.sendKeepAlivePings()
            }
        }
        
        try {
            serverSocket = socketFactory.createServerSocket(AppConfig.DEFAULT_PORT)
            
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.soTimeout = READ_TIMEOUT_MS.toInt()
                    
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
     * Handles incoming client connection.
     */
    private fun handleIncomingConnection(socket: java.net.Socket) {
        connectionScope.launch {
            val address = socket.inetAddress.hostAddress ?: "unknown"
            val lock = connectionPool.getOrCreateConnectionLock(address)
            var storedConnection: PooledSocket? = null

            try {
                // Add to connection pool (returns the stored instance — never
                // re-fetch by key, a replacement may have landed in between)
                val added = connectionPool.addConnection(address, socket)
                if (added == null) {
                    Logger.w("SocketManager -> Pool full, rejecting connection from $address")
                    socketFactory.closeSocket(socket, "SocketManager")
                    return@launch
                }
                storedConnection = added

                val pooledSocket = added.socket

                val inputStream = DataInputStream(pooledSocket.getInputStream())
                val buffer = ByteArray(AppConfig.DEFAULT_BUFFER_SIZE) // Use configured buffer size
                
                try {
                    while (isRunning && !pooledSocket.isClosed) {
                        try {
                            // Read length first
                            val length = inputStream.readInt()
                            if (length <= 0 || length > AppConfig.MAX_PAYLOAD_SIZE_BYTES) {
                                Logger.e("SocketManager -> Invalid payload length from $address: $length")
                                break
                            }

                            // Read payload bytes
                            val bytes = ByteArray(length)
                            var totalRead = 0

                            while (totalRead < length) {
                                val remaining = length - totalRead
                                val readSize = minOf(buffer.size, remaining)
                                val bytesRead = inputStream.read(buffer, 0, readSize)

                                if (bytesRead == -1) {
                                    Logger.e("SocketManager -> End of stream from $address")
                                    break
                                }

                                System.arraycopy(buffer, 0, bytes, totalRead, bytesRead)
                                totalRead += bytesRead
                            }

                            // Short read = peer died mid-frame; the buffer tail
                            // is zeros and deserializing it would emit a ghost
                            // payload or save corrupt data. Close the connection.
                            if (totalRead < length) {
                                Logger.e("SocketManager -> Incomplete frame from $address ($totalRead/$length bytes) — discarding")
                                break
                            }

                            // Update last used timestamp
                            connectionPool.updateLastUsed(address)

                            // Deserialize payload and emit
                            val payload = PayloadSerializer.deserialize(bytes)
                            _incomingPayloads.emit(address to payload)

                        } catch (e: EOFException) {
                            Logger.d("SocketManager -> Connection closed normally: $address")
                            break
                        } catch (e: SocketTimeoutException) {
                            Logger.d("SocketManager -> Read timeout from $address")
                            break
                        } catch (e: SocketException) {
                            Logger.d("SocketManager -> Connection reset from $address")
                            break
                        } catch (e: Exception) {
                            Logger.e("SocketManager -> Read Error from $address", e)
                            break
                        }
                    }
                } finally {
                    // Ensure cleanup
                    try { inputStream.close() } catch (e: Exception) {
                        Logger.w("SocketManager -> Failed to close inputStream for $address")
                    }
                    socketFactory.closeSocket(pooledSocket, "SocketManager")
                }
            } catch (e: Exception) {
                Logger.e("SocketManager -> Connection Error $address: ${e.message}")
                Logger.d("SocketManager -> Exception details: ${e.stackTraceToString()}")
            } finally {
                Logger.d("SocketManager -> Connection cleanup: $address")
                storedConnection?.let {
                    connectionPool.removePooledSocket(address, it, closeSocket = false)
                }
            }
        }
    }
    
    /**
     * Sends a payload to a target address.
     * Uses Connection Pooling to reuse sockets.
     * Includes Write Timeout to prevent coroutine hanging.
     */
    suspend fun sendPayload(targetAddress: String, payload: Payload): Result<Unit> = withContext(ioDispatcher) {
        val lock = connectionPool.getOrCreateConnectionLock(targetAddress)
        
        lock.withLock {
            try {
                Logger.d("SocketManager -> sendPayload START: target=$targetAddress, payloadType=${payload.type}")
                
                // Check if existing socket is valid
                var socketValid = connectionPool.hasValidConnection(targetAddress)
                
                if (!socketValid) {
                    Logger.d("SocketManager -> No valid connection, opening new connection to $targetAddress")
                    
                    // Remove old connection if exists
                    connectionPool.removeConnection(targetAddress, closeSocket = true)
                    
                    // Create new connection
                    val socket = try {
                        socketFactory.createClientSocket(targetAddress)
                    } catch (e: Exception) {
                        Logger.e("SocketManager -> Failed to connect to $targetAddress", e)
                        return@withContext Result.failure(e)
                    }
                    
                    if (connectionPool.addConnection(targetAddress, socket) == null) {
                        socketFactory.closeSocket(socket, "SocketManager")
                        Logger.e("SocketManager -> Failed to add connection to pool for $targetAddress")
                        return@withContext Result.failure(Exception("Connection pool full"))
                    }
                    
                    Logger.d("SocketManager -> Connection established to $targetAddress")
                }
                
                // Get socket and mark as in use
                val socket = connectionPool.getConnection(targetAddress)
                    ?: return@withContext Result.failure(Exception("No connection available"))
                
                connectionPool.setConnectionInUse(targetAddress, true)
                
                // Send payload
                val outputStream = DataOutputStream(socket.getOutputStream())
                val bytes = PayloadSerializer.serialize(payload)
                
                try {
                    withTimeout(WRITE_TIMEOUT_MS) {
                        outputStream.writeInt(bytes.size)
                        outputStream.write(bytes)
                        // Only flush for small payloads
                        if (bytes.size < 64 * 1024) { // 64KB threshold
                            outputStream.flush()
                        }
                        Logger.d("SocketManager -> Payload sent successfully to $targetAddress")
                    }
                } catch (e: TimeoutCancellationException) {
                    Logger.e("SocketManager -> Write timeout to $targetAddress")
                    throw SocketTimeoutException("Write timeout")
                }
                
                // Update last used timestamp
                connectionPool.updateLastUsed(targetAddress)
                Logger.d("SocketManager -> sendPayload COMPLETE: target=$targetAddress")
                Result.success(Unit)
                
            } catch (e: SocketTimeoutException) {
                Logger.e("SocketManager -> Send Timeout to $targetAddress", e)
                cleanupConnection(targetAddress)
                Result.failure(e)
            } catch (e: Exception) {
                Logger.e("SocketManager -> Send Failed to $targetAddress", e)
                cleanupConnection(targetAddress)
                Result.failure(e)
            } finally {
                // Always mark socket as not in use
                connectionPool.setConnectionInUse(targetAddress, false)
            }
        }
    }
    
    /**
     * Cleans up a failed connection.
     */
    private fun cleanupConnection(targetAddress: String) {
        connectionPool.removeConnection(targetAddress, closeSocket = true)
        Logger.d("SocketManager -> Connection cleaned up: $targetAddress")
    }
    
    /**
     * Registers a known peer for potential pre-warming.
     */
    fun registerKnownPeer(peerId: String, address: String) {
        // Registration tracking removed — pre-warming is handled on-demand
    }

    /**
     * Gets the number of active connections.
     */
    fun getActiveConnectionCount(): Int = connectionPool.getActiveConnectionCount()

    
    /**
     * Stops listening for incoming connections.
     */
    fun stopListening() {
        if (!isRunning) return
        Logger.i("SocketManager -> Stopping...")
        isRunning = false
        
        // Cancel jobs
        cleanupJob?.cancel()
        cleanupJob = null
        
        keepAliveJob?.cancel()
        keepAliveJob = null
        
        // Close server socket
        try {
            serverSocket?.close()
            Logger.d("SocketManager -> ServerSocket closed")
        } catch (e: Exception) {
            Logger.e("SocketManager -> Failed to close ServerSocket", e)
        }
        serverSocket = null
        
        // Cancel connection scope
        connectionScope.cancel()
        
        // Clear all connections
        connectionPool.clearAll()
        
        Logger.i("SocketManager -> Stopped successfully")
    }
    
    /**
     * Full cleanup of all resources.
     */
    fun cleanup() {
        stopListening()
        Logger.d("SocketManager -> Full cleanup completed")
    }
}
