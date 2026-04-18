package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.ParallelFileTransfer
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
 * - Parallel file transfer for large files
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
    
    // Dedicated scope for connection management
    private val connectionScope = CoroutineScope(ioDispatcher + SupervisorJob())
    
    // Cleanup job for removing idle sockets
    private var cleanupJob: Job? = null
    
    // Keep-alive ping job
    private var keepAliveJob: Job? = null
    
    companion object {
        private const val CLEANUP_INTERVAL_MS = 60_000L // 1 minute
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L // 60 seconds (was 30s) - reduce network overhead by 50%
        private const val CONNECT_TIMEOUT_MS = 5000L // 5s
        private const val READ_TIMEOUT_MS = 30000L // 30s
        private const val WRITE_TIMEOUT_MS = 5000L // 5s
    }
    
    init {
        // Initialize keep-alive manager with sendPayload reference
        keepAliveManager = KeepAliveManager(connectionPool) { peerId, payload ->
            sendPayload(peerId, payload)
        }
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
            var acquiredPermit = false
            
            try {
                // Add to connection pool
                if (!connectionPool.addConnection(address, socket)) {
                    Logger.w("SocketManager -> Pool full, rejecting connection from $address")
                    socketFactory.closeSocket(socket, "SocketManager")
                    return@launch
                }
                acquiredPermit = true
                
                val pooledSocket = connectionPool.getConnection(address)
                    ?: throw Exception("Failed to get pooled socket")
                
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

                            // Update last used timestamp
                            connectionPool.updateLastUsed(address)

                            // Deserialize payload header
                            val payload = PayloadSerializer.deserialize(bytes)

                            // Check for parallel transfer marker (for large files)
                            if (payload.type == Payload.PayloadType.FILE || payload.type == Payload.PayloadType.VIDEO) {
                                // Check if there's a parallel mode marker available
                                if (inputStream.available() >= 4) {
                                    inputStream.mark(4)
                                    val marker = inputStream.readInt()
                                    if (marker == 1) {
                                        // Parallel transfer detected - receive file using ParallelFileTransfer
                                        Logger.d("SocketManager -> Parallel transfer detected for ${payload.id}, receiving file...")
                                        val fileResult = ParallelFileTransfer.receiveFile(pooledSocket)
                                        if (fileResult.isSuccess) {
                                            // Create new payload with received file data
                                            val fileBytes = fileResult.getOrNull()
                                            if (fileBytes != null) {
                                                val enrichedPayload = payload.copy(data = fileBytes)
                                                _incomingPayloads.emit(address to enrichedPayload)
                                                Logger.d("SocketManager -> Parallel transfer completed: ${fileBytes.size} bytes")
                                            } else {
                                                Logger.e("SocketManager -> Parallel transfer returned null bytes")
                                                _incomingPayloads.emit(address to payload)
                                            }
                                            continue // Continue to next iteration
                                        } else {
                                            Logger.e("SocketManager -> Parallel transfer failed: ${fileResult.exceptionOrNull()?.message}")
                                            // Still emit original payload but log the error
                                            _incomingPayloads.emit(address to payload)
                                            continue
                                        }
                                    } else {
                                        // Not a parallel transfer marker, reset stream
                                        inputStream.reset()
                                    }
                                }
                            }

                            // Standard payload emit (no parallel transfer)
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
                connectionPool.removeConnection(address, closeSocket = false)
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
            var acquiredPermit = false
            
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
                    
                    if (!connectionPool.addConnection(targetAddress, socket)) {
                        socketFactory.closeSocket(socket, "SocketManager")
                        Logger.e("SocketManager -> Failed to add connection to pool for $targetAddress")
                        return@withContext Result.failure(Exception("Connection pool full"))
                    }
                    acquiredPermit = true
                    
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
     * Pre-warms connection to a known peer.
     */
    suspend fun preWarmConnection(peerAddress: String) = withContext(ioDispatcher) {
        if (!connectionPool.hasValidConnection(peerAddress)) {
            connectionPool.preWarmConnection(peerAddress, socketFactory)
        }
    }
    
    /**
     * Registers a known peer for potential pre-warming.
     */
    fun registerKnownPeer(peerId: String, address: String) {
        connectionPool.registerKnownPeer(peerId)
        connectionScope.launch {
            preWarmConnection(address)
        }
    }
    
    /**
     * Removes a known peer.
     */
    fun removeKnownPeer(peerId: String) {
        connectionPool.removeKnownPeer(peerId)
    }
    
    /**
     * Gets the number of active connections.
     */
    fun getActiveConnectionCount(): Int = connectionPool.getActiveConnectionCount()

    /**
     * Checks if a valid connection exists for a peer.
     */
    fun hasValidConnection(peerAddress: String): Boolean {
        return connectionPool.hasValidConnection(peerAddress)
    }

    /**
     * Gets a connection for a peer.
     */
    fun getConnection(peerAddress: String): java.net.Socket? {
        return connectionPool.getConnection(peerAddress)
    }
    
    /**
     * Sends large file using parallel transfer for better performance.
     */
    suspend fun sendLargeFile(
        targetAddress: String,
        fileBytes: ByteArray,
        payload: Payload
    ): Result<Unit> = withContext(ioDispatcher) {
        val lock = connectionPool.getOrCreateConnectionLock(targetAddress)
        
        lock.withLock {
            try {
                // Get or create connection
                if (!connectionPool.hasValidConnection(targetAddress)) {
                    connectionPool.removeConnection(targetAddress, closeSocket = true)
                    
                    val socket = socketFactory.createClientSocket(targetAddress)
                    if (!connectionPool.addConnection(targetAddress, socket)) {
                        socketFactory.closeSocket(socket, "SocketManager")
                        return@withContext Result.failure(Exception("Connection pool full"))
                    }
                }
                
                val socket = connectionPool.getConnection(targetAddress)
                    ?: return@withContext Result.failure(Exception("No connection available"))
                
                connectionPool.setConnectionInUse(targetAddress, true)
                
                // Determine if parallel transfer is needed
                val useParallel = fileBytes.size > 500 * 1024 // 500KB threshold
                
                if (useParallel) {
                    Logger.d("SocketManager -> Using parallel transfer for ${fileBytes.size / 1024}KB file")
                    val chunkCount = ParallelFileTransfer.calculateOptimalChunkCount(fileBytes.size)
                    
                    // Send payload header first
                    val outputStream = DataOutputStream(socket.getOutputStream())
                    val headerBytes = PayloadSerializer.serialize(payload)
                    outputStream.writeInt(headerBytes.size)
                    outputStream.write(headerBytes)
                    outputStream.writeInt(1) // Parallel mode marker
                    outputStream.flush()
                    
                    // Send file in parallel chunks
                    ParallelFileTransfer.sendFile(
                        socket = socket,
                        fileBytes = fileBytes,
                        chunkCount = chunkCount
                    ) { bytesTransferred, totalBytes, percentage ->
                        Logger.d("SocketManager -> Transfer progress: ${percentage.toInt()}%")
                    }
                } else {
                    // Standard single-threaded transfer
                    val outputStream = DataOutputStream(socket.getOutputStream())
                    val bytes = PayloadSerializer.serialize(payload)
                    
                    withTimeout(WRITE_TIMEOUT_MS) {
                        outputStream.writeInt(bytes.size)
                        outputStream.write(bytes)
                        outputStream.write(fileBytes)
                        outputStream.flush()
                    }
                }
                
                connectionPool.updateLastUsed(targetAddress)
                Result.success(Unit)
                
            } catch (e: Exception) {
                Logger.e("SocketManager -> Large file send failed to $targetAddress", e)
                cleanupConnection(targetAddress)
                Result.failure(e)
            } finally {
                connectionPool.setConnectionInUse(targetAddress, false)
            }
        }
    }
    
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
