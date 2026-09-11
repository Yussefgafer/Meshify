package com.p2p.meshify.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadExtrasTest {

    // ---- Handshake ----

    @Test
    fun `Handshake defaults version to 4 and nullable fields to null`() {
        val h = Handshake(name = "Alice")
        assertEquals(4, h.version)
        assertEquals("Alice", h.name)
        assertNull(h.avatarHash)
        assertNull(h.publicKeyBase64)
        assertTrue(h.timestamp > 0)
    }

    @Test
    fun `Handshake retains all fields`() {
        val h = Handshake(
            version = 4,
            name = "Bob",
            avatarHash = "abc123",
            timestamp = 1_700_000_000_000L,
            publicKeyBase64 = "key=="
        )
        assertEquals(4, h.version)
        assertEquals("Bob", h.name)
        assertEquals("abc123", h.avatarHash)
        assertEquals(1_700_000_000_000L, h.timestamp)
        assertEquals("key==", h.publicKeyBase64)
    }

    @Test
    fun `Handshake equality is value-based`() {
        val a = Handshake(version = 4, name = "Alice", timestamp = 100L)
        val b = Handshake(version = 4, name = "Alice", timestamp = 100L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Handshake copy preserves immutability`() {
        val original = Handshake(name = "Alice", avatarHash = "h1", timestamp = 100L)
        val updated = original.copy(name = "Bob", avatarHash = null)
        assertEquals("Bob", updated.name)
        assertNull(updated.avatarHash)
        assertEquals("Alice", original.name)
        assertEquals("h1", original.avatarHash)
    }

    @Test
    fun `Handshake json round-trip preserves fields`() {
        val original = Handshake(name = "Alice", avatarHash = "hash", publicKeyBase64 = "pub==", timestamp = 42L)
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Handshake>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `Handshake json omits default handling - missing nullable fields decode as null`() {
        val json = """{"name":"Carol"}"""
        val decoded = Json.decodeFromString<Handshake>(json)
        assertEquals("Carol", decoded.name)
        assertEquals(4, decoded.version)
        assertNull(decoded.avatarHash)
        assertNull(decoded.publicKeyBase64)
    }

    // ---- DeleteType ----

    @Test
    fun `DeleteType has exactly two values`() {
        val values = DeleteType.values()
        assertEquals(2, values.size)
        assertEquals(DeleteType.DELETE_FOR_ME, values[0])
        assertEquals(DeleteType.DELETE_FOR_EVERYONE, values[1])
    }

    @Test
    fun `DeleteType names are stable`() {
        assertEquals("DELETE_FOR_ME", DeleteType.DELETE_FOR_ME.name)
        assertEquals("DELETE_FOR_EVERYONE", DeleteType.DELETE_FOR_EVERYONE.name)
    }

    @Test
    fun `DeleteType exhaustive when covers all cases`() {
        for (type in DeleteType.values()) {
            val label = when (type) {
                DeleteType.DELETE_FOR_ME -> "me"
                DeleteType.DELETE_FOR_EVERYONE -> "everyone"
            }
            assertNotNull(label)
        }
    }

    @Test
    fun `DeleteType json round-trip`() {
        for (type in DeleteType.values()) {
            val json = Json.encodeToString(type)
            val decoded = Json.decodeFromString<DeleteType>(json)
            assertEquals(type, decoded)
        }
    }

    // ---- DeleteRequest ----

    @Test
    fun `DeleteRequest retains all fields and defaults deletedAt`() {
        val before = System.currentTimeMillis()
        val req = DeleteRequest(messageId = "m1", deleteType = DeleteType.DELETE_FOR_ME, deletedBy = "peerA")
        val after = System.currentTimeMillis()
        assertEquals("m1", req.messageId)
        assertEquals(DeleteType.DELETE_FOR_ME, req.deleteType)
        assertEquals("peerA", req.deletedBy)
        assertTrue(req.deletedAt in before..after)
    }

    @Test
    fun `DeleteRequest equality and copy`() {
        val a = DeleteRequest("m1", DeleteType.DELETE_FOR_ME, "peerA", 100L)
        val b = DeleteRequest("m1", DeleteType.DELETE_FOR_ME, "peerA", 100L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val updated = a.copy(deleteType = DeleteType.DELETE_FOR_EVERYONE)
        assertEquals(DeleteType.DELETE_FOR_EVERYONE, updated.deleteType)
        assertEquals("m1", updated.messageId)
        assertEquals(DeleteType.DELETE_FOR_ME, a.deleteType)
    }

    @Test
    fun `DeleteRequest different deleteType makes not equal`() {
        val a = DeleteRequest("m1", DeleteType.DELETE_FOR_ME, "peerA", 100L)
        val b = DeleteRequest("m1", DeleteType.DELETE_FOR_EVERYONE, "peerA", 100L)
        assertNotEquals(a, b)
    }

    @Test
    fun `DeleteRequest json round-trip`() {
        val original = DeleteRequest("m1", DeleteType.DELETE_FOR_EVERYONE, "peerA", 123L)
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<DeleteRequest>(json)
        assertEquals(original, decoded)
    }

    // ---- ReactionUpdate ----

    @Test
    fun `ReactionUpdate retains fields with nullable reaction`() {
        val r = ReactionUpdate(messageId = "m1", reaction = "👍", senderId = "peerA")
        assertEquals("m1", r.messageId)
        assertEquals("👍", r.reaction)
        assertEquals("peerA", r.senderId)
    }

    @Test
    fun `ReactionUpdate null reaction means removal`() {
        val r = ReactionUpdate(messageId = "m1", reaction = null, senderId = "peerA")
        assertNull(r.reaction)
    }

    @Test
    fun `ReactionUpdate equality and copy`() {
        val a = ReactionUpdate("m1", "❤️", "peerA")
        val b = ReactionUpdate("m1", "❤️", "peerA")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val removed = a.copy(reaction = null)
        assertNull(removed.reaction)
        assertEquals("❤️", a.reaction)
    }

    @Test
    fun `ReactionUpdate json round-trip with and without reaction`() {
        val withReaction = ReactionUpdate("m1", "👍", "peerA")
        val jsonWith = Json.encodeToString(ReactionUpdate.serializer(), withReaction)
        assertEquals(withReaction, Json.decodeFromString(ReactionUpdate.serializer(), jsonWith))

        val withoutReaction = ReactionUpdate("m1", null, "peerA")
        val jsonWithout = Json.encodeToString(ReactionUpdate.serializer(), withoutReaction)
        assertEquals(withoutReaction, Json.decodeFromString(ReactionUpdate.serializer(), jsonWithout))
    }

    @Test
    fun `ReactionUpdate different reaction makes not equal`() {
        val a = ReactionUpdate("m1", "👍", "peerA")
        val b = ReactionUpdate("m1", "👎", "peerA")
        assertNotEquals(a, b)
    }
}
