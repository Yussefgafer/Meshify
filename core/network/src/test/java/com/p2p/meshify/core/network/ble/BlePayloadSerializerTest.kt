package com.p2p.meshify.core.network.ble

import com.p2p.meshify.domain.model.Payload
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePayloadSerializerTest {

    private fun makePayload(dataSize: Int): Payload {
        return Payload(
            id = "00000000-0000-0000-0000-000000000001",
            senderId = "00000000-0000-0000-0000-000000000002",
            type = Payload.PayloadType.TEXT,
            data = ByteArray(dataSize) { it.toByte() }
        )
    }

    @Test
    fun singleChunkRoundTrip_reconstructsIdentically() {
        val payload = makePayload(20)
        val chunks = BlePayloadSerializer.serializeToChunks(payload, 512)
        assertEquals(1, chunks.size)

        val result = BlePayloadSerializer.processChunkForKey("peerA", chunks[0])
        assertNotNull(result)
        assertEquals(payload.id, result!!.id)
        assertEquals(payload.senderId, result.senderId)
        assertEquals(payload.type, result.type)
        assertEquals(payload.data.size, result.data.size)
        assertEquals(payload.data[0], result.data[0])
        assertEquals(payload.data[19], result.data[19])
    }

    @Test
    fun multiChunkRoundTrip_reconstructsIdentically() {
        val payload = makePayload(2000)
        val chunks = BlePayloadSerializer.serializeToChunks(payload, 512)
        assert(chunks.size > 1) { "expected multi-chunk split, got ${chunks.size}" }

        var result: Payload? = null
        chunks.forEachIndexed { index, chunk ->
            val r = BlePayloadSerializer.processChunkForKey("peerA", chunk)
            if (index == chunks.lastIndex) result = r
        }
        assertNotNull(result)
        assertEquals(payload.id, result!!.id)
        assertEquals(payload.data.size, result.data.size)
        assertEquals(payload.data.toList(), result.data.toList())
    }

    @Test
    fun multiChunkOutOfOrder_reassembles() {
        val payload = makePayload(2000)
        val chunks = BlePayloadSerializer.serializeToChunks(payload, 512)
        val shuffled = chunks.shuffled()

        var result: Payload? = null
        shuffled.forEachIndexed { index, chunk ->
            val r = BlePayloadSerializer.processChunkForKey("peerB", chunk)
            if (index == shuffled.lastIndex) result = r
        }
        assertNotNull(result)
        assertEquals(payload.data.toList(), result!!.data.toList())
    }

    @Test
    fun differentPeerKeys_doNotCrossContaminate() {
        val payloadA = makePayload(2000)
        val payloadB = makePayload(2000)
        val chunksA = BlePayloadSerializer.serializeToChunks(payloadA, 512)
        val chunksB = BlePayloadSerializer.serializeToChunks(payloadB, 512)

        var resultA: Payload? = null
        var resultB: Payload? = null
        chunksA.forEachIndexed { i, c ->
            if (i == chunksA.lastIndex) resultA = BlePayloadSerializer.processChunkForKey("peerA", c)
            else BlePayloadSerializer.processChunkForKey("peerA", c)
        }
        chunksB.forEachIndexed { i, c ->
            if (i == chunksB.lastIndex) resultB = BlePayloadSerializer.processChunkForKey("peerB", c)
            else BlePayloadSerializer.processChunkForKey("peerB", c)
        }

        assertNotNull(resultA)
        assertNotNull(resultB)
        assertEquals(payloadA.data.toList(), resultA!!.data.toList())
        assertEquals(payloadB.data.toList(), resultB!!.data.toList())
    }

    @Test
    fun msgId_is64BitAndUniqueAcrossSerializations() {
        val a = BlePayloadSerializer.serializeToChunks(makePayload(20), 512)
        val b = BlePayloadSerializer.serializeToChunks(makePayload(20), 512)

        // First 8 bytes carry the 64-bit msgId (M3 fix — was a 32-bit Int); the full header is
        // 20 bytes (8B msgId + 12B of fixed fields), so the chunk must be at least that wide.
        assertTrue("chunk too small to carry the 20-byte header", a[0].size >= 20)

        // Distinct serializations produce distinct msgIds so per-peer reassembly keys never collide.
        val msgIdA = ByteBuffer.wrap(a[0]).long
        val msgIdB = ByteBuffer.wrap(b[0]).long
        assert(msgIdA != msgIdB) { "expected distinct msgIds, both were $msgIdA" }

        // The 8-byte msgId must not shift the remaining 12 bytes of fixed-header fields: the
        // following totalSize / chunkIndex / totalChunks still decode to a coherent chunk.
        val bufA = ByteBuffer.wrap(a[0])
        bufA.long // skip msgId
        val totalSizeA = bufA.int
        val chunkIndexA = bufA.int
        val totalChunksA = bufA.int
        assertTrue("totalSize should be positive", totalSizeA > 0)
        assertTrue("chunkIndex in range", chunkIndexA in 0 until totalChunksA)
    }
}
