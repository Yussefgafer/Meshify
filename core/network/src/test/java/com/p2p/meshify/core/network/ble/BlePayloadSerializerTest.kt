package com.p2p.meshify.core.network.ble

import com.p2p.meshify.domain.model.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
