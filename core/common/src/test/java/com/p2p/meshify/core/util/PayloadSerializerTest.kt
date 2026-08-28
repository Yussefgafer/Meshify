package com.p2p.meshify.core.util

import com.p2p.meshify.core.util.PayloadSerializer.DeserializeResult
import com.p2p.meshify.domain.model.Payload
import java.nio.ByteBuffer
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadSerializerTest {

    @Test
    fun serialize_then_deserializeSafe_roundTrips() {
        val id = UUID.randomUUID().toString()
        val senderId = UUID.randomUUID().toString()
        val payload = Payload(
            id = id,
            senderId = senderId,
            timestamp = 123456L,
            type = Payload.PayloadType.VIDEO,
            data = byteArrayOf(1, 2, 3, 4)
        )

        val bytes = PayloadSerializer.serialize(payload)
        val result = PayloadSerializer.deserializeSafe(bytes)

        assertTrue(result is DeserializeResult.Success)
        val restored = (result as DeserializeResult.Success).payload
        assertEquals(id, restored.id)
        assertEquals(senderId, restored.senderId)
        assertEquals(123456L, restored.timestamp)
        assertEquals(Payload.PayloadType.VIDEO, restored.type)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restored.data)
    }

    @Test
    fun deserializeSafe_emptyArray_isError() {
        val result = PayloadSerializer.deserializeSafe(byteArrayOf())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_belowMinPayloadSize_isError() {
        val result = PayloadSerializer.deserializeSafe(ByteArray(15))
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_totalLengthExceedsBuffer_isError() {
        val buffer = ByteBuffer.allocate(20)
        buffer.putInt(999) // totalLength larger than buffer
        buffer.putInt(3) // version V3
        buffer.putLong(0L)
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_unknownVersion_isError() {
        val buffer = ByteBuffer.allocate(20)
        buffer.putInt(20) // valid totalLength
        buffer.putInt(99) // unknown version
        buffer.putLong(0L)
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_v2Ordinal_mapsToType() {
        val msg = UUID.randomUUID()
        val snd = UUID.randomUUID()
        val buffer = ByteBuffer.allocate(52)
        buffer.putInt(52)
        buffer.putInt(2) // V2
        buffer.putLong(123L)
        buffer.putInt(1) // ordinal 1 -> FILE
        buffer.putLong(msg.mostSignificantBits)
        buffer.putLong(msg.leastSignificantBits)
        buffer.putLong(snd.mostSignificantBits)
        buffer.putLong(snd.leastSignificantBits)

        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Success)
        val restored = (result as DeserializeResult.Success).payload
        assertEquals(Payload.PayloadType.FILE, restored.type)
        assertEquals(msg.toString(), restored.id)
        assertEquals(snd.toString(), restored.senderId)
        assertEquals(123L, restored.timestamp)
        assertArrayEquals(byteArrayOf(), restored.data)
    }

    @Test
    fun deserializeSafe_v2OrdinalOutOfRange_mapsToSystemControl() {
        val msg = UUID.randomUUID()
        val snd = UUID.randomUUID()
        val buffer = ByteBuffer.allocate(52)
        buffer.putInt(52)
        buffer.putInt(2) // V2
        buffer.putLong(0L)
        buffer.putInt(100) // out of range ordinal
        buffer.putLong(msg.mostSignificantBits)
        buffer.putLong(msg.leastSignificantBits)
        buffer.putLong(snd.mostSignificantBits)
        buffer.putLong(snd.leastSignificantBits)

        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Success)
        assertEquals(Payload.PayloadType.SYSTEM_CONTROL, (result as DeserializeResult.Success).payload.type)
    }

    @Test
    fun deserializeSafe_v3TypeLengthExceedsMax_isError() {
        val buffer = ByteBuffer.allocate(40)
        buffer.putInt(40)
        buffer.putInt(3) // V3
        buffer.putLong(0L)
        buffer.putInt(65) // > MAX_TYPE_LENGTH (64)
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_v3TypeLengthExceedsRemaining_isError() {
        val buffer = ByteBuffer.allocate(20)
        buffer.putInt(20)
        buffer.putInt(3) // V3
        buffer.putLong(0L)
        buffer.putInt(100) // larger than remaining bytes
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_v3MissingUuidBytes_isError() {
        val buffer = ByteBuffer.allocate(40)
        buffer.putInt(40)
        buffer.putInt(3) // V3
        buffer.putLong(0L)
        buffer.putInt(4) // type length
        buffer.put("TEXT".toByteArray()) // only 4 bytes remain before we need 32 UUID bytes
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }

    @Test
    fun deserializeSafe_dataExceedsMax_isError() {
        val maxDataSize = 10 * 1024 * 1024
        val msg = UUID.randomUUID()
        val snd = UUID.randomUUID()
        val buffer = ByteBuffer.allocate(60 + maxDataSize + 1)
        buffer.putInt(60 + maxDataSize + 1)
        buffer.putInt(3) // V3
        buffer.putLong(0L)
        buffer.putInt(4) // type length
        buffer.put("TEXT".toByteArray())
        buffer.putLong(msg.mostSignificantBits)
        buffer.putLong(msg.leastSignificantBits)
        buffer.putLong(snd.mostSignificantBits)
        buffer.putLong(snd.leastSignificantBits)
        // remaining data exceeds MAX_DATA_SIZE
        val result = PayloadSerializer.deserializeSafe(buffer.array())
        assertTrue(result is DeserializeResult.Error)
    }
}
