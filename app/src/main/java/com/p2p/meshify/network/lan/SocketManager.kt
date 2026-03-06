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
 * Robust Socket Manager with Connection Pooling and Cleanup.
 * Fixes Memory Leaks and Thread Safety issues.
 */
class SocketManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _incomingPayloads = MutableSharedFlow<Pair<String, Payload>>(extraBufferCapacity = 64)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    
    // Thread-safe map for active connections
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    
    @Volatile
    private var isRunning = false

    /**
     * Starts listening for incoming connections.
     */
    suspend fun startListening() = withContext(ioDispatcher) {
        if (isRunning) return@withContext
        isRunning = true

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

    private fun handleIncomingConnection(socket: Socket) {
        CoroutineScope(ioDispatcher).launch {
            val address = socket.inetAddress.hostAddress ?: "unknown"
            try {
                socket.use { client ->
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
                            
                            val payload = PayloadSerializer.deserialize(bytes)
                            _incomingPayloads.emit(address to payload)
                            
                        } catch (e: EOFException) {
                            // Normal closure
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
                Logger.d("SocketManager -> Connection closed: $address")
            }
        }
    }

    /**
     * Sends a payload to a target address.
     * Uses Connection Pooling to reuse sockets.
     */
    suspend fun sendPayload(targetAddress: String, payload: Payload): Result<Unit> = withContext(ioDispatcher) {
        var socket = activeConnections[targetAddress]

        try {
            // Check if existing socket is valid
            if (socket == null || socket.isClosed || !socket.isConnected) {
                Logger.d("SocketManager -> Opening new connection to $targetAddress")
                // Close old if exists just in case
                socket?.close()
                
                socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, AppConfig.DEFAULT_PORT), 5000) // 5s Connect Timeout
                socket.soTimeout = 30000 // 30s Read Timeout
                socket.keepAlive = true
                
                activeConnections[targetAddress] = socket
            }

            val outputStream = DataOutputStream(socket.getOutputStream())
            val bytes = PayloadSerializer.serialize(payload)
            
            // Write length then data
            outputStream.writeInt(bytes.size)
            outputStream.write(bytes)
            outputStream.flush()
            
            return@withContext Result.success(Unit)

        } catch (e: Exception) {
            Logger.e("SocketManager -> Send Failed to $targetAddress", e)
            // Cleanup broken connection
            try { socket?.close() } catch (ex: Exception) {}
            activeConnections.remove(targetAddress)
            return@withContext Result.failure(e)
        }
    }

    fun stopListening() {
        if (!isRunning) return
        Logger.i("SocketManager -> Stopping...")
        isRunning = false
        
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        
        // Cleanup all active connections
        val iterator = activeConnections.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try { entry.value.close() } catch (e: Exception) {}
            iterator.remove()
        }
    }
}
