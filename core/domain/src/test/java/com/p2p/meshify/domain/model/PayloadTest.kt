package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for Payload data class.
 */
class PayloadTest {

    @Test
    fun `Payload equals returns true for same id`() {
        val id = UUID.randomUUID().toString()
        val payload1 = Payload(
            id = id,
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )
        val payload2 = Payload(
            id = id,
            senderId = "sender2",
            type = Payload.PayloadType.FILE,
            data = byteArrayOf(4, 5, 6)
        )

        assertEquals(payload1, payload2)
    }

    @Test
    fun `Payload equals returns false for different id`() {
        val payload1 = Payload(
            id = UUID.randomUUID().toString(),
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )
        val payload2 = Payload(
            id = UUID.randomUUID().toString(),
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )

        assertNotEquals(payload1, payload2)
    }

    @Test
    fun `Payload hashCode is based on id only`() {
        val id = UUID.randomUUID().toString()
        val payload1 = Payload(
            id = id,
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )
        val payload2 = Payload(
            id = id,
            senderId = "sender2",
            type = Payload.PayloadType.FILE,
            data = byteArrayOf(4, 5, 6)
        )

        assertEquals(payload1.hashCode(), payload2.hashCode())
    }

    @Test
    fun `Payload default id is generated UUID`() {
        val payload = Payload(
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )

        assertNotNull(payload.id)
        assertTrue(UUID.fromString(payload.id) is UUID)
    }

    @Test
    fun `Payload default timestamp is current time`() {
        val before = System.currentTimeMillis()
        val payload = Payload(
            senderId = "sender1",
            type = Payload.PayloadType.TEXT,
            data = byteArrayOf(1, 2, 3)
        )
        val after = System.currentTimeMillis()

        assertTrue(payload.timestamp in before..after)
    }

    @Test
    fun `PayloadTypeFromString returns correct type for valid name`() {
        assertEquals(Payload.PayloadType.TEXT, PayloadTypeFromString("TEXT"))
        assertEquals(Payload.PayloadType.FILE, PayloadTypeFromString("FILE"))
        assertEquals(Payload.PayloadType.VIDEO, PayloadTypeFromString("VIDEO"))
        assertEquals(Payload.PayloadType.HANDSHAKE, PayloadTypeFromString("HANDSHAKE"))
    }

    @Test
    fun `PayloadTypeFromString returns null for invalid name`() {
        assertNull(PayloadTypeFromString("INVALID"))
        assertNull(PayloadTypeFromString(""))
        assertNull(PayloadTypeFromString("text")) // case sensitive
    }

    @Test
    fun `String toPayloadType extension works correctly`() {
        assertEquals(Payload.PayloadType.TEXT, "TEXT".toPayloadType())
        assertEquals(Payload.PayloadType.FILE, "FILE".toPayloadType())
        assertNull("invalid".toPayloadType())
    }
}
