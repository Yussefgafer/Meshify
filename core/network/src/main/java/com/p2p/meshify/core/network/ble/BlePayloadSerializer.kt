package com.p2p.meshify.core.network.ble

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.PayloadSerializer
import com.p2p.meshify.domain.model.Payload
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "BlePayloadSerializer"

/**
 * Wire format for BLE chunked transfer:
 * [4B: totalSize][4B: chunkIndex][4B: totalChunks][variable: chunkData]
 *
 * Each chunk is limited to AppConfig.BLE_MTU_SIZE - AppConfig.BLE_CHUNK_HEADER_SIZE bytes.
 */
object BlePayloadSerializer {

    private const val CHUNK_HEADER_SIZE = 12 // 4 + 4 + 4
    private const val MAX_CHUNK_DATA_SIZE = AppConfig.BLE_MTU_SIZE - AppConfig.BLE_CHUNK_HEADER_SIZE

    private val reassemblyBuffers = ConcurrentHashMap<String, ReassemblyState>()

    private data class ReassemblyState(
        val totalChunks: Int,
        val totalSize: Int,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
        var lastUpdateTime: Long = System.currentTimeMillis() // var — updated on each chunk
    )

    /**
     * Serialize a Payload into BLE-compatible chunks.
     * Each chunk is ready to be sent via BLE GATT characteristic.
     */
    fun serializeToChunks(payload: Payload): List<ByteArray> {
        val fullBytes = PayloadSerializer.serialize(payload)
        val totalSize = fullBytes.size
        val totalChunks = (totalSize + MAX_CHUNK_DATA_SIZE - 1) / MAX_CHUNK_DATA_SIZE

        return if (totalChunks == 1) {
            listOf(buildChunk(0, 1, totalSize, fullBytes))
        } else {
            (0 until totalChunks).map { chunkIndex ->
                val offset = chunkIndex * MAX_CHUNK_DATA_SIZE
                val chunkDataSize = minOf(MAX_CHUNK_DATA_SIZE, totalSize - offset)
                val chunkData = fullBytes.copyOfRange(offset, offset + chunkDataSize)
                buildChunk(chunkIndex, totalChunks, totalSize, chunkData)
            }
        }
    }

    /**
     * Build a single chunk with header.
     * Format: [4B totalSize][4B chunkIndex][4B totalChunks][chunkData]
     */
    private fun buildChunk(chunkIndex: Int, totalChunks: Int, totalSize: Int, chunkData: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE + chunkData.size)
        buffer.putInt(totalSize)
        buffer.putInt(chunkIndex)
        buffer.putInt(totalChunks)
        buffer.put(chunkData)
        return buffer.array()
    }

    /**
     * Process a chunk with a specific reassembly key (for multi-peer support).
     */
    fun processChunkForKey(peerId: String, chunkBytes: ByteArray): Payload? {
        if (chunkBytes.size < CHUNK_HEADER_SIZE) {
            Logger.e("Chunk too small: ${chunkBytes.size} bytes", tag = TAG)
            return null
        }

        val buffer = ByteBuffer.wrap(chunkBytes)
        val totalSize = buffer.int
        val chunkIndex = buffer.int
        val totalChunks = buffer.int

        if (totalSize <= 0 || totalSize > AppConfig.MAX_PAYLOAD_SIZE_BYTES ||
            chunkIndex < 0 || chunkIndex >= totalChunks
        ) {
            Logger.e("Invalid chunk metadata", tag = TAG)
            return null
        }

        val chunkData = ByteArray(buffer.remaining())
        buffer.get(chunkData)

        val reassemblyKey = "ble_${peerId}_${totalSize}_${totalChunks}"

        return processChunkInternal(reassemblyKey, chunkIndex, totalChunks, totalSize, chunkData)
    }

    private fun processChunkInternal(
        reassemblyKey: String,
        chunkIndex: Int,
        totalChunks: Int,
        totalSize: Int,
        chunkData: ByteArray
    ): Payload? {
        val state = reassemblyBuffers.getOrPut(reassemblyKey) {
            ReassemblyState(totalChunks, totalSize)
        }

        // Update sliding window timeout on each chunk arrival
        state.lastUpdateTime = System.currentTimeMillis()

        // Check for timeout
        if (System.currentTimeMillis() - state.lastUpdateTime > AppConfig.BLE_REASSEMBLY_TIMEOUT_MS) {
            Logger.w("Reassembly timeout for key: $reassemblyKey", tag = TAG)
            reassemblyBuffers.remove(reassemblyKey)
            return null
        }

        state.chunks[chunkIndex] = chunkData

        // Check if all chunks received
        if (state.chunks.size == totalChunks) {
            return reassembleAndClear(state, reassemblyKey)
        }

        return null
    }

    private fun reassembleAndClear(state: ReassemblyState, key: String): Payload? {
        val sortedChunks = state.chunks.entries.sortedBy { it.key }
        val totalSize = sortedChunks.sumOf { it.value.size }

        val buffer = ByteBuffer.allocate(totalSize)
        sortedChunks.forEach { (_, data) -> buffer.put(data) }

        reassemblyBuffers.remove(key)

        return try {
            val fullBytes = buffer.array()
            PayloadSerializer.deserialize(fullBytes)
        } catch (e: Exception) {
            Logger.e("Reassembly failed: ${e.message}", tag = TAG)
            null
        }
    }

    /**
     * Clean up stale reassembly buffers.
     * Should be called periodically (e.g., every 30 seconds).
     */
    fun cleanupStaleBuffers() {
        val now = System.currentTimeMillis()
        val staleKeys = reassemblyBuffers.filterValues {
            now - it.lastUpdateTime > AppConfig.BLE_REASSEMBLY_TIMEOUT_MS
        }.keys

        staleKeys.forEach { key ->
            Logger.w("Cleaning up stale reassembly buffer: $key", tag = TAG)
            reassemblyBuffers.remove(key)
        }
    }

    /**
     * Get the maximum data size per chunk (MTU - header).
     */
    fun getMaxChunkDataSize(): Int = MAX_CHUNK_DATA_SIZE
}
