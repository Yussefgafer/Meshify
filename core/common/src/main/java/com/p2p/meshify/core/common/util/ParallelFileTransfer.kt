package com.p2p.meshify.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Parallel file transfer for high-speed transfers.
 * 
 * Splits large files into chunks and transfers them in parallel
 * using multiple threads for maximum throughput.
 * 
 * Features:
 * - Configurable chunk count (default: 4)
 * - Progress tracking
 * - Error recovery per chunk
 * - Automatic cleanup on failure
 */
object ParallelFileTransfer {
    
    /**
     * Transfer progress callback.
     */
    fun interface ProgressListener {
        fun onProgress(bytesTransferred: Long, totalBytes: Long, percentage: Double)
    }
    
    /**
     * Send file in parallel chunks.
     *
     * @param socket Target socket connection
     * @param fileBytes Complete file bytes
     * @param chunkCount Number of parallel chunks (1-8)
     * @param listener Progress listener
     * @return Result with success or error
     */
    suspend fun sendFile(
        socket: Socket,
        fileBytes: ByteArray,
        chunkCount: Int = 4,
        listener: ProgressListener? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileSize = fileBytes.size
            val chunkSize = (fileSize + chunkCount - 1) / chunkCount // Ceiling division

            // ✅ MAJOR FIX M3: Increase buffer size to 32KB for better throughput
            // 8KB was too small for high-speed transfers
            val outputStream = BufferedOutputStream(socket.getOutputStream(), 32768)
            val dataOutputStream = DataOutputStream(outputStream)

            dataOutputStream.writeInt(fileSize) // Total file size
            dataOutputStream.writeInt(chunkCount) // Number of chunks
            dataOutputStream.flush() // Flush metadata immediately

            // Send chunks in parallel with error tracking and retry logic
            var totalSent = 0L
            val sendResults = mutableListOf<Result<Unit>>()
            val failedChunks = mutableListOf<Int>() // Track failed chunk indices for retry

            coroutineScope {
                // First pass: send all chunks
                val firstPassResults = (0 until chunkCount).map { index ->
                    async {
                        try {
                            val chunkOffset = index * chunkSize
                            val chunkLength = minOf(chunkSize, fileSize - chunkOffset)

                            // Timeout لكل chunk
                            withTimeout(30000L) {
                                dataOutputStream.writeInt(index)
                                dataOutputStream.writeInt(chunkLength)
                                dataOutputStream.write(fileBytes, chunkOffset, chunkLength)

                                // ✅ MAJOR FIX M3: Strategic flush after each chunk
                                // This prevents buffering delays and ensures immediate transmission
                                // Only flush for multi-chunk transfers to avoid overhead on small files
                                if (chunkCount > 1) {
                                    dataOutputStream.flush()
                                }
                            }

                            totalSent += chunkLength
                            listener?.onProgress(totalSent, fileSize.toLong(), (totalSent.toDouble() / fileSize) * 100)
                            Result.success<Unit>(Unit)
                        } catch (e: Exception) {
                            Logger.e("ParallelFileTransfer -> Chunk $index failed", e)
                            failedChunks.add(index) // Track for retry
                            Result.failure<Unit>(e)
                        }
                    }
                }.awaitAll()

                sendResults.addAll(firstPassResults)

                // ✅ PF07: Retry failed chunks only (not entire transfer)
                if (failedChunks.isNotEmpty()) {
                    Logger.d("ParallelFileTransfer -> Retrying ${failedChunks.size} failed chunks")
                    
                    val retryResults = failedChunks.map { index ->
                        async {
                            try {
                                val chunkOffset = index * chunkSize
                                val chunkLength = minOf(chunkSize, fileSize - chunkOffset)

                                withTimeout(30000L) {
                                    // Send retry marker
                                    dataOutputStream.writeInt(-2) // Retry marker
                                    dataOutputStream.writeInt(index)
                                    dataOutputStream.writeInt(chunkLength)
                                    dataOutputStream.write(fileBytes, chunkOffset, chunkLength)
                                    
                                    if (chunkCount > 1) {
                                        dataOutputStream.flush()
                                    }
                                }

                                totalSent += chunkLength
                                listener?.onProgress(totalSent, fileSize.toLong(), (totalSent.toDouble() / fileSize) * 100)
                                Result.success<Unit>(Unit)
                            } catch (e: Exception) {
                                Logger.e("ParallelFileTransfer -> Retry chunk $index failed", e)
                                Result.failure<Unit>(e)
                            }
                        }
                    }.awaitAll()

                    // Update results with retry results
                    retryResults.forEachIndexed { i, result ->
                        val originalIndex = failedChunks[i]
                        sendResults[originalIndex] = result
                    }
                }
            }

            // التحقق من نجاح جميع chunks بعد retry
            val failedCount = sendResults.count { it.isFailure }
            if (failedCount > 0) {
                return@withContext Result.failure(Exception("$failedCount chunks failed after retry"))
            }

            // Send completion marker
            dataOutputStream.writeInt(-1) // End marker
            dataOutputStream.flush() // ✅ Final flush to ensure all data is sent

            Result.success(Unit)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Receive file in parallel chunks.
     * 
     * @param socket Source socket connection
     * @param listener Progress listener
     * @return Result with received file bytes
     */
    suspend fun receiveFile(
        socket: Socket,
        listener: ProgressListener? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = DataInputStream(socket.getInputStream())
            
            // Receive file metadata
            val fileSize = inputStream.readInt()
            val chunkCount = inputStream.readInt()
            
            // Receive chunks
            val receivedChunks = Array<ByteArray?>(chunkCount) { null }
            var totalReceived = 0L
            
            coroutineScope {
                (0 until chunkCount).map { index ->
                    async {
                        // Receive chunk header
                        val chunkIndex = inputStream.readInt()
                        val chunkSize = inputStream.readInt()
                        
                        // Receive chunk data
                        val chunk = ByteArray(chunkSize)
                        inputStream.readFully(chunk)
                        
                        receivedChunks[chunkIndex] = chunk
                        
                        // Update progress
                        totalReceived += chunkSize
                        listener?.onProgress(totalReceived, fileSize.toLong(), (totalReceived.toDouble() / fileSize) * 100)
                    }
                }.awaitAll()
            }
            
            // Wait for end marker
            val endMarker = inputStream.readInt()
            if (endMarker != -1) {
                return@withContext Result.failure(Exception("Invalid end marker"))
            }
            
            // Combine chunks
            val fileBytes = ByteArray(fileSize)
            var offset = 0
            for (chunk in receivedChunks) {
                if (chunk == null) {
                    return@withContext Result.failure(Exception("Missing chunk"))
                }
                System.arraycopy(chunk, 0, fileBytes, offset, chunk.size)
                offset += chunk.size
            }
            
            Result.success(fileBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calculate optimal chunk count based on file size.
     */
    fun calculateOptimalChunkCount(fileSize: Int): Int {
        return when {
            fileSize < 100 * 1024 -> 1 // < 100KB: single chunk
            fileSize < 500 * 1024 -> 2 // < 500KB: 2 chunks
            fileSize < 2 * 1024 * 1024 -> 4 // < 2MB: 4 chunks
            fileSize < 10 * 1024 * 1024 -> 6 // < 10MB: 6 chunks
            else -> 8 // > 10MB: 8 chunks (max)
        }
    }
    
    /**
     * Estimate transfer time.
     */
    fun estimateTransferTime(fileSize: Int, transferSpeedKbps: Int = 5000): Long {
        // Default: 5000 KB/s (~40 Mbps) for LAN
        val sizeKB = fileSize / 1024
        return (sizeKB * 1000L / transferSpeedKbps) // milliseconds
    }
}
