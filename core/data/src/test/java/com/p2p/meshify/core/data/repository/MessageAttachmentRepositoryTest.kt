package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MessageAttachmentRepository.
 *
 * Covers:
 * - Saving attachments (single and multiple)
 * - Retrieving attachments for a message
 * - Deleting attachments
 * - Edge cases (empty lists, missing files)
 * - Grouped message sending
 */
@RunWith(RobolectricTestRunner::class)
class MessageAttachmentRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private val messageDao: MessageDao = mockk(relaxed = true)
    private val fileManager: IFileManager = mockk(relaxed = true)

    private lateinit var repository: MessageAttachmentRepository

    private val testMessageId = "msg-001"
    private val testFilePath = "/tmp/media/sent_album_msg-001_0.jpg"

    @Before
    fun setup() {
        // Return unique path for each saveMedia call so filenames are distinct
        var callIndex = 0
        coEvery { fileManager.saveMedia(any<String>(), any<ByteArray>()) } answers {
            val fileName = arg<String>(0)
            "/tmp/media/$fileName"
        }

        repository = MessageAttachmentRepository(
            messageDao = messageDao,
            fileManager = fileManager,
            ioDispatcher = testDispatcher
        )
    }

    // ============================================================================================
    // saveAttachments() TESTS
    // ============================================================================================

    @Test
    fun `saveAttachments saves single attachment`() = runTest(testDispatcher) {
        // Given
        val attachments = listOf(
            byteArrayOf(0x01, 0x02, 0x03) to MessageType.IMAGE
        )

        // When
        val result = repository.saveAttachments(testMessageId, attachments)

        // Then
        assertTrue(result.isSuccess)
        val entities = result.getOrNull()
        assertNotNull(entities)
        assertEquals(1, entities!!.size)
        assertEquals(testFilePath, entities.first().filePath)
        assertEquals(MessageType.IMAGE, entities.first().type)
        assertEquals(testMessageId, entities.first().messageId)

        coVerify { fileManager.saveMedia(any(), any()) }
        coVerify { messageDao.insertMessageAttachments(entities) }
    }

    @Test
    fun `saveAttachments saves multiple attachments`() = runTest(testDispatcher) {
        // Given: 3 attachments (2 images, 1 video)
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE,
            byteArrayOf(0x02) to MessageType.IMAGE,
            byteArrayOf(0x03) to MessageType.VIDEO
        )

        // When
        val result = repository.saveAttachments(testMessageId, attachments)

        // Then
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)

        val entities = result.getOrNull()
        assertNotNull("entities should not be null", entities)

        assertEquals("Expected 3 entities", 3, entities!!.size)

        // Verify filenames are unique
        val filenames = entities.map { it.filePath }
        assertEquals("Unique filenames", 3, filenames.toSet().size)

        coVerify(exactly = 3) { fileManager.saveMedia(any(), any()) }
        coVerify { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun `saveAttachments fails with empty attachments list`() = runTest(testDispatcher) {
        // Given: empty list
        val attachments = emptyList<Pair<ByteArray, MessageType>>()

        // When
        val result = repository.saveAttachments(testMessageId, attachments)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No attachments") == true)
        coVerify(exactly = 0) { fileManager.saveMedia(any(), any()) }
        coVerify(exactly = 0) { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun `saveAttachments fails when file save fails`() = runTest(testDispatcher) {
        // Given: fileManager returns null
        coEvery { fileManager.saveMedia(any(), any()) } returns null

        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE
        )

        // When
        val result = repository.saveAttachments(testMessageId, attachments)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to save attachment") == true)
        coVerify(exactly = 0) { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun `saveAttachments fails if partial save fails`() = runTest(testDispatcher) {
        // Given: second save fails
        var callCount = 0
        coEvery { fileManager.saveMedia(any(), any()) } answers {
            callCount++
            if (callCount == 2) null else testFilePath
        }

        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE,
            byteArrayOf(0x02) to MessageType.IMAGE
        )

        // When
        val result = repository.saveAttachments(testMessageId, attachments)

        // Then: fails, no insert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun `saveAttachments uses jpg extension for images`() = runTest(testDispatcher) {
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE
        )

        repository.saveAttachments(testMessageId, attachments)

        coVerify { fileManager.saveMedia(match { it.contains("jpg") }, any()) }
    }

    @Test
    fun `saveAttachments uses mp4 extension for videos`() = runTest(testDispatcher) {
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.VIDEO
        )

        repository.saveAttachments(testMessageId, attachments)

        coVerify { fileManager.saveMedia(match { it.contains("mp4") }, any()) }
    }

    @Test
    fun `saveAttachments handles mixed image and video types`() = runTest(testDispatcher) {
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE,
            byteArrayOf(0x02) to MessageType.VIDEO
        )

        val result = repository.saveAttachments(testMessageId, attachments)

        assertTrue(result.isSuccess)
        val entities = result.getOrNull()
        assertEquals(MessageType.IMAGE, entities!![0].type)
        assertEquals(MessageType.VIDEO, entities[1].type)
    }

    // ============================================================================================
    // getAttachmentsForMessage() TESTS
    // ============================================================================================

    @Test
    fun `getAttachmentsForMessage returns attachments`() = runTest(testDispatcher) {
        // Given
        val expected = listOf(
            MessageAttachmentEntity(
                id = "att-1", type = MessageType.IMAGE,
                messageId = testMessageId, filePath = "/path/to/img.jpg"
            )
        )
        coEvery { messageDao.getAttachmentsForMessage(testMessageId) } returns expected

        // When
        val result = repository.getAttachmentsForMessage(testMessageId)

        // Then
        assertEquals(1, result.size)
        assertEquals("att-1", result.first().id)
    }

    @Test
    fun `getAttachmentsForMessage returns empty list when none exist`() = runTest(testDispatcher) {
        // Given
        coEvery { messageDao.getAttachmentsForMessage("empty-msg") } returns emptyList()

        // When
        val result = repository.getAttachmentsForMessage("empty-msg")

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================================================
    // getAllAttachments() TESTS
    // ============================================================================================

    @Test
    fun `getAllAttachments returns all attachments`() = runTest(testDispatcher) {
        // Given
        val expected = listOf(
            MessageAttachmentEntity(
                id = "att-1", type = MessageType.IMAGE,
                messageId = "msg-1", filePath = "/path/1.jpg"
            ),
            MessageAttachmentEntity(
                id = "att-2", type = MessageType.VIDEO,
                messageId = "msg-2", filePath = "/path/2.mp4"
            )
        )
        coEvery { messageDao.getAllAttachments() } returns expected

        // When
        val result = repository.getAllAttachments()

        // Then
        assertEquals(2, result.size)
    }

    // ============================================================================================
    // deleteAttachmentsForMessage() TESTS
    // ============================================================================================

    @Test
    fun `deleteAttachmentsForMessage deletes files and records`() = runTest(testDispatcher) {
        // Given: attachments exist
        val attachments = listOf(
            MessageAttachmentEntity(
                id = "att-1", type = MessageType.IMAGE,
                messageId = testMessageId, filePath = testFilePath
            )
        )
        coEvery { messageDao.getAttachmentsForMessage(testMessageId) } returns attachments

        // When
        repository.deleteAttachmentsForMessage(testMessageId)

        // Then: DAO delete was called
        coVerify { messageDao.deleteAttachmentsForMessages(listOf(testMessageId)) }
    }

    @Test
    fun `deleteAttachmentsForMessage handles empty attachments`() = runTest(testDispatcher) {
        // Given: no attachments
        coEvery { messageDao.getAttachmentsForMessage("empty-msg") } returns emptyList()

        // When
        repository.deleteAttachmentsForMessage("empty-msg")

        // Then: still calls DAO to clean up any orphaned records
        coVerify { messageDao.deleteAttachmentsForMessages(listOf("empty-msg")) }
    }

    @Test
    fun `deleteAttachmentsForMessage handles non-existent file paths`() = runTest(testDispatcher) {
        // Given: attachment with non-existent file
        val attachments = listOf(
            MessageAttachmentEntity(
                id = "att-1", type = MessageType.IMAGE,
                messageId = testMessageId, filePath = "/nonexistent/path/file.jpg"
            )
        )
        coEvery { messageDao.getAttachmentsForMessage(testMessageId) } returns attachments

        // When: should not throw
        repository.deleteAttachmentsForMessage(testMessageId)

        // Then: DAO delete still called
        coVerify { messageDao.deleteAttachmentsForMessages(listOf(testMessageId)) }
    }

    // ============================================================================================
    // sendGroupedMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendGroupedMessage fails with empty attachments`() = runTest(testDispatcher) {
        // Given
        val emptyAttachments = emptyList<Pair<ByteArray, MessageType>>()
        val mockMessageRepo = mockk<MessageRepository>(relaxed = true)

        // When
        val result = repository.sendGroupedMessage(
            messageId = testMessageId,
            peerId = "peer-123",
            peerName = "Alice",
            caption = "My Album",
            attachments = emptyAttachments,
            messageRepository = mockMessageRepo
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No attachments") == true)
    }

    @Test
    fun `sendGroupedMessage fails if saveAttachments fails`() = runTest(testDispatcher) {
        // Given: first save fails
        coEvery { fileManager.saveMedia(any(), any()) } returns null

        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE
        )
        val mockMessageRepo = mockk<MessageRepository>(relaxed = true)

        // When
        val result = repository.sendGroupedMessage(
            messageId = testMessageId,
            peerId = "peer-123",
            peerName = "Alice",
            caption = "Album",
            attachments = attachments,
            messageRepository = mockMessageRepo
        )

        // Then
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockMessageRepo.sendFileMessage(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sendGroupedMessage sends first attachment as representative`() = runTest(testDispatcher) {
        // Given
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.IMAGE,
            byteArrayOf(0x02) to MessageType.IMAGE
        )
        val mockMessageRepo = mockk<MessageRepository>(relaxed = true)
        coEvery { mockMessageRepo.sendFileMessage(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = repository.sendGroupedMessage(
            messageId = testMessageId,
            peerId = "peer-123",
            peerName = "Alice",
            caption = "Vacation Photos",
            attachments = attachments,
            messageRepository = mockMessageRepo
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify {
            mockMessageRepo.sendFileMessage(
                peerId = "peer-123",
                peerName = "Alice",
                fileBytes = byteArrayOf(0x01),
                fileName = "Album: Vacation Photos",
                fileType = MessageType.IMAGE,
                replyToId = null
            )
        }
    }

    @Test
    fun `sendGroupedMessage sets type to VIDEO when all are videos`() = runTest(testDispatcher) {
        // Given: all video attachments
        val attachments = listOf(
            byteArrayOf(0x01) to MessageType.VIDEO,
            byteArrayOf(0x02) to MessageType.VIDEO
        )
        val mockMessageRepo = mockk<MessageRepository>(relaxed = true)
        coEvery { mockMessageRepo.sendFileMessage(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = repository.sendGroupedMessage(
            messageId = testMessageId,
            peerId = "peer-123",
            peerName = "Alice",
            caption = "Videos",
            attachments = attachments,
            messageRepository = mockMessageRepo
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify {
            mockMessageRepo.sendFileMessage(
                peerId = "peer-123",
                peerName = "Alice",
                fileBytes = any(),
                fileName = any(),
                fileType = MessageType.VIDEO,
                replyToId = null
            )
        }
    }
}
