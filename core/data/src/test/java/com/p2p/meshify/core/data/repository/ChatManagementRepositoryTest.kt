package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ChatManagementRepository.
 *
 * Covers:
 * - Chat CRUD operations
 * - Message deletion (for me / for everyone)
 * - Message forwarding to single and multiple peers
 * - Search operations
 * - Edge cases (empty results, missing messages)
 */
@RunWith(RobolectricTestRunner::class)
class ChatManagementRepositoryTest {

    private val chatDao: ChatDao = mockk(relaxed = true)
    private val messageDao: MessageDao = mockk(relaxed = true)

    private lateinit var repository: ChatManagementRepository

    private val testPeerId = "peer-123"
    private val testPeerName = "Alice"
    private val testMessageId = "msg-001"

    @Before
    fun setup() {
        // Use relaxed mockk for context - this handles all getString calls
        // by returning default empty strings. The forwarded message content
        // is not validated for exact string matching in these tests.
        val mockContext = mockk<Context>(relaxed = true)

        repository = ChatManagementRepository(
            context = mockContext,
            chatDao = chatDao,
            messageDao = messageDao
        )
    }

    // ============================================================================================
    // getAllChats() TESTS
    // ============================================================================================

    @Test
    fun `getAllChats returns empty list from DAO`() = runTest {
        every { chatDao.getAllChats() } returns flowOf(emptyList())
        val chats = repository.getAllChats().first()
        assertTrue(chats.isEmpty())
    }

    @Test
    fun `getAllChats returns chats from DAO ordered by lastTimestamp`() = runTest {
        val expected = listOf(
            ChatEntity("peer1", "Alice", "Hello", 2000L),
            ChatEntity("peer2", "Bob", "Hi", 1000L)
        )
        every { chatDao.getAllChats() } returns flowOf(expected)

        val chats = repository.getAllChats().first()
        assertEquals(2, chats.size)
        assertEquals("peer1", chats[0].peerId)
        assertEquals("peer2", chats[1].peerId)
    }

    // ============================================================================================
    // searchChats() TESTS
    // ============================================================================================

    @Test
    fun `searchChats returns matching chats`() = runTest {
        val expected = listOf(
            ChatEntity("peer1", "Alice", "Hello there", 1000L)
        )
        every { chatDao.searchChats("Alice") } returns flowOf(expected)

        val results = repository.searchChats("Alice").first()
        assertEquals(1, results.size)
    }

    @Test
    fun `searchChats returns empty for no match`() = runTest {
        every { chatDao.searchChats("ZZZ") } returns flowOf(emptyList())
        val results = repository.searchChats("ZZZ").first()
        assertTrue(results.isEmpty())
    }

    // ============================================================================================
    // getMessages() TESTS
    // ============================================================================================

    @Test
    fun `getMessages returns messages for chat`() = runTest {
        val expected = listOf(
            MessageEntity(
                id = "msg1", chatId = testPeerId, senderId = testPeerId,
                text = "Hello", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
            )
        )
        every { messageDao.getAllMessagesForChat(testPeerId) } returns flowOf(expected)

        val messages = repository.getMessages(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals("Hello", messages.first().text)
    }

    // ============================================================================================
    // getMessagesPaged() TESTS
    // ============================================================================================

    @Test
    fun `getMessagesPaged returns paginated slice`() = runTest {
        val expected = listOf(
            MessageEntity(
                id = "msg1", chatId = testPeerId, senderId = testPeerId,
                text = "Page item", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
            )
        )
        every { messageDao.getMessagesPaged(testPeerId, 10, 0) } returns flowOf(expected)

        val page = repository.getMessagesPaged(testPeerId, 10, 0).first()
        assertEquals(1, page.size)
    }

    // ============================================================================================
    // deleteChat() TESTS
    // ============================================================================================

    @Test
    fun `deleteChat removes chat and all messages`() = runTest {
        repository.deleteChat(testPeerId)

        coVerify { chatDao.deleteChatById(testPeerId) }
        coVerify { messageDao.deleteAllMessagesForChat(testPeerId) }
    }

    @Test
    fun `deleteChat throws when DAO fails`() = runTest {
        coEvery { chatDao.deleteChatById(any()) } throws RuntimeException("DB error")

        try {
            repository.deleteChat(testPeerId)
            assertFalse("Expected exception to be thrown", true)
        } catch (e: Exception) {
            assertEquals("DB error", e.message)
        }
    }

    // ============================================================================================
    // markChatAsRead() TESTS
    // ============================================================================================

    @Test
    fun `markChatAsRead resets unread count`() = runTest {
        repository.markChatAsRead(testPeerId)
        coVerify { chatDao.resetUnreadCount(testPeerId) }
    }

    @Test
    fun `markChatAsRead throws when DAO fails`() = runTest {
        coEvery { chatDao.resetUnreadCount(any()) } throws RuntimeException("DB error")

        try {
            repository.markChatAsRead(testPeerId)
            assertFalse("Expected exception to be thrown", true)
        } catch (e: Exception) {
            assertEquals("DB error", e.message)
        }
    }

    // ============================================================================================
    // deleteMessage() TESTS
    // ============================================================================================

    @Test
    fun `deleteMessage for me marks message as deleted`() = runTest {
        // Given
        val message = MessageEntity(
            id = testMessageId, chatId = testPeerId, senderId = testPeerId,
            text = "Delete me", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.deleteMessage(testMessageId, DeleteType.DELETE_FOR_ME)

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.markAsDeletedForMe(testMessageId) }
        coVerify(exactly = 0) { messageDao.markAsDeletedForEveryone(any(), any(), any()) }
    }

    @Test
    fun `deleteMessage for everyone marks message and sends delete request`() = runTest {
        // Given
        val message = MessageEntity(
            id = testMessageId, chatId = testPeerId, senderId = testPeerId,
            text = "Delete for all", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.deleteMessage(testMessageId, DeleteType.DELETE_FOR_EVERYONE)

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.markAsDeletedForEveryone(eq(testMessageId), any(), eq(testPeerId)) }
    }

    @Test
    fun `deleteMessage returns failure when message not found`() = runTest {
        coEvery { messageDao.getMessageById("nonexistent") } returns null

        val result = repository.deleteMessage("nonexistent", DeleteType.DELETE_FOR_ME)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `deleteMessage returns failure on exception`() = runTest {
        coEvery { messageDao.getMessageById(any()) } throws RuntimeException("DB crash")

        val result = repository.deleteMessage(testMessageId, DeleteType.DELETE_FOR_ME)

        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // forwardMessage() TESTS
    // ============================================================================================

    @Test
    fun `forwardMessage returns failure when original message not found`() = runTest {
        coEvery { messageDao.getMessageById("nonexistent") } returns null

        val result = repository.forwardMessage("nonexistent", listOf("peer1"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `forwardMessage creates messages for each target peer`() = runTest {
        // Given: original text message
        val originalMessage = MessageEntity(
            id = testMessageId, chatId = "source-peer", senderId = "original-sender",
            text = "Hello everyone!", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns originalMessage
        coEvery { chatDao.getChatById("target-1") } returns null
        coEvery { chatDao.getChatById("target-2") } returns null

        // When: forward to 2 peers
        val result = repository.forwardMessage(testMessageId, listOf("target-1", "target-2"))

        // Then
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)

        // Should have inserted 2 messages (one per target)
        coVerify(exactly = 2) { messageDao.insertMessage(any()) }

        // Should have created and updated chat records (2 per peer = 4 total)
        // Each peer gets: 1x create chat + 1x update lastMessage
        coVerify(exactly = 4) { chatDao.insertChat(any()) }
    }

    @Test
    fun `forwardMessage preserves original message type`() = runTest {
        // Given: image message
        val originalMessage = MessageEntity(
            id = testMessageId, chatId = "source-peer", senderId = "original-sender",
            text = null, mediaPath = "/path/to/image.jpg",
            type = MessageType.IMAGE, timestamp = 1000L,
            isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns originalMessage
        coEvery { chatDao.getChatById("target-1") } returns null

        // When
        val result = repository.forwardMessage(testMessageId, listOf("target-1"))

        // Then
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)

        val messageSlot = slot<MessageEntity>()
        coVerify { messageDao.insertMessage(capture(messageSlot)) }

        assertEquals(MessageType.IMAGE, messageSlot.captured.type)
        assertEquals("/path/to/image.jpg", messageSlot.captured.mediaPath)
        assertTrue(messageSlot.captured.isFromMe)
    }

    @Test
    fun `forwardMessage uses existing chat when available`() = runTest {
        // Given: existing chat
        val originalMessage = MessageEntity(
            id = testMessageId, chatId = "source-peer", senderId = "original-sender",
            text = "Forward this", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        val existingChat = ChatEntity("target-1", "Bob", "Previous", 500L)

        coEvery { messageDao.getMessageById(testMessageId) } returns originalMessage
        coEvery { chatDao.getChatById("target-1") } returns existingChat

        // When
        val result = repository.forwardMessage(testMessageId, listOf("target-1"))

        // Then
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        // Chat updated with new lastMessage
        coVerify { chatDao.insertChat(any()) }
    }

    @Test
    fun `forwardMessage handles failure during forwarding`() = runTest {
        // Given: message DAO throws on second insert
        val originalMessage = MessageEntity(
            id = testMessageId, chatId = "source-peer", senderId = "original-sender",
            text = "Forward", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns originalMessage
        coEvery { chatDao.getChatById(any()) } returns null
        coEvery { messageDao.insertMessage(any()) } throws RuntimeException("Insert failed")

        // When
        val result = repository.forwardMessage(testMessageId, listOf("target-1"))

        // Then: failed
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // @Deprecated copyMessageToChat TESTS
    // ============================================================================================

    @Test
    fun `forwardMessage with single peer works as delegation`() = runTest {
        // Given: this is a private method, but we test forwardMessage which it delegates to
        val originalMessage = MessageEntity(
            id = testMessageId, chatId = "source-peer", senderId = "original-sender",
            text = "Deprecated test", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = false, status = MessageStatus.SENT
        )
        coEvery { messageDao.getMessageById(testMessageId) } returns originalMessage
        coEvery { chatDao.getChatById("target-1") } returns null

        // When: forward to single peer (same as copyMessageToChat)
        val result = repository.forwardMessage(testMessageId, listOf("target-1"))

        // Then: still works
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        coVerify { messageDao.insertMessage(any()) }
    }
}
