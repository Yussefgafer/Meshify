package com.p2p.meshify.core.network.ble

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.PayloadSerializer
import com.p2p.meshify.domain.model.Payload
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val TAG = "BlePayloadSerializer"

/**
 * Wire format for BLE chunked transfer:
 * [4B: msgId][4B: totalSize][4B: chunkIndex][4B: totalChunks][variable: chunkData]
 *
 * msgId is a random Int generated per message and shared by all of its chunks, so the
 * reassembly key is unique per transfer — identically shaped transfers cannot interleave.
 *
 * Chunk data capacity is derived from the live negotiated MTU passed by the caller,
 * not from the requested AppConfig.BLE_MTU_SIZE.
 */
object BlePayloadSerializer {

    private const val CHUNK_HEADER_SIZE = 16 // msgId + totalSize + chunkIndex + totalChunks

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
     *
     * @param maxWirePayloadSize usable bytes per GATT write/notify (negotiated MTU - ATT overhead).
     */
    fun serializeToChunks(payload: Payload, maxWirePayloadSize: Int): List<ByteArray> {
        val maxChunkDataSize = maxWirePayloadSize - CHUNK_HEADER_SIZE
        if (maxChunkDataSize <= 0) {
            throw IllegalArgumentException("maxWirePayloadSize $maxWirePayloadSize leaves no room for chunk data")
        }

        val fullBytes = PayloadSerializer.serialize(payload)
        if (fullBytes.isEmpty()) {
            throw IllegalArgumentException("Cannot serialize an empty payload")
        }

        val totalSize = fullBytes.size
        val totalChunks = (totalSize + maxChunkDataSize - 1) / maxChunkDataSize
        val msgId = Random.Default.nextInt()

        return if (totalChunks == 1) {
            listOf(buildChunk(msgId, 0, 1, totalSize, fullBytes))
        } else {
            (0 until totalChunks).map { chunkIndex ->
                val offset = chunkIndex * maxChunkDataSize
                val chunkDataSize = minOf(maxChunkDataSize, totalSize - offset)
                val chunkData = fullBytes.copyOfRange(offset, offset + chunkDataSize)
                buildChunk(msgId, chunkIndex, totalChunks, totalSize, chunkData)
            }
        }
    }

    /**
     * Build a single chunk with header.
     * Format: [4B msgId][4B totalSize][4B chunkIndex][4B totalChunks][chunkData]
     */
    private fun buildChunk(msgId: Int, chunkIndex: Int, totalChunks: Int, totalSize: Int, chunkData: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE + chunkData.size)
        buffer.putInt(msgId)
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
        val msgId = buffer.int
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

        val reassemblyKey = "ble_${peerId}_${msgId}"

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

        // Check for timeout BEFORE updating lastUpdateTime — otherwise
        // now - now = 0 and the timeout NEVER triggers.
        if (System.currentTimeMillis() - state.lastUpdateTime > AppConfig.BLE_REASSEMBLY_TIMEOUT_MS) {
            Logger.e("BLE Incomplete transfer dropped (timeout): $reassemblyKey", tag = TAG)
            reassemblyBuffers.remove(reassemblyKey)
            return null
        }

        // Update sliding window timeout on each chunk arrival
        state.lastUpdateTime = System.currentTimeMillis()

        state.chunks[chunkIndex] = chunkData

        // Check if all chunks received
        if (state.chunks.size == totalChunks) {
            return reassembleAndClear(state, reassemblyKey)
        }

        return null
    }

    private fun reassembleAndClear(state: ReassemblyState, key: String): Payload? {
        val sortedChunks = state.chunks.entries.sortedBy { it.key }
        val receivedBytes = sortedChunks.sumOf { it.value.size }

        // Defensive: guards against mixed-version garbage even though msgId-keyed
        // buffers make cross-transfer contamination practically impossible
        if (receivedBytes != state.totalSize) {
            Logger.e("BLE Chunks sum to $receivedBytes bytes but header claims ${state.totalSize} for $key - dropping", tag = TAG)
            reassemblyBuffers.remove(key)
            return null
        }

        val buffer = ByteBuffer.allocate(receivedBytes)
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
}
