package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.util.ImageCompressor
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/**
 * Unit tests for MessageRepository.
 *
 * Uses in-memory Room database for transaction testing.
 * Robolectric required for Android API access (ImageCompressor, file system).
 */
@RunWith(RobolectricTestRunner::class)
class MessageRepositoryTest {

    // In-memory database
    private lateinit var database: MeshifyDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var chatDao: ChatDao
    private lateinit var pendingMessageDao: PendingMessageDao

    // Mocks
    private val transportManager: TransportManager = mockk(relaxed = true)
    private val mockTransport: IMeshTransport = mockk(relaxed = true)
    private val fileManager: IFileManager = mockk(relaxed = true)
    private val settingsRepository: ISettingsRepository = mockk(relaxed = true)

    private lateinit var repository: MessageRepository

    private val testPeerId = "peer-123"
    private val testPeerName = "Alice"
    private val myDeviceId = "my-device-id"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
        chatDao = database.chatDao()
        pendingMessageDao = database.pendingMessageDao()

        // Transport mock: online by default
        every { mockTransport.onlinePeers } returns kotlinx.coroutines.flow.MutableStateFlow(setOf(testPeerId))
        every { transportManager.getAllTransports() } returns listOf(mockTransport)
        every { transportManager.selectBestTransport(any()) } returns listOf(mockTransport)
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)

        // Settings: device ID
        coEvery { settingsRepository.getDeviceId() } returns myDeviceId
        every { settingsRepository.displayName } returns kotlinx.coroutines.flow.flowOf("TestUser")

        // File manager: save succeeds
        coEvery { fileManager.saveMedia(any(), any()) } answers {
            val fileName = firstArg<String>()
            val dir = context.filesDir.resolve("media")
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(secondArg())
            file.absolutePath
        }

        repository = MessageRepository(
            database = database,
            messageDao = messageDao,
            chatDao = chatDao,
            pendingMessageDao = pendingMessageDao,
            transportManager = transportManager,
            fileManager = fileManager,
            settingsRepository = settingsRepository
        )
    }

    @After
    fun teardown() {
        database.close()
        unmockkAll()
    }

    // ============================================================================================
    // sendTextMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendTextMessage saves message and sends when peer online`() = runTest {
        // When
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "Hello, World!",
            replyToId = null
        )

        // Then: success
        assertTrue(result.isSuccess)

        // Chat record created
        val chat = chatDao.getChatById(testPeerId)
        assertNotNull(chat)

        // Message saved in database
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals("Hello, World!", messages.first().text)
        assertEquals(MessageStatus.SENT, messages.first().status)
        assertTrue(messages.first().isFromMe)
    }

    @Test
    fun `sendTextMessage queues when peer is offline`() = runTest {
        // Given: peer is offline
        every { transportManager.getAllTransports() } returns emptyList()
        every { transportManager.selectBestTransport(any()) } returns emptyList()

        // When
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "Offline message",
            replyToId = null
        )

        // Then: success (queued for later)
        assertTrue(result.isSuccess)

        // Message saved as QUEUED
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals(MessageStatus.QUEUED, messages.first().status)

        // Pending message created
        val pending = pendingMessageDao.getByRecipient(testPeerId)
        assertEquals(1, pending.size)
    }

    @Test
    fun `sendTextMessage saves replyToId when provided`() = runTest {
        // When
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "Replying",
            replyToId = "original-msg-id"
        )

        // Then
        assertTrue(result.isSuccess)
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals("original-msg-id", messages.first().replyToId)
    }

    @Test
    fun `sendTextMessage fails when transport fails`() = runTest {
        // Given: transport fails
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.failure(Exception("Send error"))

        // When
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "Will fail",
            replyToId = null
        )

        // Then: failure
        assertTrue(result.isFailure)

        // Message marked as FAILED
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals(MessageStatus.FAILED, messages.first().status)

        // Pending message queued for retry
        val pending = pendingMessageDao.getByRecipient(testPeerId)
        assertEquals(1, pending.size)
    }

    // ============================================================================================
    // sendImageMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendImageMessage compresses and sends image`() = runTest {
        // Given: mock ImageCompressor
        mockkObject(ImageCompressor)
        every { ImageCompressor.compress(any(), any(), any()) } returns ImageCompressor.CompressionResult(
            bytes = byteArrayOf(0x01, 0x02, 0x03),
            originalSize = 100,
            compressedSize = 3,
            compressionRatio = 97.0,
            width = 100,
            height = 100
        )

        // When
        val result = repository.sendImageMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            imageBytes = byteArrayOf(0x00, 0x01, 0x02),
            extension = "jpg",
            replyToId = null
        )

        // Then
        assertTrue(result.isSuccess)

        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.IMAGE, messages.first().type)
        assertNotNull(messages.first().mediaPath)
        assertEquals(MessageStatus.SENT, messages.first().status)
    }

    @Test
    fun `sendImageMessage fails when save fails`() = runTest {
        // Given: ImageCompressor succeeds but file save fails
        mockkObject(ImageCompressor)
        every { ImageCompressor.compress(any(), any(), any()) } returns ImageCompressor.CompressionResult(
            bytes = byteArrayOf(0x01, 0x02, 0x03),
            originalSize = 100,
            compressedSize = 3,
            compressionRatio = 97.0,
            width = 100,
            height = 100
        )
        coEvery { fileManager.saveMedia(any(), any()) } returns null

        // When
        val result = repository.sendImageMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            imageBytes = byteArrayOf(0x00),
            extension = "jpg",
            replyToId = null
        )

        // Then: failure - no message saved
        assertTrue(result.isFailure)
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertTrue(messages.isEmpty())
    }

    // ============================================================================================
    // sendVideoMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendVideoMessage sends valid video`() = runTest {
        // Given: small video under 50MB
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoDir = File(context.filesDir, "media").also { it.mkdirs() }
        val videoFile = File(videoDir, "test_video.mp4")
        videoFile.writeBytes(ByteArray(1024))
        coEvery { fileManager.saveMedia(any(), any()) } returns videoFile.absolutePath

        // When
        val result = repository.sendVideoMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            videoBytes = ByteArray(1024), // 1KB
            extension = "mp4",
            replyToId = null
        )

        // Then
        assertTrue(result.isSuccess)
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(MessageType.VIDEO, messages.first().type)
    }

    @Test
    fun `sendVideoMessage rejects video over 50MB`() = runTest {
        // Given: 51MB video
        val largeVideo = ByteArray(51 * 1024 * 1024)

        // When
        val result = repository.sendVideoMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            videoBytes = largeVideo,
            extension = "mp4",
            replyToId = null
        )

        // Then: failure
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("max 50MB") == true)
    }

    // ============================================================================================
    // sendFileMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendFileMessage sends file to online peer`() = runTest {
        // When
        val result = repository.sendFileMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            fileBytes = byteArrayOf(0x01, 0x02, 0x03),
            fileName = "test.bin",
            fileType = MessageType.FILE,
            replyToId = null
        )

        // Then
        assertTrue(result.isSuccess)
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.FILE, messages.first().type)
        assertEquals("test.bin", messages.first().text)
    }

    @Test
    fun `sendFileMessage queues file when peer offline`() = runTest {
        // Given: peer offline
        every { transportManager.getAllTransports() } returns emptyList()

        // When
        val result = repository.sendFileMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            fileBytes = byteArrayOf(0x01),
            fileName = "offline.bin",
            fileType = MessageType.FILE,
            replyToId = null
        )

        // Then
        assertTrue(result.isSuccess)
        val pending = pendingMessageDao.getByRecipient(testPeerId)
        assertEquals(1, pending.size)
    }

    // ============================================================================================
    // sendFileWithProgress() TESTS
    // ============================================================================================

    @Test
    fun `sendFileWithProgress sends file with progress callback`() = runTest {
        // Given: a temp file
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "test_${UUID.randomUUID()}.bin")
        tempFile.writeBytes(ByteArray(100) { it.toByte() })

        var progressValues = mutableListOf<Int>()

        // When
        val result = repository.sendFileWithProgress(
            messageId = UUID.randomUUID().toString(),
            peerId = testPeerId,
            peerName = testPeerName,
            file = tempFile,
            fileType = MessageType.FILE,
            caption = "Test file",
            replyToId = null,
            progressCallback = { progressValues.add(it) }
        )

        // Then
        assertTrue("sendFileWithProgress should succeed", result.isSuccess)
        assertTrue("Progress callback should have been called", progressValues.isNotEmpty())
        assertEquals(100, progressValues.last())

        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `sendFileWithProgress fails for non-existent file`() = runTest {
        // Given: non-existent file
        val missingFile = File("/nonexistent/path/file.bin")

        // When
        val result = repository.sendFileWithProgress(
            messageId = "test-id",
            peerId = testPeerId,
            peerName = testPeerName,
            file = missingFile,
            fileType = MessageType.FILE,
            caption = "Missing",
            replyToId = null
        )

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `sendFileWithProgress fails for oversized file`() = runTest {
        // Given: oversize file (over 100MB)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "oversize_test.bin")
        // Create a sparse file with large length without allocating 100MB in memory
        tempFile.writeBytes(ByteArray(1)) // minimal content
        // Use RandomAccessFile to set the length to just over the limit
        java.io.RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(com.p2p.meshify.domain.model.AppConstants.MAX_FILE_SIZE_BYTES + 1)
        }
        tempFile.deleteOnExit()

        // When
        val result = repository.sendFileWithProgress(
            messageId = "test-id",
            peerId = testPeerId,
            peerName = testPeerName,
            file = tempFile,
            fileType = MessageType.FILE,
            caption = "Oversized",
            replyToId = null
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too large") == true)
    }

    @Test
    fun `sendFileWithProgress queues when peer offline`() = runTest {
        // Given: peer offline
        every { transportManager.getAllTransports() } returns emptyList()
        every { mockTransport.onlinePeers } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "offline_test.bin")
        tempFile.writeBytes(ByteArray(50))

        // When
        val result = repository.sendFileWithProgress(
            messageId = "offline-test-id",
            peerId = testPeerId,
            peerName = testPeerName,
            file = tempFile,
            fileType = MessageType.FILE,
            caption = "Offline file",
            replyToId = null
        )

        // Then: queued
        assertTrue(result.isSuccess)
        val pending = pendingMessageDao.getByRecipient(testPeerId)
        assertEquals(1, pending.size)

        tempFile.delete()
    }

    // ============================================================================================
    // searchMessagesInChat() TESTS
    // ============================================================================================

    @Test
    fun `searchMessagesInChat returns matching messages`() = runTest {
        // Given: messages in database
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-1", chatId = testPeerId, senderId = myDeviceId,
                text = "Hello World", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = true, status = MessageStatus.SENT
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-2", chatId = testPeerId, senderId = testPeerId,
                text = "How are you?", type = MessageType.TEXT,
                timestamp = 2000L, isFromMe = false, status = MessageStatus.SENT
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-3", chatId = testPeerId, senderId = myDeviceId,
                text = "Goodbye", type = MessageType.TEXT,
                timestamp = 3000L, isFromMe = true, status = MessageStatus.SENT
            )
        )

        // When: search for "Hello"
        val results = repository.searchMessagesInChat(testPeerId, "Hello").first()

        // Then
        assertEquals(1, results.size)
        assertEquals("Hello World", results.first().text)
    }

    @Test
    fun `searchMessagesInChat returns empty for no match`() = runTest {
        // Given: messages in database
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-1", chatId = testPeerId, senderId = myDeviceId,
                text = "Hello", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = true, status = MessageStatus.SENT
            )
        )

        // When: search for non-matching text
        val results = repository.searchMessagesInChat(testPeerId, "ZZZZZZZZ").first()

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchMessagesInChat returns empty for empty chat`() = runTest {
        val results = repository.searchMessagesInChat(testPeerId, "test").first()
        assertTrue(results.isEmpty())
    }

    // ============================================================================================
    // getMessages() / getMessagesPaged() TESTS
    // ============================================================================================

    @Test
    fun `getMessages returns messages for chat`() = runTest {
        // Given
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-1", chatId = testPeerId, senderId = myDeviceId,
                text = "First", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = true, status = MessageStatus.SENT
            )
        )

        // When
        val messages = repository.getMessages(testPeerId).first()

        // Then
        assertEquals(1, messages.size)
        assertEquals("First", messages.first().text)
    }

    @Test
    fun `getMessagesPaged returns paginated messages`() = runTest {
        // Given: two messages
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-1", chatId = testPeerId, senderId = myDeviceId,
                text = "First", type = MessageType.TEXT,
                timestamp = 1000L, isFromMe = true, status = MessageStatus.SENT
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                id = "msg-2", chatId = testPeerId, senderId = testPeerId,
                text = "Second", type = MessageType.TEXT,
                timestamp = 2000L, isFromMe = false, status = MessageStatus.SENT
            )
        )

        // When: page with limit=1, offset=0
        val page = repository.getMessagesPaged(testPeerId, limit = 1, offset = 0).first()

        // Then
        assertEquals(1, page.size)
    }

    // ============================================================================================
    // sendTextMessage() Error Edge Cases
    // ============================================================================================

    @Test
    fun `sendTextMessage with empty text still sends`() = runTest {
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "",
            replyToId = null
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendTextMessage fails if no transport available and peer online`() = runTest {
        // Given: no transports but getAllTransports says we have transports?
        // Actually, the code checks transports via getAllTransports. Let's mock it differently.
        // Make selectBestTransport return empty list
        every { transportManager.selectBestTransport(any()) } returns emptyList()
        every { transportManager.getAllTransports() } returns listOf(mockTransport) // peer "online"

        // When
        val result = repository.sendTextMessage(
            peerId = testPeerId,
            peerName = testPeerName,
            text = "No transport",
            replyToId = null
        )

        // Then: send fails, message + pending saved
        assertTrue(result.isFailure)
        val messages = messageDao.getAllMessagesForChat(testPeerId).first()
        assertEquals(1, messages.size)
        assertEquals(MessageStatus.FAILED, messages.first().status)

        val pending = pendingMessageDao.getByRecipient(testPeerId)
        assertEquals(1, pending.size)
    }
}
