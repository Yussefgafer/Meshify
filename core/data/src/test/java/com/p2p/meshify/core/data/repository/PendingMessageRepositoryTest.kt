package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for PendingMessageRepository.
 *
 * Covers:
 * - Queue operations
 * - Retry logic with backoff
 * - Edge cases (missing files, offline peers)
 * - State flows
 */
@RunWith(RobolectricTestRunner::class)
class PendingMessageRepositoryTest {

    // Core dependencies
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val messageDao: MessageDao = mockk(relaxed = true)
    private val transportManager: TransportManager = mockk(relaxed = true)
    private val settingsRepository: ISettingsRepository = mockk(relaxed = true)

    // Transport mock
    private val mockTransport: IMeshTransport = mockk(relaxed = true)

    // In-memory store to simulate DAO behavior
    private val pendingStore = mutableMapOf<String, PendingMessageEntity>()

    private lateinit var repository: PendingMessageRepository

    private val testPeerId = "peer-123"
    private val testPeerName = "Alice"
    private val testMessageId = "msg-001"
    private val testContent = "Hello, world!"

    @Before
    fun setup() {
        pendingStore.clear()

        // Settings repo: device ID
        coEvery { settingsRepository.getDeviceId() } returns "my-device-id"

        // Pending DAO: realistic insert/get/delete behavior
        coEvery { pendingMessageDao.insert(any()) } answers {
            val entity = firstArg<PendingMessageEntity>()
            pendingStore[entity.id] = entity
        }
        coEvery { pendingMessageDao.getAll() } answers {
            pendingStore.values.toList()
        }
        coEvery { pendingMessageDao.getByRecipient(any()) } answers {
            pendingStore.values.filter { it.recipientId == firstArg<String>() }
        }
        coEvery { pendingMessageDao.deleteById(any()) } answers {
            pendingStore.remove(firstArg<String>())
        }
        coEvery { pendingMessageDao.deleteByStatus(any()) } answers {
            val status = firstArg<MessageStatus>()
            pendingStore.entries.removeAll { it.value.status == status }
        }

        // Transport manager: return mock transport
        every { transportManager.selectBestTransport(any()) } returns listOf(mockTransport)

        // Transport send: succeed by default
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)

        // Message DAO: getMessagesByIds return empty by default
        coEvery { messageDao.getMessagesByIds(any()) } returns emptyList()

        repository = PendingMessageRepository(
            pendingMessageDao = pendingMessageDao,
            messageDao = messageDao,
            transportManager = transportManager,
            settingsRepository = settingsRepository
        )
    }

    // ============================================================================================
    // queueMessage() TESTS
    // ============================================================================================

    @Test
    fun `queueMessage inserts pending message and refreshes count`() = runTest {
        // When
        repository.queueMessage(
            messageId = testMessageId,
            recipientId = testPeerId,
            recipientName = testPeerName,
            content = testContent,
            type = MessageType.TEXT
        )

        // Then: record was inserted
        coVerify { pendingMessageDao.insert(any()) }
        assertEquals(1, pendingStore.size)

        val inserted = pendingStore[testMessageId]
        assertNotNull(inserted)
        assertEquals(testPeerId, inserted!!.recipientId)
        assertEquals(testContent, inserted.content)
        assertEquals(MessageStatus.QUEUED, inserted.status)
        assertEquals(0, inserted.retryCount)

        // Then: pending count flow is updated
        val count = repository.pendingCount.first()
        assertEquals(1, count)
    }

    @Test
    fun `queueMessage with image type sets correct type`() = runTest {
        // When
        repository.queueMessage("img-1", testPeerId, testPeerName, "[Image]", MessageType.IMAGE)

        // Then
        val entity = pendingStore["img-1"]
        assertNotNull(entity)
        assertEquals(MessageType.IMAGE, entity!!.type)
    }

    @Test
    fun `queueMessage inserts multiple messages and increments count`() = runTest {
        // When
        repository.queueMessage("msg-1", testPeerId, testPeerName, "First", MessageType.TEXT)
        repository.queueMessage("msg-2", testPeerId, testPeerName, "Second", MessageType.TEXT)

        // Then
        assertEquals(2, repository.pendingCount.first())
    }

    // ============================================================================================
    // retryPendingMessages() TESTS
    // ============================================================================================

    @Test
    fun `retryPendingMessages with empty pending list returns success`() = runTest {
        // Given: no pending messages for this peer
        pendingStore.clear()

        // When
        val result = repository.retryPendingMessages(testPeerId)

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { messageDao.getMessagesByIds(any()) }
        coVerify(exactly = 0) { mockTransport.sendPayload(any(), any()) }
    }

    @Test
    fun `retryPendingMessages sends message and updates status on success`() = runTest {
        // Given: one pending text message
        val message = createTestMessage(mediaPath = null)
        val pending = createPendingMessage(MessageType.TEXT)
        pendingStore[pending.id] = pending
        coEvery { messageDao.getMessagesByIds(listOf(testMessageId)) } returns listOf(message)
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)

        // When
        val result = repository.retryPendingMessages(testPeerId)

        // Then
        assertTrue(result.isSuccess)

        // Transport was called
        coVerify { mockTransport.sendPayload(testPeerId, any()) }

        // Message status updated to SENT
        coVerify { messageDao.updateMessageStatus(testMessageId, MessageStatus.SENT) }

        // Pending message removed
        assertFalse(pendingStore.containsKey(testMessageId))
    }

    @Test
    fun `retryPendingMessages removes pending when message not in DB`() = runTest {
        // Given: pending message exists but no corresponding MessageEntity
        val pending = createPendingMessage(MessageType.TEXT)
        pendingStore[pending.id] = pending
        coEvery { messageDao.getMessagesByIds(any()) } returns emptyList()

        // When
        val result = repository.retryPendingMessages(testPeerId)

        // Then: pending was deleted, counted as failure
        assertTrue(result.isFailure)
        assertFalse(pendingStore.containsKey(testMessageId))
        coVerify { pendingMessageDao.deleteById(testMessageId) }
    }

    @Test
    fun `retryPendingMessages fails when media file does not exist`() = runTest {
        // Given: pending image message with non-existent file path
        val message = createTestMessage(
            mediaPath = "/nonexistent/path/image.jpg",
            type = MessageType.IMAGE
        )
        val pending = createPendingMessage(MessageType.IMAGE)
        pendingStore[pending.id] = pending
        coEvery { messageDao.getMessagesByIds(listOf(testMessageId)) } returns listOf(message)

        // When
        val result = repository.retryPendingMessages(testPeerId)

        // Then: should fail without calling transport
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockTransport.sendPayload(any(), any()) }

        // The pending message should remain in the store for future retry attempts
        assertTrue(pendingStore.containsKey(testMessageId))
    }

    @Test
    fun `retryPendingMessages with partial failures returns failure`() = runTest {
        // Given: two pending messages, first succeeds, second has missing message
        val msg1Id = "msg-success"
        val msg2Id = "msg-fail"

        // First message: should succeed
        val message1 = MessageEntity(
            id = msg1Id, chatId = testPeerId, senderId = "me",
            text = "First", type = MessageType.TEXT,
            timestamp = 1000L, isFromMe = true, status = MessageStatus.QUEUED
        )
        val pending1 = PendingMessageEntity(
            id = msg1Id, recipientId = testPeerId, recipientName = testPeerName,
            content = "First", type = MessageType.TEXT
        )
        pendingStore[msg1Id] = pending1

        // Second message: has no MessageEntity (simulates race condition)
        val pending2 = PendingMessageEntity(
            id = msg2Id, recipientId = testPeerId, recipientName = testPeerName,
            content = "Second", type = MessageType.TEXT
        )
        pendingStore[msg2Id] = pending2

        coEvery { messageDao.getMessagesByIds(listOf(msg1Id, msg2Id)) } returns listOf(message1)
        coEvery { mockTransport.sendPayload(testPeerId, any()) } returns Result.success(Unit)

        // When
        val result = repository.retryPendingMessages(testPeerId)

        // Then: should fail because 1 of 2 failed
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("1 messages failed") == true)
    }

    // ============================================================================================
    // retryForOnlinePeer() TESTS
    // ============================================================================================

    @Test
    fun `retryForOnlinePeer skips when no pending messages`() = runTest {
        // Given: no pending messages
        pendingStore.clear()

        // When
        repository.retryForOnlinePeer(testPeerId)

        // Then: no retry attempt
        coVerify(exactly = 0) { mockTransport.sendPayload(any(), any()) }
    }

    @Test
    fun `retryForOnlinePeer retries when pending exist`() = runTest {
        // Given: pending message exists
        val message = createTestMessage(mediaPath = null)
        val pending = createPendingMessage(MessageType.TEXT)
        pendingStore[pending.id] = pending
        coEvery { messageDao.getMessagesByIds(listOf(testMessageId)) } returns listOf(message)
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)

        // When
        repository.retryForOnlinePeer(testPeerId)

        // Then: transport was called
        coVerify { mockTransport.sendPayload(any(), any()) }
    }

    // ============================================================================================
    // getPendingCountForRecipient() TESTS
    // ============================================================================================

    @Test
    fun `getPendingCountForRecipient returns zero when no messages`() = runTest {
        val count = repository.getPendingCountForRecipient("unknown-peer")
        assertEquals(0, count)
    }

    @Test
    fun `getPendingCountForRecipient returns correct count`() = runTest {
        // Given: two pending messages for testPeerId
        pendingStore["msg-1"] = createPendingMessage(MessageType.TEXT, id = "msg-1")
        pendingStore["msg-2"] = createPendingMessage(MessageType.TEXT, id = "msg-2")

        // When
        val count = repository.getPendingCountForRecipient(testPeerId)

        // Then
        assertEquals(2, count)
    }

    @Test
    fun `getPendingCountForRecipient filters by recipient`() = runTest {
        // Given: one message for testPeerId, one for another peer
        pendingStore["msg-1"] = createPendingMessage(MessageType.TEXT, id = "msg-1")
        pendingStore["msg-2"] = PendingMessageEntity(
            id = "msg-2", recipientId = "other-peer", recipientName = "Bob",
            content = "Hey", type = MessageType.TEXT
        )

        // When
        val countForTestPeer = repository.getPendingCountForRecipient(testPeerId)

        // Then
        assertEquals(1, countForTestPeer)
    }

    // ============================================================================================
    // deletePendingMessage() TESTS
    // ============================================================================================

    @Test
    fun `deletePendingMessage removes from store and refreshes`() = runTest {
        // Given: one pending message
        repository.queueMessage(testMessageId, testPeerId, testPeerName, testContent, MessageType.TEXT)

        // When
        repository.deletePendingMessage(testMessageId)

        // Then
        assertFalse(pendingStore.containsKey(testMessageId))
        coVerify { pendingMessageDao.deleteById(testMessageId) }

        // Count should be zero after delete
        assertEquals(0, repository.pendingCount.first())
    }

    @Test
    fun `deletePendingMessage with non-existent id does not throw`() = runTest {
        // Should not crash
        repository.deletePendingMessage("non-existent-id")
        coVerify { pendingMessageDao.deleteById("non-existent-id") }
    }

    @Test
    fun `deletePendingMessage reduces count correctly`() = runTest {
        // Given: two pending messages via queue (which refreshes the flow)
        repository.queueMessage("msg-1", testPeerId, testPeerName, "First", MessageType.TEXT)
        repository.queueMessage("msg-2", testPeerId, testPeerName, "Second", MessageType.TEXT)

        assertEquals(2, repository.pendingCount.first())

        // When: delete one
        repository.deletePendingMessage("msg-1")

        // Then: count reduced
        assertEquals(1, repository.pendingCount.first())
    }

    // ============================================================================================
    // getAllPendingMessages() TESTS
    // ============================================================================================

    @Test
    fun `getAllPendingMessages returns all stored messages`() = runTest {
        // Given: two messages
        pendingStore["msg-1"] = createPendingMessage(MessageType.TEXT, id = "msg-1")
        pendingStore["msg-2"] = createPendingMessage(MessageType.IMAGE, id = "msg-2")

        // When
        val all = repository.getAllPendingMessages()

        // Then
        assertEquals(2, all.size)
    }

    @Test
    fun `getAllPendingMessages returns empty list when none pending`() = runTest {
        val all = repository.getAllPendingMessages()
        assertTrue(all.isEmpty())
    }

    // ============================================================================================
    // clearAllPending() TESTS
    // ============================================================================================

    @Test
    fun `clearAllPending removes all queued messages`() = runTest {
        // Given: two pending messages via queue (which refreshes the flow)
        repository.queueMessage("msg-1", testPeerId, testPeerName, "First", MessageType.TEXT)
        repository.queueMessage("msg-2", testPeerId, testPeerName, "Second", MessageType.TEXT)
        assertEquals(2, repository.pendingCount.first())

        // When
        repository.clearAllPending()

        // Then
        assertEquals(0, repository.pendingCount.first())
        coVerify { pendingMessageDao.deleteByStatus(MessageStatus.QUEUED) }
    }

    @Test
    fun `clearAllPending on empty store does not throw`() = runTest {
        repository.clearAllPending()
        // No exception expected, count stays 0
        assertEquals(0, repository.pendingCount.first())
    }

    // ============================================================================================
    // StateFlow TESTS
    // ============================================================================================

    @Test
    fun `pendingCount flow starts at zero`() = runTest {
        val count = repository.pendingCount.first()
        assertEquals(0, count)
    }

    @Test
    fun `pendingMessages flow starts empty`() = runTest {
        val messages = repository.pendingMessages.first()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `pendingMessages flow updates after queueMessage`() = runTest {
        // When
        repository.queueMessage(testMessageId, testPeerId, testPeerName, testContent, MessageType.TEXT)

        // Then
        val messages = repository.pendingMessages.first()
        assertEquals(1, messages.size)
        assertEquals(testMessageId, messages.first().id)
    }

    // ============================================================================================
    // getPendingMessages() TESTS
    // ============================================================================================

    @Test
    fun `getPendingMessages returns messages for recipient`() = runTest {
        // Given: one for testPeerId, one for another peer
        pendingStore["msg-1"] = createPendingMessage(MessageType.TEXT, id = "msg-1")
        pendingStore["msg-2"] = PendingMessageEntity(
            id = "msg-2", recipientId = "other-peer", recipientName = "Bob",
            content = "Other", type = MessageType.TEXT
        )

        // When
        val forPeer = repository.getPendingMessages(testPeerId)

        // Then
        assertEquals(1, forPeer.size)
        assertEquals("msg-1", forPeer.first().id)
    }

    // ============================================================================================
    // Helper methods
    // ============================================================================================

    private fun createTestMessage(
        mediaPath: String? = null,
        type: MessageType = MessageType.TEXT
    ): MessageEntity {
        return MessageEntity(
            id = testMessageId,
            chatId = testPeerId,
            senderId = "my-device-id",
            text = if (type == MessageType.TEXT) testContent else null,
            mediaPath = mediaPath,
            type = type,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED
        )
    }

    private fun createPendingMessage(
        type: MessageType,
        id: String = testMessageId
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            recipientId = testPeerId,
            recipientName = testPeerName,
            content = testContent,
            type = type,
            status = MessageStatus.QUEUED,
            retryCount = 0,
            maxRetries = 5
        )
    }
}
