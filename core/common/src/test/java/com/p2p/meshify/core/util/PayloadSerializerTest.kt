package com.p2p.meshify.core.util

import com.p2p.meshify.domain.model.Payload
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for PayloadSerializer.
 */
class PayloadSerializerTest {

    @Test
    fun `serialize and deserialize TEXT payload`() {
        val originalPayload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = Payload.PayloadType.TEXT,
            data = "Hello World".toByteArray()
        )

        val bytes = PayloadSerializer.serialize(originalPayload)
        val result = PayloadSerializer.deserialize(bytes)

        assertEquals(originalPayload.id, result.id)
        assertEquals(originalPayload.senderId, result.senderId)
        assertEquals(originalPayload.timestamp, result.timestamp)
        assertEquals(originalPayload.type, result.type)
        assertArrayEquals(originalPayload.data, result.data)
    }

    @Test
    fun `serialize and deserialize FILE payload with large data`() {
        val largeData = ByteArray(1024 * 100) { it.toByte() } // 100KB
        val originalPayload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = Payload.PayloadType.FILE,
            data = largeData
        )

        val bytes = PayloadSerializer.serialize(originalPayload)
        val result = PayloadSerializer.deserialize(bytes)

        assertEquals(originalPayload.id, result.id)
        assertEquals(Payload.PayloadType.FILE, result.type)
        assertArrayEquals(originalPayload.data, result.data)
    }

    @Test
    fun `deserialize V2 payload (backward compatibility)`() {
        // Create a V2 format payload manually
        val buffer = java.nio.ByteBuffer.allocate(100)
        val data = "test".toByteArray()
        
        buffer.putInt(4 + 4 + 8 + 4 + 16 + 16 + data.size) // Total length
        buffer.putInt(2) // V2 version
        buffer.putLong(System.currentTimeMillis())
        buffer.putInt(0) // Type ordinal (TEXT)
        buffer.putLong(UUID.randomUUID().mostSignificantBits)
        buffer.putLong(UUID.randomUUID().leastSignificantBits)
        buffer.putLong(UUID.randomUUID().mostSignificantBits)
        buffer.putLong(UUID.randomUUID().leastSignificantBits)
        buffer.put(data)

        val bytes = buffer.array()
        val result = PayloadSerializer.deserialize(bytes)

        assertNotNull(result)
        assertEquals(Payload.PayloadType.TEXT, result.type)
    }

    @Test
    fun `deserialize V3 payload with string type`() {
        val originalPayload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = Payload.PayloadType.VIDEO,
            data = byteArrayOf(1, 2, 3, 4, 5)
        )

        val bytes = PayloadSerializer.serialize(originalPayload)
        val result = PayloadSerializer.deserialize(bytes)

        assertEquals(originalPayload.type, result.type)
        assertEquals(Payload.PayloadType.VIDEO, result.type)
    }

    @Test
    fun `deserialize corrupted data returns safe payload`() {
        val corruptedBytes = byteArrayOf(0x01, 0x02, 0x03) // Invalid data

        val result = PayloadSerializer.deserialize(corruptedBytes)

        assertNotNull(result)
        assertEquals("unknown", result.senderId)
        assertEquals(Payload.PayloadType.SYSTEM_CONTROL, result.type)
    }

    @Test
    fun `deserialize empty array returns safe payload`() {
        val emptyBytes = ByteArray(0)

        val result = PayloadSerializer.deserialize(emptyBytes)

        assertNotNull(result)
        assertEquals(Payload.PayloadType.SYSTEM_CONTROL, result.type)
    }

    @Test
    fun `deserialize payload too small returns error`() {
        val smallBytes = ByteArray(3) // Less than minimum size (16 bytes)

        val result = PayloadSerializer.deserializeSafe(smallBytes)

        assertTrue(result is PayloadSerializer.DeserializeResult.Error)
    }

    @Test
    fun `deserialize invalid length returns error`() {
        val buffer = java.nio.ByteBuffer.allocate(20)
        buffer.putInt(-1) // Invalid negative length
        buffer.putInt(3) // Version
        buffer.putLong(System.currentTimeMillis())

        val bytes = buffer.array()
        val result = PayloadSerializer.deserializeSafe(bytes)

        assertTrue(result is PayloadSerializer.DeserializeResult.Error)
    }

    @Test
    fun `deserialize unknown version returns error`() {
        val buffer = java.nio.ByteBuffer.allocate(50)
        buffer.putInt(50) // Total length
        buffer.putInt(99) // Unknown version
        buffer.putLong(System.currentTimeMillis())

        val bytes = buffer.array()
        val result = PayloadSerializer.deserializeSafe(bytes)

        assertTrue(result is PayloadSerializer.DeserializeResult.Error)
    }

    @Test
    fun `serialize preserves payload equality`() {
        val payload1 = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            type = Payload.PayloadType.TEXT,
            data = "test".toByteArray()
        )
        val payload2 = Payload(
            id = payload1.id,
            senderId = payload1.senderId,
            type = payload1.type,
            data = payload1.data
        )

        val bytes1 = PayloadSerializer.serialize(payload1)
        val bytes2 = PayloadSerializer.serialize(payload2)

        assertArrayEquals(bytes1, bytes2)
    }

    @Test
    fun `deserialize HANDSHAKE payload type`() {
        val originalPayload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = Payload.PayloadType.HANDSHAKE,
            data = "{\"name\":\"John\"}".toByteArray()
        )

        val bytes = PayloadSerializer.serialize(originalPayload)
        val result = PayloadSerializer.deserialize(bytes)

        assertEquals(Payload.PayloadType.HANDSHAKE, result.type)
    }

    @Test
    fun `deserialize SYSTEM_CONTROL payload type`() {
        val originalPayload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "TYPING_ON".toByteArray()
        )

        val bytes = PayloadSerializer.serialize(originalPayload)
        val result = PayloadSerializer.deserialize(bytes)

        assertEquals(Payload.PayloadType.SYSTEM_CONTROL, result.type)
    }
}
