package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Factory for creating configured sockets.
 * Centralizes socket creation logic with consistent configuration.
 * 
 * Responsibilities:
 * - Create server sockets with proper configuration
 * - Create client sockets with timeouts
 * - Configure socket options (keep-alive, reuse address, etc.)
 */
class SocketFactory {
    
    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000L // 5s
        private const val READ_TIMEOUT_MS = 30000L // 30s
        private const val WRITE_TIMEOUT_MS = 5000L // 5s
    }
    
    /**
     * Creates a configured server socket.
     * 
     * @param port Port to bind to
     * @return Configured ServerSocket
     */
    fun createServerSocket(port: Int): ServerSocket {
        Logger.i("SocketFactory -> Creating ServerSocket on port $port")
        return ServerSocket(port).apply {
            reuseAddress = true
            Logger.i("SocketFactory -> ServerSocket bound and listening")
        }
    }
    
    /**
     * Creates a configured client socket connected to a remote address.
     * 
     * @param address Remote IP address
     * @param port Remote port
     * @return Connected Socket with configured timeouts
     * @throws Exception if connection fails
     */
    fun createClientSocket(address: String, port: Int = AppConfig.DEFAULT_PORT): Socket {
        Logger.d("SocketFactory -> Connecting to $address:$port")
        
        val socket = Socket()
        try {
            // Configure timeouts before connecting
            socket.soTimeout = READ_TIMEOUT_MS.toInt()
            
            // Connect with timeout
            socket.connect(
                InetSocketAddress(address, port),
                CONNECT_TIMEOUT_MS.toInt()
            )
            
            // Configure socket options
            socket.keepAlive = true
            
            Logger.d("SocketFactory -> Client socket connected to $address")
            return socket
        } catch (e: Exception) {
            Logger.e("SocketFactory -> Failed to create client socket to $address", e)
            socket.close()
            throw e
        }
    }
    
    /**
     * Configures an existing socket with standard settings.
     * 
     * @param socket Socket to configure
     * @param readTimeout Read timeout in milliseconds
     */
    fun configureSocket(socket: Socket, readTimeout: Long = READ_TIMEOUT_MS) {
        socket.soTimeout = readTimeout.toInt()
        socket.keepAlive = true
    }
    
    /**
     * Safely closes a socket with error handling.
     * 
     * @param socket Socket to close
     * @param tag Tag for logging
     */
    fun closeSocket(socket: Socket?, tag: String = "SocketFactory") {
        socket?.let {
            try {
                if (!it.isInputShutdown) it.shutdownInput()
            } catch (e: Exception) {
                Logger.w("$tag -> Failed to shutdown input: ${e.message}")
            }
            
            try {
                if (!it.isOutputShutdown) it.shutdownOutput()
            } catch (e: Exception) {
                Logger.w("$tag -> Failed to shutdown output: ${e.message}")
            }
            
            try {
                if (!it.isClosed) it.close()
                Logger.d("$tag -> Socket closed successfully")
            } catch (e: Exception) {
                Logger.e("$tag -> Failed to close socket", e)
            }
        }
    }
    
    /**
     * Checks if a socket is valid and connected.
     * 
     * @param socket Socket to check
     * @return true if socket is connected and not closed
     */
    fun isSocketValid(socket: Socket?): Boolean {
        return socket?.isConnected == true && !socket.isClosed
    }
}
