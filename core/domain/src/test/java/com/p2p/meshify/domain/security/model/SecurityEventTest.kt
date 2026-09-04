package com.p2p.meshify.domain.security.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SecurityEvent].
 *
 * After Phase 4 simplification only `MESSAGE_SEND_FAILED` remains in
 * [SecurityEvent.EventType]; the encryption-related event types were removed.
 * This suite pins the state set, the default field values, the factory, and
 * equality semantics.
 */
class SecurityEventTest {

    @Test
    fun `event type set contains only message-send-failed`() {
        val types = SecurityEvent.EventType.values()
        assertEquals(1, types.size)
        assertEquals(SecurityEvent.EventType.MESSAGE_SEND_FAILED, types[0])
        assertEquals("MESSAGE_SEND_FAILED", types[0].name)
    }

    @Test
    fun `factory messageSendFailed populates all fields`() {
        val event = SecurityEvent.messageSendFailed(
            messageId = "msg-1",
            peerId = "peer-9",
            reason = "socket closed"
        )
        assertEquals(SecurityEvent.EventType.MESSAGE_SEND_FAILED, event.type)
        assertEquals("msg-1", event.messageId)
        assertEquals("peer-9", event.peerId)
        assertEquals("socket closed", event.reason)
    }

    @Test
    fun `fields default to empty strings on direct construction`() {
        val event = SecurityEvent(type = SecurityEvent.EventType.MESSAGE_SEND_FAILED)
        assertEquals("", event.messageId)
        assertEquals("", event.peerId)
        assertEquals("", event.reason)
        assertEquals(SecurityEvent.EventType.MESSAGE_SEND_FAILED, event.type)
    }

    @Test
    fun `equality is value-based across all fields`() {
        val a = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m1", "p1", "r1")
        val b = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m1", "p1", "r1")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different reason makes events unequal`() {
        val a = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m1", "p1", "r1")
        val b = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m1", "p1", "r2")
        assertNotEquals(a, b)
    }

    @Test
    fun `different messageId makes events unequal`() {
        val a = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m1", "p1", "r1")
        val b = SecurityEvent(SecurityEvent.EventType.MESSAGE_SEND_FAILED, "m2", "p1", "r1")
        assertNotEquals(a, b)
    }

    @Test
    fun `copy overrides targeted fields and preserves the rest`() {
        val original = SecurityEvent.messageSendFailed("m1", "p1", "r1")
        val updated = original.copy(reason = "timeout")
        assertEquals("m1", updated.messageId)
        assertEquals("p1", updated.peerId)
        assertEquals("timeout", updated.reason)
        assertEquals(SecurityEvent.EventType.MESSAGE_SEND_FAILED, updated.type)
        assertEquals("r1", original.reason)
    }

    @Test
    fun `empty strings are valid field values`() {
        val event = SecurityEvent(
            type = SecurityEvent.EventType.MESSAGE_SEND_FAILED,
            messageId = "",
            peerId = "",
            reason = ""
        )
        assertTrue(event.messageId.isEmpty())
        assertTrue(event.peerId.isEmpty())
        assertTrue(event.reason.isEmpty())
    }
}
