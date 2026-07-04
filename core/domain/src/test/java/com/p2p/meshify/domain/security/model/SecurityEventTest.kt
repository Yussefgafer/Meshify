package com.p2p.meshify.domain.security.model

import com.p2p.meshify.domain.security.model.SecurityEvent.EventType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SecurityEvent data class and its EventType enum.
 */
class SecurityEventTest {

    // --- EventType enum ---

    @Test
    fun `EventType contains MESSAGE_SEND_FAILED`() {
        assertEquals(1, EventType.values().size)
        assertEquals(EventType.MESSAGE_SEND_FAILED, EventType.valueOf("MESSAGE_SEND_FAILED"))
    }

    @Test
    fun `EventType MESSAGE_SEND_FAILED has ordinal 0`() {
        assertEquals(0, EventType.MESSAGE_SEND_FAILED.ordinal)
    }

    @Test
    fun `EventType values have unique names`() {
        val names = EventType.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    // --- Construction ---

    @Test
    fun `SecurityEvent constructed with all params returns correct values`() {
        val event = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-001",
            peerId = "peer-42",
            reason = "Connection timeout"
        )

        assertEquals(EventType.MESSAGE_SEND_FAILED, event.type)
        assertEquals("msg-001", event.messageId)
        assertEquals("peer-42", event.peerId)
        assertEquals("Connection timeout", event.reason)
    }

    @Test
    fun `SecurityEvent uses default values for optional params`() {
        val event = SecurityEvent(type = EventType.MESSAGE_SEND_FAILED)

        assertEquals("", event.messageId)
        assertEquals("", event.peerId)
        assertEquals("", event.reason)
    }

    // --- copy() ---

    @Test
    fun `SecurityEvent copy creates equal instance with no overrides`() {
        val event = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1",
            reason = "Failed"
        )

        assertEquals(event, event.copy())
    }

    @Test
    fun `SecurityEvent copy overrides specified field`() {
        val event = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1",
            reason = "Failed"
        )

        val copy = event.copy(messageId = "msg-2")

        assertEquals("msg-2", copy.messageId)
        assertEquals("peer-1", copy.peerId)
    }

    // --- equals() / hashCode() ---

    @Test
    fun `SecurityEvent equals returns true for same field values`() {
        val a = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1",
            reason = "Timeout"
        )
        val b = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1",
            reason = "Timeout"
        )

        assertEquals(a, b)
    }

    @Test
    fun `SecurityEvent equals returns false for different messageId`() {
        val a = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1"
        )
        val b = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-2"
        )

        assertNotEquals(a, b)
    }

    @Test
    fun `SecurityEvent equals returns false for different reason`() {
        val a = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            reason = "Timeout"
        )
        val b = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            reason = "Network error"
        )

        assertNotEquals(a, b)
    }

    @Test
    fun `SecurityEvent hashCode is consistent for equal instances`() {
        val a = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1"
        )
        val b = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1",
            peerId = "peer-1"
        )

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `SecurityEvent hashCode differs for different messageId`() {
        val a = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-1"
        )
        val b = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-2"
        )

        assertNotEquals(a.hashCode(), b.hashCode())
    }

    // --- toString() ---

    @Test
    fun `SecurityEvent toString contains key fields`() {
        val event = SecurityEvent(
            type = EventType.MESSAGE_SEND_FAILED,
            messageId = "msg-007",
            peerId = "peer-xyz",
            reason = "Timeout"
        )

        val str = event.toString()
        assertTrue(str.contains("MESSAGE_SEND_FAILED"))
        assertTrue(str.contains("msg-007"))
        assertTrue(str.contains("peer-xyz"))
        assertTrue(str.contains("Timeout"))
    }

    // --- Companion messageSendFailed ---

    @Test
    fun `messageSendFailed creates SecurityEvent with correct values`() {
        val event = SecurityEvent.messageSendFailed(
            messageId = "msg-99",
            peerId = "peer-88",
            reason = "Connection lost"
        )

        assertEquals(EventType.MESSAGE_SEND_FAILED, event.type)
        assertEquals("msg-99", event.messageId)
        assertEquals("peer-88", event.peerId)
        assertEquals("Connection lost", event.reason)
    }

    @Test
    fun `messageSendFailed default values are empty strings`() {
        val event = SecurityEvent.messageSendFailed(
            messageId = "",
            peerId = "",
            reason = ""
        )

        assertEquals("", event.messageId)
        assertEquals("", event.peerId)
        assertEquals("", event.reason)
    }
}
