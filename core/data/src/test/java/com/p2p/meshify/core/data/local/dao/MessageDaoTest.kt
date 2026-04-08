package com.p2p.meshify.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.domain.model.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * DAO tests using Room in-memory database with Robolectric.
 *
 * These tests verify the actual Room SQL queries and entity mappings
 * against an in-memory SQLite database, ensuring data persistence
 * behavior is correct.
 *
 * Each test is independent — no shared state between tests.
 */
@RunWith(RobolectricTestRunner::class)
class MessageDaoTest {

    private lateinit var db: MeshifyDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var chatDao: ChatDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeshifyDatabase::class.java
        ).allowMainThreadQueries().build()

        messageDao = db.messageDao()
        chatDao = db.chatDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ============================================================================================
    // TEST HELPERS
    // ============================================================================================

    private fun createTestMessage(
        id: String = UUID.randomUUID().toString(),
        chatId: String = "test-peer",
        senderId: String = "my-id",
        text: String = "Test message",
        type: MessageType = MessageType.TEXT,
        timestamp: Long = System.currentTimeMillis(),
        isFromMe: Boolean = true,
        status: MessageStatus = MessageStatus.SENT,
        replyToId: String? = null,
        groupId: String? = null
    ): MessageEntity {
        return MessageEntity(
            id = id,
            chatId = chatId,
            senderId = senderId,
            text = text,
            type = type,
            timestamp = timestamp,
            isFromMe = isFromMe,
            status = status,
            replyToId = replyToId,
            groupId = groupId
        )
    }

    private suspend fun createTestChat(
        peerId: String = "test-peer",
        peerName: String = "Test Peer",
        lastMessage: String? = "Test",
        lastTimestamp: Long = System.currentTimeMillis()
    ) {
        chatDao.insertChat(ChatEntity(peerId, peerName, lastMessage, lastTimestamp))
    }

    // ============================================================================================
    // INSERT AND RETRIEVE BY ID TESTS
    // ============================================================================================

    @Test
    fun `insertMessage and getMessageById returns saved message`() = runTest {
        // Given
        val message = createTestMessage(id = "msg-001", text = "Hello Room")

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById("msg-001")

        // Then
        assertNotNull(result)
        assertEquals("msg-001", result?.id)
        assertEquals("Hello Room", result?.text)
        assertEquals(MessageType.TEXT, result?.type)
        assertEquals(MessageStatus.SENT, result?.status)
        assertTrue(result?.isFromMe == true)
    }

    @Test
    fun `getMessageById returns null for non-existent message`() = runTest {
        // When
        val result = messageDao.getMessageById("does-not-exist")

        // Then
        assertNull(result)
    }

    @Test
    fun `insertMessage with default values works correctly`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg-default",
            chatId = "peer1",
            senderId = "sender1",
            text = null,
            timestamp = 1000L,
            isFromMe = false
        )

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById("msg-default")

        // Then
        assertNotNull(result)
        assertNull(result?.text)
        assertNull(result?.mediaPath)
        assertEquals(MessageType.TEXT, result?.type)
        assertEquals(MessageStatus.SENT, result?.status)
        assertFalse(result?.isDeletedForMe == true)
        assertFalse(result?.isDeletedForEveryone == true)
        assertNull(result?.reaction)
        assertNull(result?.replyToId)
        assertNull(result?.groupId)
    }

    // ============================================================================================
    // GET MESSAGES FOR PEER TESTS
    // ============================================================================================

    @Test
    fun `getAllMessagesForChat returns messages for specific peer only`() = runTest {
        // Given
        createTestChat("peer-a")
        createTestChat("peer-b")

        val msgA1 = createTestMessage(id = "msg-a1", chatId = "peer-a", text = "A1", timestamp = 1000L)
        val msgA2 = createTestMessage(id = "msg-a2", chatId = "peer-a", text = "A2", timestamp = 2000L)
        val msgB1 = createTestMessage(id = "msg-b1", chatId = "peer-b", text = "B1", timestamp = 3000L)

        messageDao.insertMessages(listOf(msgA1, msgA2, msgB1))

        // When
        val result = messageDao.getAllMessagesForChat("peer-a").first()

        // Then
        assertEquals(2, result.size)
        assertEquals("A1", result[0].text)
        assertEquals("A2", result[1].text)
    }

    @Test
    fun `getAllMessagesForChat returns empty list for unknown peer`() = runTest {
        // When
        val result = messageDao.getAllMessagesForChat("unknown-peer").first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllMessagesForChat returns messages ordered by timestamp`() = runTest {
        // Given
        createTestChat("peer-ordered")

        val msg3 = createTestMessage(id = "msg-3", chatId = "peer-ordered", text = "Third", timestamp = 3000L)
        val msg1 = createTestMessage(id = "msg-1", chatId = "peer-ordered", text = "First", timestamp = 1000L)
        val msg2 = createTestMessage(id = "msg-2", chatId = "peer-ordered", text = "Second", timestamp = 2000L)

        messageDao.insertMessages(listOf(msg3, msg1, msg2))

        // When
        val result = messageDao.getAllMessagesForChat("peer-ordered").first()

        // Then
        assertEquals(3, result.size)
        assertEquals("First", result[0].text)
        assertEquals("Second", result[1].text)
        assertEquals("Third", result[2].text)
    }

    // ============================================================================================
    // PAGINATION TESTS
    // ============================================================================================

    @Test
    fun `getMessagesPaged returns correct page with limit and offset`() = runTest {
        // Given
        createTestChat("peer-paged")

        val messages = (1..10).map { i ->
            createTestMessage(
                id = "msg-$i",
                chatId = "peer-paged",
                text = "Message $i",
                timestamp = i.toLong() * 1000
            )
        }
        messageDao.insertMessages(messages)

        // When: Get page 2 (offset 5, limit 3)
        val result = messageDao.getMessagesPaged("peer-paged", limit = 3, offset = 5).first()

        // Then
        assertEquals(3, result.size)
        assertEquals("Message 6", result[0].text)
        assertEquals("Message 7", result[1].text)
        assertEquals("Message 8", result[2].text)
    }

    @Test
    fun `getMessagesPaged returns partial page when offset near end`() = runTest {
        // Given
        createTestChat("peer-partial")

        val messages = (1..5).map { i ->
            createTestMessage(
                id = "partial-$i",
                chatId = "peer-partial",
                text = "Msg $i",
                timestamp = i.toLong() * 1000
            )
        }
        messageDao.insertMessages(messages)

        // When: Request 10 items starting at offset 3 (only 2 exist)
        val result = messageDao.getMessagesPaged("peer-partial", limit = 10, offset = 3).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("Msg 4", result[0].text)
        assertEquals("Msg 5", result[1].text)
    }

    @Test
    fun `getMessagesPaged returns empty when offset exceeds total`() = runTest {
        // Given
        createTestChat("peer-empty-page")

        val messages = (1..3).map { i ->
            createTestMessage(
                id = "empty-$i",
                chatId = "peer-empty-page",
                text = "Msg $i",
                timestamp = i.toLong() * 1000
            )
        }
        messageDao.insertMessages(messages)

        // When
        val result = messageDao.getMessagesPaged("peer-empty-page", limit = 5, offset = 10).first()

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================================================
    // MESSAGE STATUS UPDATE TESTS
    // ============================================================================================

    @Test
    fun `updateMessageStatus changes status correctly`() = runTest {
        // Given
        val message = createTestMessage(id = "status-msg", status = MessageStatus.QUEUED)
        messageDao.insertMessage(message)

        // When
        messageDao.updateMessageStatus("status-msg", MessageStatus.SENT)
        val result = messageDao.getMessageById("status-msg")

        // Then
        assertEquals(MessageStatus.SENT, result?.status)
    }

    @Test
    fun `updateMessageStatus follows full lifecycle`() = runTest {
        // Given
        val message = createTestMessage(id = "lifecycle-msg", status = MessageStatus.QUEUED)
        messageDao.insertMessage(message)

        // When: QUEUED -> SENDING -> SENT -> DELIVERED -> READ
        messageDao.updateMessageStatus("lifecycle-msg", MessageStatus.SENDING)
        assertEquals(MessageStatus.SENDING, messageDao.getMessageById("lifecycle-msg")?.status)

        messageDao.updateMessageStatus("lifecycle-msg", MessageStatus.SENT)
        assertEquals(MessageStatus.SENT, messageDao.getMessageById("lifecycle-msg")?.status)

        messageDao.updateMessageStatus("lifecycle-msg", MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, messageDao.getMessageById("lifecycle-msg")?.status)

        messageDao.updateMessageStatus("lifecycle-msg", MessageStatus.READ)
        assertEquals(MessageStatus.READ, messageDao.getMessageById("lifecycle-msg")?.status)
    }

    @Test
    fun `updateMessageStatus to FAILED works correctly`() = runTest {
        // Given
        val message = createTestMessage(id = "failed-msg", status = MessageStatus.SENDING)
        messageDao.insertMessage(message)

        // When
        messageDao.updateMessageStatus("failed-msg", MessageStatus.FAILED)
        val result = messageDao.getMessageById("failed-msg")

        // Then
        assertEquals(MessageStatus.FAILED, result?.status)
    }

    // ============================================================================================
    // DELETE MESSAGE TESTS
    // ============================================================================================

    @Test
    fun `deleteMessages removes specified messages`() = runTest {
        // Given
        val msg1 = createTestMessage(id = "del-1")
        val msg2 = createTestMessage(id = "del-2")
        val msg3 = createTestMessage(id = "del-3")
        messageDao.insertMessages(listOf(msg1, msg2, msg3))

        // When
        messageDao.deleteMessages(listOf("del-1", "del-3"))

        // Then
        assertNull(messageDao.getMessageById("del-1"))
        assertNotNull(messageDao.getMessageById("del-2"))
        assertNull(messageDao.getMessageById("del-3"))
    }

    @Test
    fun `deleteMessages with empty list does nothing`() = runTest {
        // Given
        val message = createTestMessage(id = "keep-msg")
        messageDao.insertMessage(message)

        // When
        messageDao.deleteMessages(emptyList())

        // Then
        assertNotNull(messageDao.getMessageById("keep-msg"))
    }

    @Test
    fun `markAsDeletedForMe sets isDeletedForMe flag`() = runTest {
        // Given
        val message = createTestMessage(id = "delete-me", isDeletedForMe = false)
        messageDao.insertMessage(message)

        // When
        messageDao.markAsDeletedForMe("delete-me")
        val result = messageDao.getMessageById("delete-me")

        // Then
        assertTrue(result?.isDeletedForMe == true)
        // Other fields should remain unchanged
        assertFalse(result?.isDeletedForEveryone == true)
        assertEquals("Test message", result?.text)
    }

    @Test
    fun `markAsDeletedForEveryone sets all deletion fields`() = runTest {
        // Given
        val message = createTestMessage(id = "delete-all", isDeletedForEveryone = false)
        messageDao.insertMessage(message)
        val deletedAt = System.currentTimeMillis()
        val deletedBy = "my-id"

        // When
        messageDao.markAsDeletedForEveryone("delete-all", deletedAt, deletedBy)
        val result = messageDao.getMessageById("delete-all")

        // Then
        assertTrue(result?.isDeletedForEveryone == true)
        assertEquals(deletedAt, result?.deletedAt)
        assertEquals(deletedBy, result?.deletedBy)
    }

    // ============================================================================================
    // REPLY-TO MESSAGE LINKAGE TESTS
    // ============================================================================================

    @Test
    fun `insertMessage with replyToId preserves reply linkage`() = runTest {
        // Given
        val originalMessage = createTestMessage(id = "original-msg", text = "Original")
        val replyMessage = createTestMessage(
            id = "reply-msg",
            text = "This is a reply",
            replyToId = "original-msg"
        )

        // When
        messageDao.insertMessages(listOf(originalMessage, replyMessage))
        val reply = messageDao.getMessageById("reply-msg")

        // Then
        assertNotNull(reply)
        assertEquals("original-msg", reply?.replyToId)
        assertEquals("This is a reply", reply?.text)
    }

    @Test
    fun `message without replyToId has null reply linkage`() = runTest {
        // Given
        val message = createTestMessage(id = "no-reply", replyToId = null)

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById("no-reply")

        // Then
        assertNull(result?.replyToId)
    }

    // ============================================================================================
    // GROUPED MESSAGE TESTS
    // ============================================================================================

    @Test
    fun `insertMessage with groupId links grouped messages`() = runTest {
        // Given
        val groupId = "group-album-001"
        val coverMessage = createTestMessage(
            id = "cover-msg",
            text = "Album cover",
            groupId = groupId
        )
        val attachmentMsg1 = createTestMessage(
            id = "attach-1",
            text = "Photo 1",
            groupId = groupId
        )
        val attachmentMsg2 = createTestMessage(
            id = "attach-2",
            text = "Photo 2",
            groupId = groupId
        )

        // When
        messageDao.insertMessages(listOf(coverMessage, attachmentMsg1, attachmentMsg2))

        // Query all messages with this groupId
        val allMessages = messageDao.getAllMessagesForChat("test-peer").first()
        val groupedMessages = allMessages.filter { it.groupId == groupId }

        // Then
        assertEquals(3, groupedMessages.size)
        assertTrue(groupedMessages.all { it.groupId == groupId })
    }

    @Test
    fun `getMessagesByIds returns specified messages`() = runTest {
        // Given
        val msg1 = createTestMessage(id = "batch-1", text = "First")
        val msg2 = createTestMessage(id = "batch-2", text = "Second")
        val msg3 = createTestMessage(id = "batch-3", text = "Third")
        messageDao.insertMessages(listOf(msg1, msg2, msg3))

        // When
        val result = messageDao.getMessagesByIds(listOf("batch-1", "batch-3"))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "batch-1" })
        assertTrue(result.any { it.id == "batch-3" })
        assertFalse(result.any { it.id == "batch-2" })
    }

    @Test
    fun `getMessagesByIds with empty list returns empty`() = runTest {
        // Given
        val message = createTestMessage(id = "solo-msg")
        messageDao.insertMessage(message)

        // When
        val result = messageDao.getMessagesByIds(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================================================
    // MESSAGE ATTACHMENT TESTS
    // ============================================================================================

    @Test
    fun `insertMessageAttachment and getAttachmentsForMessage work correctly`() = runTest {
        // Given
        val message = createTestMessage(id = "attach-parent")
        messageDao.insertMessage(message)

        val attachment1 = MessageAttachmentEntity(
            id = "att-1",
            type = MessageType.IMAGE,
            messageId = "attach-parent",
            filePath = "/path/to/image1.jpg"
        )
        val attachment2 = MessageAttachmentEntity(
            id = "att-2",
            type = MessageType.VIDEO,
            messageId = "attach-parent",
            filePath = "/path/to/video1.mp4"
        )

        // When
        messageDao.insertMessageAttachments(listOf(attachment1, attachment2))
        val result = messageDao.getAttachmentsForMessage("attach-parent")

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "att-1" && it.type == MessageType.IMAGE })
        assertTrue(result.any { it.id == "att-2" && it.type == MessageType.VIDEO })
    }

    @Test
    fun `getAttachmentsForMessage returns empty for message without attachments`() = runTest {
        // Given
        val message = createTestMessage(id = "no-attach-msg")
        messageDao.insertMessage(message)

        // When
        val result = messageDao.getAttachmentsForMessage("no-attach-msg")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteAttachmentsForMessages removes attachments for specified messages`() = runTest {
        // Given
        val msg1 = createTestMessage(id = "del-attach-1")
        val msg2 = createTestMessage(id = "del-attach-2")
        messageDao.insertMessages(listOf(msg1, msg2))

        val att1 = MessageAttachmentEntity(id = "att-msg1", messageId = "del-attach-1", filePath = "/path1")
        val att2 = MessageAttachmentEntity(id = "att-msg2", messageId = "del-attach-2", filePath = "/path2")
        messageDao.insertMessageAttachments(listOf(att1, att2))

        // When
        messageDao.deleteAttachmentsForMessages(listOf("del-attach-1"))

        // Then
        val remaining1 = messageDao.getAttachmentsForMessage("del-attach-1")
        val remaining2 = messageDao.getAttachmentsForMessage("del-attach-2")
        assertTrue(remaining1.isEmpty())
        assertEquals(1, remaining2.size)
    }

    // ============================================================================================
    // UPDATE REACTION TESTS
    // ============================================================================================

    @Test
    fun `updateReaction sets reaction on message`() = runTest {
        // Given
        val message = createTestMessage(id = "reaction-msg", reaction = null)
        messageDao.insertMessage(message)

        // When
        messageDao.updateReaction("reaction-msg", "👍")
        val result = messageDao.getMessageById("reaction-msg")

        // Then
        assertEquals("👍", result?.reaction)
    }

    @Test
    fun `updateReaction can remove reaction by setting null`() = runTest {
        // Given
        val message = createTestMessage(id = "remove-reaction", reaction = "❤️")
        messageDao.insertMessage(message)

        // When
        messageDao.updateReaction("remove-reaction", null)
        val result = messageDao.getMessageById("remove-reaction")

        // Then
        assertNull(result?.reaction)
    }

    // ============================================================================================
    // DELETE ALL MESSAGES FOR CHAT TESTS
    // ============================================================================================

    @Test
    fun `deleteAllMessagesForChat removes only messages for that chat`() = runTest {
        // Given
        createTestChat("chat-to-delete")
        createTestChat("chat-to-keep")

        val msgA1 = createTestMessage(id = "del-a1", chatId = "chat-to-delete", text = "A1")
        val msgA2 = createTestMessage(id = "del-a2", chatId = "chat-to-delete", text = "A2")
        val msgB1 = createTestMessage(id = "keep-b1", chatId = "chat-to-keep", text = "B1")

        messageDao.insertMessages(listOf(msgA1, msgA2, msgB1))

        // When
        messageDao.deleteAllMessagesForChat("chat-to-delete")

        // Then
        val remainingA = messageDao.getAllMessagesForChat("chat-to-delete").first()
        val remainingB = messageDao.getAllMessagesForChat("chat-to-keep").first()
        assertTrue(remainingA.isEmpty())
        assertEquals(1, remainingB.size)
        assertEquals("B1", remainingB[0].text)
    }

    // ============================================================================================
    // MEDIA MESSAGE TESTS
    // ============================================================================================

    @Test
    fun `insertMessage with mediaPath stores correctly`() = runTest {
        // Given
        val message = MessageEntity(
            id = "media-msg",
            chatId = "peer-media",
            senderId = "my-id",
            text = "Check this image",
            mediaPath = "/storage/emulated/0/Pictures/meshify/img_001.jpg",
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.SENT
        )

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById("media-msg")

        // Then
        assertNotNull(result)
        assertEquals("/storage/emulated/0/Pictures/meshify/img_001.jpg", result?.mediaPath)
        assertEquals(MessageType.IMAGE, result?.type)
    }

    @Test
    fun `insertMessage with VIDEO type stores correctly`() = runTest {
        // Given
        val message = MessageEntity(
            id = "video-msg",
            chatId = "peer-video",
            senderId = "my-id",
            text = null,
            mediaPath = "/storage/video.mp4",
            type = MessageType.VIDEO,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.SENT
        )

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById("video-msg")

        // Then
        assertNotNull(result)
        assertEquals(MessageType.VIDEO, result?.type)
        assertEquals("/storage/video.mp4", result?.mediaPath)
    }

    // ============================================================================================
    // REPLACE ON CONFLICT TESTS
    // ============================================================================================

    @Test
    fun `insertMessage with same ID replaces existing message`() = runTest {
        // Given
        val original = createTestMessage(id = "replace-msg", text = "Original text", timestamp = 1000L)
        messageDao.insertMessage(original)

        // When: Insert with same ID but different content
        val updated = createTestMessage(id = "replace-msg", text = "Updated text", timestamp = 2000L)
        messageDao.insertMessage(updated)

        // Then
        val result = messageDao.getMessageById("replace-msg")
        assertNotNull(result)
        assertEquals("Updated text", result?.text)
        assertEquals(2000L, result?.timestamp)
    }

    // ============================================================================================
    // CONCURRENT WRITE TESTS
    // ============================================================================================

    @Test
    fun `concurrent inserts do not corrupt data`() = runTest {
        // Given
        val messages = (1..20).map { i ->
            createTestMessage(
                id = "concurrent-$i",
                chatId = "peer-concurrent",
                text = "Msg $i",
                timestamp = i.toLong() * 100
            )
        }

        // When: Insert all messages (simulating concurrent writes via sequential inserts)
        messages.forEach { messageDao.insertMessage(it) }

        // Then
        val result = messageDao.getAllMessagesForChat("peer-concurrent").first()
        assertEquals(20, result.size)
        // Verify no data corruption — all texts should be intact
        for (i in 1..20) {
            val found = result.any { it.text == "Msg $i" }
            assertTrue("Message $i should exist", found)
        }
    }

    @Test
    fun `rapid status updates on same message are consistent`() = runTest {
        // Given
        val message = createTestMessage(id = "rapid-status", status = MessageStatus.QUEUED)
        messageDao.insertMessage(message)

        // When: Rapid status changes
        messageDao.updateMessageStatus("rapid-status", MessageStatus.SENDING)
        messageDao.updateMessageStatus("rapid-status", MessageStatus.SENT)
        messageDao.updateMessageStatus("rapid-status", MessageStatus.DELIVERED)

        // Then
        val result = messageDao.getMessageById("rapid-status")
        assertEquals(MessageStatus.DELIVERED, result?.status)
    }

    @Test
    fun `getAllAttachments returns all attachments in database`() = runTest {
        // Given
        val msg = createTestMessage(id = "attach-all-parent")
        messageDao.insertMessage(msg)

        val attachments = (1..5).map { i ->
            MessageAttachmentEntity(
                id = "all-attach-$i",
                type = MessageType.IMAGE,
                messageId = "attach-all-parent",
                filePath = "/path/file_$i.jpg"
            )
        }
        messageDao.insertMessageAttachments(attachments)

        // When
        val result = messageDao.getAllAttachments()

        // Then
        assertTrue(result.size >= 5)
        // Our 5 attachments should all be present
        for (i in 1..5) {
            assertTrue(result.any { it.id == "all-attach-$i" })
        }
    }
}
