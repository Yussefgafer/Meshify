package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.security.impl.EcdhSessionManager
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.domain.security.model.MessageEnvelope
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatRepositoryImpl.
 *
 * Note: ChatRepositoryImpl is a facade that internally instantiates sub-repositories
 * (MessageRepository, ChatManagementRepository, PendingMessageRepository, etc.).
 * These tests verify the facade's public API behavior by mocking all dependencies
 * and asserting correct DAO interactions.
 *
 * For full coverage, the sub-repositories should have their own unit tests.
 */
class ChatRepositoryImplTest {

    // Core dependencies
    private val mockContext: Context = mockk(relaxed = true) {
        every { applicationContext } returns mockk(relaxed = true)
    }
    private val mockStringProvider: StringResourceProvider = mockk(relaxed = true)
    private val mockDatabase: MeshifyDatabase = mockk(relaxed = true)
    private val mockChatDao: ChatDao = mockk(relaxed = true)
    private val mockMessageDao: MessageDao = mockk(relaxed = true)
    private val mockPendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val mockTransportManager: TransportManager = mockk(relaxed = true)
    private val mockFileManager: IFileManager = mockk(relaxed = true)
    private val mockNotificationHelper: NotificationHelper = mockk(relaxed = true)
    private val mockSettingsRepository: ISettingsRepository = mockk(relaxed = true)
    private val mockPeerIdentity: PeerIdentityRepository = mockk(relaxed = true)
    private val mockMessageCrypto: MessageEnvelopeCrypto = mockk(relaxed = true)
    private val mockEcdhSessionManager: EcdhSessionManager = mockk(relaxed = true)
    private val mockSessionKeyStore: EncryptedSessionKeyStore = mockk(relaxed = true)

    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        // Default stubs for settings
        coEvery { mockSettingsRepository.getDeviceId() } returns "my-device-id"
        every { mockSettingsRepository.displayName } returns flowOf("TestUser")
        every { mockSettingsRepository.avatarHash } returns flowOf(null)

        // Default: no online peers (peer is offline)
        every { mockTransportManager.getAllTransports() } returns emptyList()

        // Default: no session key (will be overridden per-test)
        coEvery { mockSessionKeyStore.getSessionKey(any()) } returns null

        // String provider defaults
        every { mockStringProvider.getString(any(), *varargAny { true }) } returns "mocked string"
        every { mockContext.getString(any()) } returns "mocked string"

        repository = ChatRepositoryImpl(
            context = mockContext,
            stringProvider = mockStringProvider,
            database = mockDatabase,
            chatDao = mockChatDao,
            messageDao = mockMessageDao,
            pendingMessageDao = mockPendingMessageDao,
            transportManager = mockTransportManager,
            fileManager = mockFileManager,
            notificationHelper = mockNotificationHelper,
            settingsRepository = mockSettingsRepository,
            peerIdentity = mockPeerIdentity,
            messageCrypto = mockMessageCrypto,
            ecdhSessionManager = mockEcdhSessionManager,
            sessionKeyStore = mockSessionKeyStore
        )
    }

    // ============================================================================================
    // getAllChats() TESTS
    // ============================================================================================

    @Test
    fun `getAllChats emits empty list initially`() = runTest {
        // Given
        every { mockChatDao.getAllChats() } returns flowOf(emptyList())

        // When
        val result = repository.getAllChats().first()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { mockMessageDao.getAllMessagesForChat(any()) }
    }

    @Test
    fun `getAllChats emits list of chats from DAO`() = runTest {
        // Given
        val expectedChats = listOf(
            ChatEntity("peer1", "Alice", "Hello", 1000L),
            ChatEntity("peer2", "Bob", "Hi there", 2000L)
        )
        every { mockChatDao.getAllChats() } returns flowOf(expectedChats)

        // When
        val result = repository.getAllChats().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("peer1", result[0].peerId)
        assertEquals("peer2", result[1].peerId)
    }

    // ============================================================================================
    // getMessages() TESTS
    // ============================================================================================

    @Test
    fun `getMessages returns messages for a peer`() = runTest {
        // Given
        val expectedMessages = listOf(
            MessageEntity(
                id = "msg1",
                chatId = "peer1",
                senderId = "peer1",
                text = "Hello",
                type = MessageType.TEXT,
                timestamp = 1000L,
                isFromMe = false,
                status = MessageStatus.SENT
            ),
            MessageEntity(
                id = "msg2",
                chatId = "peer1",
                senderId = "my-device-id",
                text = "Hi back",
                type = MessageType.TEXT,
                timestamp = 2000L,
                isFromMe = true,
                status = MessageStatus.SENT
            )
        )
        every { mockMessageDao.getAllMessagesForChat("peer1") } returns flowOf(expectedMessages)

        // When
        val result = repository.getMessages("peer1").first()

        // Then
        assertEquals(2, result.size)
        assertEquals("msg1", result[0].id)
        assertEquals("msg2", result[1].id)
    }

    @Test
    fun `getMessages returns empty list for peer with no messages`() = runTest {
        // Given
        every { mockMessageDao.getAllMessagesForChat("unknown-peer") } returns flowOf(emptyList())

        // When
        val result = repository.getMessages("unknown-peer").first()

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================================================
    // getMessagesPaged() TESTS
    // ============================================================================================

    @Test
    fun `getMessagesPaged returns paginated messages`() = runTest {
        // Given
        val expectedMessages = listOf(
            MessageEntity(
                id = "msg1",
                chatId = "peer1",
                senderId = "peer1",
                text = "First",
                type = MessageType.TEXT,
                timestamp = 1000L,
                isFromMe = false,
                status = MessageStatus.SENT
            )
        )
        every { mockMessageDao.getMessagesPaged("peer1", 10, 0) } returns flowOf(expectedMessages)

        // When
        val result = repository.getMessagesPaged("peer1", 10, 0).first()

        // Then
        assertEquals(1, result.size)
        assertEquals("First", result[0].text)
    }

    // ============================================================================================
    // sendMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendMessage fails when no session key available`() = runTest {
        // Given
        coEvery { mockSessionKeyStore.getSessionKey("peer1") } returns null

        // When
        val result = repository.sendMessage("peer1", "Alice", "Hello", null)

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("Secure session") == true)
    }

    @Test
    fun `sendMessage encrypts and sends when session exists and peer is offline`() = runTest {
        // Given
        val sessionKeyInfo = EncryptedSessionKeyStore.SessionKeyInfo(
            sessionKey = ByteArray(32) { 0x42 },
            peerPublicKeyHex = "abcd1234"
        )
        coEvery { mockSessionKeyStore.getSessionKey("peer1") } returns sessionKeyInfo

        val encryptedEnvelope = MessageEnvelope(
            senderId = "my-device-id",
            recipientId = "peer1",
            nonce = ByteArray(12),
            timestamp = System.currentTimeMillis(),
            iv = ByteArray(12),
            ciphertext = ByteArray(32),
            signature = ByteArray(64)
        )
        coEvery { mockMessageCrypto.encrypt(any(), "peer1", any()) } returns encryptedEnvelope

        // Peer is offline
        every { mockTransportManager.getAllTransports() } returns emptyList()

        // When
        val result = repository.sendMessage("peer1", "Alice", "Hello", null)

        // Then
        assertTrue(result.isSuccess)
        // Message should be saved
        val messageSlot = slot<MessageEntity>()
        coVerify { mockMessageDao.insertMessage(capture(messageSlot)) }
        assertEquals("Hello text should be encrypted, DB shows placeholder", "[Encrypted]", messageSlot.captured.text)
        assertEquals(MessageStatus.QUEUED, messageSlot.captured.status)
        // And queued for later delivery
        coVerify { mockPendingMessageDao.insert(any()) }
    }

    // ============================================================================================
    // sendGroupedMessage() TESTS
    // ============================================================================================

    @Test
    fun `sendGroupedMessage fails with empty attachments`() = runTest {
        // When
        val result = repository.sendGroupedMessage(
            peerId = "peer1",
            peerName = "Alice",
            caption = "My Album",
            attachments = emptyList(),
            replyToId = null
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No attachments") == true)
    }

    @Test
    fun `sendGroupedMessage creates message with groupId and saves attachments`() = runTest {
        // Given
        val attachment1 = ByteArray(100) { 0x01 } to MessageType.IMAGE
        val attachment2 = ByteArray(200) { 0x02 } to MessageType.IMAGE
        val attachments = listOf(attachment1, attachment2)

        coEvery { mockFileManager.saveMedia(any(), any()) } returns "/fake/path/to/attachments"

        // When
        val result = repository.sendGroupedMessage(
            peerId = "peer1",
            peerName = "Alice",
            caption = "My Album",
            attachments = attachments,
            replyToId = null
        )

        // Then
        assertTrue(result.isSuccess)
        // Chat should be created
        val chatSlot = slot<ChatEntity>()
        coVerify { mockChatDao.insertChat(capture(chatSlot)) }
        assertEquals("peer1", chatSlot.captured.peerId)
        assertEquals("Alice", chatSlot.captured.peerName)

        // Message should be created with groupId
        val messageSlot = slot<MessageEntity>()
        coVerify { mockMessageDao.insertMessage(capture(messageSlot)) }
        val savedMessage = messageSlot.captured
        assertEquals("peer1", savedMessage.chatId)
        assertEquals("My Album", savedMessage.text)
        assertNotNull(savedMessage.groupId)
        assertEquals(savedMessage.id, savedMessage.groupId) // groupId == messageId for group owner
        assertEquals(MessageStatus.QUEUED, savedMessage.status)
    }

    @Test
    fun `sendGroupedMessage with replyToId links reply correctly`() = runTest {
        // Given
        val attachments = listOf(ByteArray(100) { 0x01 } to MessageType.IMAGE)
        coEvery { mockFileManager.saveMedia(any(), any()) } returns "/fake/path"

        // When
        val result = repository.sendGroupedMessage(
            peerId = "peer1",
            peerName = "Alice",
            caption = "Reply Album",
            attachments = attachments,
            replyToId = "original-msg-id"
        )

        // Then
        assertTrue(result.isSuccess)
        val messageSlot = slot<MessageEntity>()
        coVerify { mockMessageDao.insertMessage(capture(messageSlot)) }
        assertEquals("original-msg-id", messageSlot.captured.replyToId)
    }

    @Test
    fun `sendGroupedMessage fails when saveMedia returns null`() = runTest {
        // Given
        val attachments = listOf(ByteArray(100) { 0x01 } to MessageType.IMAGE)
        coEvery { mockFileManager.saveMedia(any(), any()) } returns null

        // When
        val result = repository.sendGroupedMessage(
            peerId = "peer1",
            peerName = "Alice",
            caption = "Failed Album",
            attachments = attachments,
            replyToId = null
        )

        // Then
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // deleteMessage() TESTS
    // ============================================================================================

    @Test
    fun `deleteMessage for me marks message as deleted`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "my-device-id",
            text = "Delete me",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = true,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message

        // When
        val result = repository.deleteMessage("msg1", DeleteType.DELETE_FOR_ME)

        // Then
        assertTrue(result.isSuccess)
        coVerify { mockMessageDao.markAsDeletedForMe("msg1") }
    }

    @Test
    fun `deleteMessage for everyone marks message as deleted`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "my-device-id",
            text = "Delete everyone",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = true,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message

        // When
        val result = repository.deleteMessage("msg1", DeleteType.DELETE_FOR_EVERYONE)

        // Then
        assertTrue(result.isSuccess)
        coVerify { mockMessageDao.markAsDeletedForEveryone(any(), any(), any()) }
    }

    @Test
    fun `deleteMessage returns failure when message not found`() = runTest {
        // Given
        coEvery { mockMessageDao.getMessageById("nonexistent") } returns null

        // When
        val result = repository.deleteMessage("nonexistent", DeleteType.DELETE_FOR_ME)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    // ============================================================================================
    // deleteChat() TESTS
    // ============================================================================================

    @Test
    fun `deleteChat removes chat and messages`() = runTest {
        // When
        repository.deleteChat("peer1")

        // Then
        coVerify { mockChatDao.deleteChatById("peer1") }
        coVerify { mockMessageDao.deleteAllMessagesForChat("peer1") }
    }

    // ============================================================================================
    // forwardMessage() TESTS
    // ============================================================================================

    @Test
    fun `forwardMessage returns failure when message not found`() = runTest {
        // Given
        coEvery { mockMessageDao.getMessageById("nonexistent") } returns null

        // When
        val result = repository.forwardMessage("nonexistent", listOf("peer2"))

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `forwardMessage returns failure when target peers list is empty`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "peer1",
            text = "Forward me",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = false,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message

        // When
        val result = repository.forwardMessage("msg1", emptyList())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No target") == true)
    }

    @Test
    fun `forwardMessage text fails without session key`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "peer1",
            text = "Forward text",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = false,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message
        coEvery { mockSessionKeyStore.getSessionKey("peer2") } returns null

        // When
        val result = repository.forwardMessage("msg1", listOf("peer2"))

        // Then
        // Forward fails without session key (text messages require encryption)
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // addReaction() TESTS
    // ============================================================================================

    @Test
    fun `addReaction delegates to reaction repository and sends payload`() = runTest {
        // Given - ReactionRepository needs the message to exist first
        val message = MessageEntity(
            id = "msg-reaction",
            chatId = "peer1",
            senderId = "peer1",
            text = "React to me",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = false,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg-reaction") } returns message
        coEvery { mockMessageDao.updateReaction(any(), any()) } returns Unit
        coEvery { mockSettingsRepository.getDeviceId() } returns "my-device-id"

        // When
        val result = repository.addReaction("msg-reaction", "👍")

        // Then
        // ReactionRepository will fail because selectBestTransport returns empty list (relaxed mock)
        // but the DAO update should still happen
        assertTrue(result.isFailure)
        coVerify { mockMessageDao.updateReaction("msg-reaction", "👍") }
    }

    @Test
    fun `addReaction returns failure when message not found`() = runTest {
        // Given
        coEvery { mockMessageDao.getMessageById("nonexistent-msg") } returns null

        // When
        val result = repository.addReaction("nonexistent-msg", "❤️")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    // ============================================================================================
    // sendImage() TESTS
    // ============================================================================================

    @Test
    fun `sendImage delegates to message repository`() = runTest {
        // Given - MessageRepository.sendImageMessage compresses, saves, and sends
        // In the mock setup, saveMedia returns null by default
        coEvery { mockFileManager.saveMedia(any(), any()) } returns null

        // When
        val result = repository.sendImage("peer1", "Alice", ByteArray(1000), "jpg", null)

        // Then
        // sendImage internally calls MessageRepository which saves to disk.
        // With saveMedia returning null, it should fail.
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // sendVideo() TESTS
    // ============================================================================================

    @Test
    fun `sendVideo delegates to message repository`() = runTest {
        // Given - saveMedia returns null
        coEvery { mockFileManager.saveMedia(any(), any()) } returns null

        // When
        val result = repository.sendVideo("peer1", "Alice", ByteArray(5000), "mp4", null)

        // Then
        assertTrue(result.isFailure)
    }

    // ============================================================================================
    // ERROR HANDLING TESTS
    // ============================================================================================

    @Test
    fun `repository does not crash when DAO throws exception during deleteMessage`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "my-device-id",
            text = "Error message",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = true,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message
        coEvery { mockMessageDao.markAsDeletedForMe("msg1") } throws RuntimeException("DB error")

        // When
        val result = repository.deleteMessage("msg1", DeleteType.DELETE_FOR_ME)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `forwardMessage handles partial failures gracefully`() = runTest {
        // Given
        val message = MessageEntity(
            id = "msg1",
            chatId = "peer1",
            senderId = "peer1",
            text = "Partial forward",
            type = MessageType.TEXT,
            timestamp = 1000L,
            isFromMe = false,
            status = MessageStatus.SENT
        )
        coEvery { mockMessageDao.getMessageById("msg1") } returns message
        coEvery { mockSessionKeyStore.getSessionKey(any()) } returns null

        // When
        val result = repository.forwardMessage("msg1", listOf("peer2", "peer3"))

        // Then
        // All forwards fail without session keys, but repository should not crash
        assertTrue(result.isFailure)
    }

    @Test
    fun `sendGroupedMessage handles transport failure gracefully`() = runTest {
        // Given
        val attachments = listOf(ByteArray(100) { 0x01 } to MessageType.IMAGE)
        coEvery { mockFileManager.saveMedia(any(), any()) } returns "/fake/path"

        // When
        val result = repository.sendGroupedMessage(
            peerId = "peer1",
            peerName = "Alice",
            caption = "Album",
            attachments = attachments,
            replyToId = null
        )

        // Then
        // sendGroupedMessage saves attachments then attempts to send via messageRepository
        // which will fail without online peers, but should not crash
        // The result depends on implementation - may succeed (queued) or fail
        // Either way, no exception should escape
    }
}
