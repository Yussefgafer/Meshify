package com.p2p.meshify.domain.security.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for [MessageEnvelope].
 *
 * The (de)serialization codec lives in `core:data` (`MessageEnvelopeCodec`,
 * `internal`) and is covered by `MessageEnvelopeCodecTest`. This suite instead
 * pins the **model invariants**: default values, immutability/data-class
 * semantics, equality, and the field contract the wire format relies on.
 */
class MessageEnvelopeTest {

    @Test
    fun `messageType defaults to text`() {
        val envelope = MessageEnvelope(
            senderId = "alice",
            recipientId = "bob",
            text = "hi",
            timestamp = 1_700_000_000_000L
        )
        assertEquals("text", envelope.messageType)
    }

    @Test
    fun `all fields are retained on construction`() {
        val envelope = MessageEnvelope(
            senderId = "alice",
            recipientId = "bob",
            text = "مرحبا — 你好 — 😀",
            timestamp = 42L,
            messageType = "image"
        )
        assertEquals("alice", envelope.senderId)
        assertEquals("bob", envelope.recipientId)
        assertEquals("مرحبا — 你好 — 😀", envelope.text)
        assertEquals(42L, envelope.timestamp)
        assertEquals("image", envelope.messageType)
    }

    @Test
    fun `equality is value-based`() {
        val a = MessageEnvelope("alice", "bob", "hi", 100L, "text")
        val b = MessageEnvelope("alice", "bob", "hi", 100L, "text")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different timestamp makes envelopes unequal`() {
        val a = MessageEnvelope("alice", "bob", "hi", 100L)
        val b = MessageEnvelope("alice", "bob", "hi", 101L)
        assertNotEquals(a, b)
    }

    @Test
    fun `different text makes envelopes unequal`() {
        val a = MessageEnvelope("alice", "bob", "hi", 100L)
        val b = MessageEnvelope("alice", "bob", "hello", 100L)
        assertNotEquals(a, b)
    }

    @Test
    fun `explicit text messageType equals the defaulted constructor result`() {
        // Value-semantics: setting messageType = "text" explicitly must equal the
        // defaulted constructor result (the wire codec must reproduce this exactly).
        val defaulted = MessageEnvelope("alice", "bob", "hi", 100L)
        val explicit = MessageEnvelope("alice", "bob", "hi", 100L, "text")
        assertEquals(defaulted, explicit)
    }

    @Test
    fun `copy preserves all fields and allows targeted overrides`() {
        val original = MessageEnvelope("alice", "bob", "hi", 100L, "text")
        val updated = original.copy(recipientId = "carol", messageType = "video")
        assertEquals("alice", updated.senderId)
        assertEquals("carol", updated.recipientId)
        assertEquals("video", updated.messageType)
        assertEquals(original.timestamp, updated.timestamp)
        assertEquals(original.text, updated.text)
        // Original is untouched (data class immutability).
        assertEquals("bob", original.recipientId)
        assertEquals("text", original.messageType)
    }

    @Test
    fun `empty strings are valid field values`() {
        val envelope = MessageEnvelope(
            senderId = "",
            recipientId = "",
            text = "",
            timestamp = 0L,
            messageType = ""
        )
        assertEquals("", envelope.senderId)
        assertEquals("", envelope.recipientId)
        assertEquals("", envelope.text)
        assertEquals(0L, envelope.timestamp)
        assertEquals("", envelope.messageType)
    }

    @Test
    fun `negative and extreme timestamps are preserved`() {
        listOf(-1L, 0L, Long.MAX_VALUE, Long.MIN_VALUE).forEach { ts ->
            assertEquals(ts, MessageEnvelope("s", "r", "t", ts).timestamp)
        }
    }
}
