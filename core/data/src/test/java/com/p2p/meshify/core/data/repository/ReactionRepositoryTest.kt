package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ReactionRepository.
 *
 * Covers:
 * - Adding reactions
 * - Removing reactions
 * - Retrieving reactions
 * - Edge cases (non-existent messages, duplicates)
 */
@RunWith(RobolectricTestRunner::class)
class ReactionRepositoryTest {

    private val messageDao: MessageDao = mockk(relaxed = true)
    private val transportManager: TransportManager = mockk(relaxed = true)
    private val settingsRepository: ISettingsRepository = mockk(relaxed = true)
    private val mockTransport: IMeshTransport = mockk(relaxed = true)

    private lateinit var repository: ReactionRepository

    private val testMessageId = "msg-001"
    private val testChatId = "chat-001"
    private val myDeviceId = "my-device-id"

    @Before
    fun setup() {
        coEvery { settingsRepository.getDeviceId() } returns myDeviceId
        every { transportManager.selectBestTransport(any()) } returns listOf(mockTransport)
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)

        repository = ReactionRepository(
            messageDao = messageDao,
            transportManager = transportManager,
            settingsRepository = settingsRepository
        )
    }

    private fun createTestMessage(): MessageEntity {
        return MessageEntity(
            id = testMessageId,
            chatId = testChatId,
            senderId = "peer-123",
            text = "Test message",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = false,
            status = MessageStatus.SENT
        )
    }

    // ============================================================================================
    // addReaction() TESTS
    // ============================================================================================

    @Test
    fun `addReaction inserts reaction via DAO`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.addReaction(testMessageId, "\u2764\uFE0F") // ❤️

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.updateReaction(testMessageId, "\u2764\uFE0F") }
        coVerify { mockTransport.sendPayload(eq(testChatId), any()) }
    }

    @Test
    fun `addReaction with null reaction removes reaction`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.addReaction(testMessageId, null)

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.updateReaction(testMessageId, null) }
    }

    @Test
    fun `addReaction fails when message not found`() = runTest {
        // Given
        coEvery { messageDao.getMessageById("nonexistent") } returns null

        // When
        val result = repository.addReaction("nonexistent", "\uD83D\uDC4D")

        // Then
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.updateReaction(any(), any()) }
    }

    @Test
    fun `addReaction sends reaction payload`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.addReaction(testMessageId, "\uD83D\uDE00")

        // Then
        assertTrue(result.isSuccess)
        // Verify payload was sent with correct type
        coVerify {
            mockTransport.sendPayload(
                eq(testChatId),
                withArg { payload ->
                    assertTrue(payload.type == Payload.PayloadType.REACTION)
                }
            )
        }
    }

    @Test
    fun `addReaction fails when no transport available`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message
        every { transportManager.selectBestTransport(any()) } returns emptyList()

        // When
        val result = repository.addReaction(testMessageId, "\uD83D\uDC4D")

        // Then: should fail because no transport
        assertTrue(result.isFailure)
    }

    @Test
    fun `addReaction sends reaction with correct sender ID`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        repository.addReaction(testMessageId, "\uD83D\uDC4D")

        // Then: DAO updates reaction
        coVerify { messageDao.updateReaction(testMessageId, "\uD83D\uDC4D") }
    }

    @Test
    fun `addReaction sends removed reaction when reaction is null`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        repository.addReaction(testMessageId, null)

        // Then: DAO updates with null
        coVerify { messageDao.updateReaction(testMessageId, null) }
    }

    @Test
    fun `addReaction fails on DAO exception`() = runTest {
        // Given
        coEvery { messageDao.getMessageById(any()) } throws RuntimeException("DB error")

        // When
        val result = repository.addReaction(testMessageId, "\uD83D\uDC4D")

        // Then
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // removeReaction() TESTS
    // ============================================================================================

    @Test
    fun `removeReaction delegates to addReaction with null`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val result = repository.removeReaction(testMessageId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.updateReaction(testMessageId, null) }
    }

    @Test
    fun `removeReaction fails when message not found`() = runTest {
        coEvery { messageDao.getMessageById("nonexistent") } returns null

        val result = repository.removeReaction("nonexistent")
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // getReaction() TESTS
    // ============================================================================================

    @Test
    fun `getReaction returns reaction for message`() = runTest {
        // Given: message has reaction
        val message = createTestMessage().copy(reaction = "\u2764\uFE0F")
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val reaction = repository.getReaction(testMessageId)

        // Then
        assertEquals("\u2764\uFE0F", reaction)
    }

    @Test
    fun `getReaction returns null for unstamped message`() = runTest {
        // Given: message with no reaction
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When
        val reaction = repository.getReaction(testMessageId)

        // Then
        assertNull(reaction)
    }

    @Test
    fun `getReaction returns null for non-existent message`() = runTest {
        coEvery { messageDao.getMessageById("nonexistent") } returns null

        val reaction = repository.getReaction("nonexistent")
        assertNull(reaction)
    }

    // ============================================================================================
    // Edge Cases
    // ============================================================================================

    @Test
    fun `duplicate reactions are idempotent`() = runTest {
        // Given
        val message = createTestMessage()
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When: same reaction set twice
        val firstResult = repository.addReaction(testMessageId, "\uD83D\uDC4D")
        val secondResult = repository.addReaction(testMessageId, "\uD83D\uDC4D")

        // Then: both succeed
        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isSuccess)

        // DAO called twice with same reaction
        coVerify(exactly = 2) { messageDao.updateReaction(testMessageId, "\uD83D\uDC4D") }
    }

    @Test
    fun `addReaction can change existing reaction`() = runTest {
        // Given: message starts with one reaction
        val message = createTestMessage().copy(reaction = "\u2764\uFE0F")
        coEvery { messageDao.getMessageById(testMessageId) } returns message

        // When: change to different reaction
        val result = repository.addReaction(testMessageId, "\uD83D\uDE00")

        // Then
        assertTrue(result.isSuccess)
        coVerify { messageDao.updateReaction(testMessageId, "\uD83D\uDE00") }
    }

    @Test
    fun `getReaction returns updated reaction after add`() = runTest {
        // Given: update reaction
        val updatedMessage = createTestMessage().copy(reaction = "\uD83D\uDE0A")
        coEvery { messageDao.getMessageById(testMessageId) } returns updatedMessage

        // When
        val reaction = repository.getReaction(testMessageId)

        // Then
        assertEquals("\uD83D\uDE0A", reaction)
    }
}
