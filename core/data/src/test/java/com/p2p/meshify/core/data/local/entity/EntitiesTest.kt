package com.p2p.meshify.core.data.local.entity

import com.p2p.meshify.domain.model.MessageType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EntitiesTest — pure-JVM (no Robolectric) tests for the @Entity data classes
 * in Entities.kt and the MessageStatus enum.
 *
 * Scope:
 * - Defaults for every nullable / optional field (ChatEntity, MessageEntity,
 *   MessageAttachmentEntity, PendingMessageEntity).
 * - copy/equality/hashCode for data classes.
 * - MessageStatus enum values and valueOf round-trip.
 */
class EntitiesTest {

    // ==================== ChatEntity ====================

    @Test
    fun chatEntity_defaults_unreadCountZeroAndLastMessageNullable() {
        val chat = ChatEntity(peerId = "p1", peerName = "Alice", lastMessage = null, lastTimestamp = 1L)
        assertEquals(0, chat.unreadCount)
        assertNull(chat.lastMessage)
    }

    @Test
    fun chatEntity_explicitValues_preserved() {
        val chat = ChatEntity("p2", "Bob", "hi there", 999L, unreadCount = 3)
        assertEquals("p2", chat.peerId)
        assertEquals("Bob", chat.peerName)
        assertEquals("hi there", chat.lastMessage)
        assertEquals(999L, chat.lastTimestamp)
        assertEquals(3, chat.unreadCount)
    }

    @Test
    fun chatEntity_copy_changesOnlyTargetField() {
        val a = ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 1)
        val b = a.copy(unreadCount = 5)
        assertEquals(5, b.unreadCount)
        assertEquals("p1", b.peerId)
        assertEquals("Alice", b.peerName)
        assertEquals(a.lastTimestamp, b.lastTimestamp)
        assertNotEquals(a, b)
    }

    @Test
    fun chatEntity_equality_byValue() {
        val a = ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 0)
        val b = ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(peerName = "Other"))
    }

    // ==================== MessageEntity ====================

    @Test
    fun messageEntity_defaults_allOptionalFieldsNullOrDefault() {
        val m = MessageEntity(
            id = "m1", chatId = "c1", senderId = "s1",
            text = null,
            timestamp = 1L, isFromMe = false
        )
        assertNull(m.text)
        assertNull(m.mediaPath)
        assertEquals(MessageType.TEXT, m.type)
        assertEquals(MessageStatus.SENT, m.status)
        assertFalse(m.isDeletedForMe)
        assertFalse(m.isDeletedForEveryone)
        assertNull(m.deletedAt)
        assertNull(m.deletedBy)
        assertNull(m.reaction)
        assertNull(m.replyToId)
        assertNull(m.groupId)
    }

    @Test
    fun messageEntity_explicitValues_preserved() {
        val m = MessageEntity(
            id = "m1", chatId = "c1", senderId = "s1",
            text = "hello", mediaPath = "/tmp/x.jpg",
            type = MessageType.IMAGE, timestamp = 9L,
            isFromMe = true, status = MessageStatus.DELIVERED,
            isDeletedForMe = true, isDeletedForEveryone = false,
            deletedAt = 10L, deletedBy = "s1", reaction = "❤️",
            replyToId = "orig1", groupId = "grp1"
        )
        assertEquals("hello", m.text)
        assertEquals("/tmp/x.jpg", m.mediaPath)
        assertEquals(MessageType.IMAGE, m.type)
        assertEquals(MessageStatus.DELIVERED, m.status)
        assertTrue(m.isDeletedForMe)
        assertFalse(m.isDeletedForEveryone)
        assertEquals(10L, m.deletedAt)
        assertEquals("s1", m.deletedBy)
        assertEquals("❤️", m.reaction)
        assertEquals("orig1", m.replyToId)
        assertEquals("grp1", m.groupId)
    }

    @Test
    fun messageEntity_copy_reactionAndDeletedFlags() {
        val m = MessageEntity(id = "m1", chatId = "c1", senderId = "s1", text = null, timestamp = 1L, isFromMe = true)
        val withReaction = m.copy(reaction = "👍")
        assertEquals("👍", withReaction.reaction)
        assertNull(m.reaction) // original untouched
        val cleared = withReaction.copy(reaction = null)
        assertNull(cleared.reaction)
        val deleted = m.copy(isDeletedForMe = true)
        assertTrue(deleted.isDeletedForMe)
    }

    @Test
    fun messageEntity_equality_byId() {
        val a = MessageEntity(id = "m1", chatId = "c1", senderId = "s1", text = null, timestamp = 1L, isFromMe = false)
        val b = MessageEntity(id = "m1", chatId = "c1", senderId = "s1", text = null, timestamp = 1L, isFromMe = false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(id = "m2"))
    }

    @Test
    fun messageEntity_allMessageTypes_roundTrip() {
        for (type in MessageType.entries) {
            val m = MessageEntity(id = "m-${type.name}", chatId = "c1", senderId = "s1", text = null, type = type, timestamp = 1L, isFromMe = false)
            assertEquals(type, m.type)
        }
    }

    // ==================== MessageAttachmentEntity ====================

    @Test
    fun messageAttachmentEntity_defaults_idGeneratedAndMessageIdNullable() {
        val a = MessageAttachmentEntity(type = MessageType.IMAGE, filePath = "/tmp/a.jpg")
        assertNotNull(a.id)
        assertTrue(a.id.isNotEmpty())
        assertNull(a.messageId)
        assertEquals("/tmp/a.jpg", a.filePath)
    }

    @Test
    fun messageAttachmentEntity_explicitIdAndMessageId() {
        val a = MessageAttachmentEntity(id = "att-1", type = MessageType.VIDEO, messageId = "m1", filePath = "/tmp/v.mp4")
        assertEquals("att-1", a.id)
        assertEquals("m1", a.messageId)
        assertEquals(MessageType.VIDEO, a.type)
    }

    @Test
    fun messageAttachmentEntity_idsAreUniquePerDefaultConstruction() {
        val a = MessageAttachmentEntity(type = MessageType.IMAGE, filePath = "/tmp/1.jpg")
        val b = MessageAttachmentEntity(type = MessageType.IMAGE, filePath = "/tmp/2.jpg")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun messageAttachmentEntity_copy() {
        val a = MessageAttachmentEntity(id = "a1", type = MessageType.FILE, messageId = "m1", filePath = "/tmp/f.pdf")
        val b = a.copy(filePath = "/tmp/other.pdf")
        assertEquals("/tmp/other.pdf", b.filePath)
        assertEquals("a1", b.id)
        assertNotEquals(a, b)
    }

    @Test
    fun messageAttachmentEntity_equality() {
        val a = MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/a.jpg")
        val b = MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/a.jpg")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ==================== PendingMessageEntity ====================

    @Test
    fun pendingMessageEntity_defaults_allFields() {
        val p = PendingMessageEntity(recipientId = "peerA", recipientName = "Bob", content = "hi")
        assertNotNull(p.id)
        assertTrue(p.id.isNotEmpty())
        assertEquals(MessageType.TEXT, p.type)
        assertEquals(MessageStatus.QUEUED, p.status)
        assertEquals(0, p.retryCount)
        assertEquals(3, p.maxRetries)
        assertNull(p.encryptedPayload)
        assertTrue(p.timestamp > 0) // System.currentTimeMillis() at construction
    }

    @Test
    fun pendingMessageEntity_explicitValues() {
        val payload = byteArrayOf(1, 2, 3)
        val p = PendingMessageEntity(
            id = "p1", recipientId = "r1", recipientName = "Name",
            content = "hello", type = MessageType.FILE,
            timestamp = 123L, status = MessageStatus.FAILED,
            retryCount = 2, maxRetries = 5, encryptedPayload = payload
        )
        assertEquals("p1", p.id)
        assertEquals(MessageType.FILE, p.type)
        assertEquals(MessageStatus.FAILED, p.status)
        assertEquals(2, p.retryCount)
        assertEquals(5, p.maxRetries)
        assertArrayEquals(payload, p.encryptedPayload)
        assertEquals(123L, p.timestamp)
    }

    @Test
    fun pendingMessageEntity_mutableStatusAndRetryCount() {
        val p = PendingMessageEntity(recipientId = "peerA", recipientName = "Bob", content = "hi")
        p.status = MessageStatus.SENDING
        p.retryCount = 1
        assertEquals(MessageStatus.SENDING, p.status)
        assertEquals(1, p.retryCount)
    }

    @Test
    fun pendingMessageEntity_idsAreUniquePerDefaultConstruction() {
        val a = PendingMessageEntity(recipientId = "p1", recipientName = "B", content = "hi")
        val b = PendingMessageEntity(recipientId = "p1", recipientName = "B", content = "hi")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun pendingMessageEntity_copy() {
        val p = PendingMessageEntity(id = "p1", recipientId = "r1", recipientName = "B", content = "hi")
        val q = p.copy(content = "bye", type = MessageType.IMAGE)
        assertEquals("bye", q.content)
        assertEquals(MessageType.IMAGE, q.type)
        assertEquals("p1", q.id)
    }

    // ==================== MessageStatus enum ====================

    @Test
    fun messageStatus_allValues_present() {
        val statuses = MessageStatus.entries.map { it.name }
        assertTrue(statuses.containsAll(listOf("QUEUED", "SENDING", "SENT", "DELIVERED", "READ", "FAILED", "RECEIVED")))
        assertEquals(7, statuses.size)
    }

    @Test
    fun messageStatus_valueOf_roundTrip() {
        for (s in MessageStatus.entries) {
            assertEquals(s, MessageStatus.valueOf(s.name))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun messageStatus_valueOf_unknown_throws() {
        MessageStatus.valueOf("NONEXISTENT")
    }
}
