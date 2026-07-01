package com.p2p.meshify.domain.security.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MessageEnvelope data class.
 */
class MessageEnvelopeTest {

    // --- Construction ---

    @Test
    fun `MessageEnvelope constructed with all params returns correct values`() {
        val envelope = MessageEnvelope(
            senderId = "sender-1",
            recipientId = "recipient-1",
            text = "Hello, world!",
            timestamp = 1000L,
            messageType = "text"
        )

        assertEquals("sender-1", envelope.senderId)
        assertEquals("recipient-1", envelope.recipientId)
        assertEquals("Hello, world!", envelope.text)
        assertEquals(1000L, envelope.timestamp)
        assertEquals("text", envelope.messageType)
    }

    @Test
    fun `MessageEnvelope uses default messageType as text`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "Hello",
            timestamp = 500L
        )

        assertEquals("text", envelope.messageType)
    }

    @Test
    fun `MessageEnvelope can have custom messageType`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "photo.jpg",
            timestamp = 500L,
            messageType = "image"
        )

        assertEquals("image", envelope.messageType)
    }

    @Test
    fun `MessageEnvelope supports empty text`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "",
            timestamp = 0L
        )

        assertEquals("", envelope.text)
    }

    @Test
    fun `MessageEnvelope supports long text`() {
        val longText = "A".repeat(10000)
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = longText,
            timestamp = 9999999999999L
        )

        assertEquals(longText, envelope.text)
    }

    @Test
    fun `MessageEnvelope supports negative timestamp`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "test",
            timestamp = -1L
        )

        assertEquals(-1L, envelope.timestamp)
    }

    // --- copy() ---

    @Test
    fun `MessageEnvelope copy creates equal instance with no overrides`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "Hello",
            timestamp = 100L,
            messageType = "text"
        )

        assertEquals(envelope, envelope.copy())
    }

    @Test
    fun `MessageEnvelope copy overrides specified field`() {
        val envelope = MessageEnvelope(
            senderId = "s1",
            recipientId = "r1",
            text = "Hello",
            timestamp = 100L
        )

        val copy = envelope.copy(text = "Updated")

        assertEquals("Updated", copy.text)
        assertEquals("s1", copy.senderId)
    }

    // --- equals() / hashCode() ---

    @Test
    fun `MessageEnvelope equals returns true for same field values`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r1", "Hi", 10L, "text")

        assertEquals(a, b)
    }

    @Test
    fun `MessageEnvelope equals returns false for different senderId`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s2", "r1", "Hi", 10L, "text")

        assertNotEquals(a, b)
    }

    @Test
    fun `MessageEnvelope equals returns false for different recipientId`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r2", "Hi", 10L, "text")

        assertNotEquals(a, b)
    }

    @Test
    fun `MessageEnvelope equals returns false for different text`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r1", "Bye", 10L, "text")

        assertNotEquals(a, b)
    }

    @Test
    fun `MessageEnvelope equals returns false for different timestamp`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r1", "Hi", 20L, "text")

        assertNotEquals(a, b)
    }

    @Test
    fun `MessageEnvelope equals returns false for different messageType`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r1", "Hi", 10L, "image")

        assertNotEquals(a, b)
    }

    @Test
    fun `MessageEnvelope hashCode is consistent for equal instances`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s1", "r1", "Hi", 10L, "text")

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `MessageEnvelope hashCode differs for different fields`() {
        val a = MessageEnvelope("s1", "r1", "Hi", 10L, "text")
        val b = MessageEnvelope("s2", "r1", "Hi", 10L, "text")

        assertNotEquals(a.hashCode(), b.hashCode())
    }

    // --- toString() ---

    @Test
    fun `MessageEnvelope toString contains key fields`() {
        val envelope = MessageEnvelope(
            senderId = "alice",
            recipientId = "bob",
            text = "Secret message",
            timestamp = 123456L,
            messageType = "text"
        )

        val str = envelope.toString()
        assertTrue(str.contains("alice"))
        assertTrue(str.contains("bob"))
        assertTrue(str.contains("Secret message"))
        assertTrue(str.contains("123456"))
    }

    // --- All fields accessible ---

    @Test
    fun `MessageEnvelope all fields are accessible`() {
        val envelope = MessageEnvelope("s", "r", "msg", 1L, "t")

        assertNotNull(envelope.senderId)
        assertNotNull(envelope.recipientId)
        assertNotNull(envelope.text)
        assertNotNull(envelope.messageType)
    }
}
