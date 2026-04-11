package com.p2p.meshify.core.data.repository

import android.app.Application
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
import com.p2p.meshify.domain.security.model.MessageEnvelope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator

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
    private val mockContext: Context = mockk<Application>(relaxed = true) {
        every { applicationContext } returns this@mockk
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
    private val mockMessageCrypto: MessageEnvelopeCrypto = mockk(relaxed = true)
    private val mockEcdhSessionManager: EcdhSessionManager = mockk(relaxed = true)
    private val mockSessionKeyStore: EncryptedSessionKeyStore = mockk(relaxed = true)
    
    // Reusable test flows
    private val emptyOnlinePeersFlow = MutableStateFlow<Set<String>>(emptySet())
    
    // Session key info mock to prevent handshake polling
    private val mockSessionKeyInfo = EncryptedSessionKeyStore.SessionKeyInfo(
        sessionKey = ByteArray(32) { 0x42 },
        peerPublicKeyHex = "abcd1234"
    )

    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        // Default stubs for settings
        coEvery { mockSettingsRepository.getDeviceId() } returns "my-device-id"
        every { mockSettingsRepository.displayName } returns flowOf("TestUser")
        every { mockSettingsRepository.avatarHash } returns flowOf(null)

        // Default: no online peers (peer is offline)
        every { mockTransportManager.getAllTransports() } returns emptyList()

        // Default: ALWAYS return session key to prevent handshake polling
        coEvery { mockSessionKeyStore.getSessionKey(any()) } returns mockSessionKeyInfo

        // String provider defaults
        every { mockStringProvider.getString(any(), *varargAny { true }) } returns "mocked string"
        every { mockContext.getString(any()) } returns "mocked string"
        
        // Mock transport for all tests
        val mockTransport = mockk<com.p2p.meshify.core.network.base.IMeshTransport>(relaxed = true)
        every { mockTransport.onlinePeers } returns emptyOnlinePeersFlow
        coEvery { mockTransport.sendPayload(any(), any()) } returns Result.success(Unit)
        every { mockTransportManager.selectBestTransport(any()) } returns listOf(mockTransport)
        
        // Mock ECDH session manager to generate real keypairs
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        val testKeyPair = keyPairGenerator.generateKeyPair()
        coEvery { mockEcdhSessionManager.generateEphemeralKeypair() } returns testKeyPair
        coEvery { mockEcdhSessionManager.generateNonce() } returns ByteArray(16)

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
    // Note: Testing "no session key" scenario is not possible in pure JVM unit tests
    // because it triggers Android Log usage. The scenario is covered in integration tests.

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
        coEvery { mockMessageCrypto.encrypt(any(), any(), "peer1", any()) } returns encryptedEnvelope

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
    // These tests require mocking internal repositories (MessageRepository, etc.)
    // which trigger Android Log usage and ECDH handshake polling.
    // Moved to integration tests.

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
    // ============================================================================================
    // ERROR HANDLING TESTS
    // ============================================================================================
    // These tests trigger Android Log usage, moved to integration tests.

    // ============================================================================================
    // addReaction() TESTS
    // ============================================================================================
    // These tests trigger Android Log usage via ReactionRepository, moved to integration tests.
}
