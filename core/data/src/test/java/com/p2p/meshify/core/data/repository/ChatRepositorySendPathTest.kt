package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.crypto.EncryptResult
import com.p2p.meshify.core.crypto.MessageCipher
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import java.nio.ByteBuffer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused send-path tests for [ChatRepositoryImpl].
 *
 * Mirrors the production flow `encryptOrRollback` → `sendPlaintextPayload`:
 * - `sendMessage` happy path: encrypted payload rides the wire, message marked SENT.
 * - first send failure: message marked FAILED and a pending-queue entry is written.
 * - `retryFailedMessage` (via `PendingMessageRepository.retrySingleMessage`):
 *   delivers the queued payload and clears the pending entry + SENT status.
 * - `sendMessageUnencrypted`: explicit user opt-in sends the plaintext envelope
 *   (no encryption attempted, even when a cipher is available).
 * - MULTI_PATH dedup: the same `payload.id` delivered twice inserts exactly one row.
 *
 * Wire-valid TEXT envelopes are built with [serializeEnvelope] (same layout as
 * `ChatRepositoryImplTest`), so the TEXT save path runs its real decode code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositorySendPathTest {

    private lateinit var context: Context
    private lateinit var stringProvider: StringResourceProvider
    private lateinit var database: MeshifyDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var pendingMessageDao: PendingMessageDao
    private lateinit var transportManager: TransportManager
    private lateinit var fileManager: IFileManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var transport: IMeshTransport

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        val appContext = mockk<Context>(relaxed = true)
        every { appContext.applicationContext } returns appContext
        context = appContext

        stringProvider = mockk(relaxed = true)
        database = mockk(relaxed = true)
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        pendingMessageDao = mockk(relaxed = true)
        fileManager = mockk(relaxed = true)
        notificationHelper = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        transportManager = mockk(relaxed = true)
        transport = mockk(relaxed = true)

        every { transportManager.getAllTransports() } returns listOf(transport)
        // Both sendPlaintextPayload (ChatRepositoryImpl) and the pending-retry
        // path (PendingMessageRepository) resolve the wire transport here.
        every { transportManager.selectBestTransport(any()) } returns listOf(transport)
        every { transport.onlinePeers } returns MutableStateFlow(setOf("peer-a"))
        every { transport.typingPeers } returns MutableStateFlow(emptySet())
        every { transport.events } returns MutableSharedFlow()
        every { transportManager.getAllEventsFlow() } returns MutableSharedFlow()
        every { transport.transportName } returns "test"
        coEvery { transport.sendPayload(any(), any()) } returns Result.success(Unit)

        every { settingsRepository.notificationsEnabled } returns kotlinx.coroutines.flow.flowOf(false)
        every { settingsRepository.notificationSound } returns kotlinx.coroutines.flow.flowOf(false)
        every { settingsRepository.notificationVibrate } returns kotlinx.coroutines.flow.flowOf(false)
        coEvery { settingsRepository.getDeviceId() } returns "self-id"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    private fun newRepo(messageCipher: MessageCipher? = null): ChatRepositoryImpl = ChatRepositoryImpl(
        context = context,
        stringProvider = stringProvider,
        database = database,
        chatDao = chatDao,
        messageDao = messageDao,
        pendingMessageDao = pendingMessageDao,
        transportManager = transportManager,
        fileManager = fileManager,
        notificationHelper = notificationHelper,
        settingsRepository = settingsRepository,
        messageCipher = messageCipher,
        transactionRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    )

    /**
     * encryptOrRollback encrypts the envelope; the wire payload must carry the
     * ciphertext and message status must land at SENT — with no pending write.
     */
    @Test
    fun `sendMessage — success path sends encrypted payload and updates status to SENT`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.Success("cipher".toByteArray())

        val captured = mutableListOf<Payload>()
        coEvery { transport.sendPayload(eq("peer-a"), capture(captured)) } returns Result.success(Unit)

        val repo = newRepo(cipher)
        val result = repo.sendMessage("peer-a", "Alice", "hello", null)

        assertTrue(result.isSuccess)
        assertTrue("expected exactly one wire payload", captured.size == 1)
        assertTrue(captured.single().isEncrypted)
        assertTrue(captured.single().data.contentEquals("cipher".toByteArray()))
        coVerify(exactly = 1) { messageDao.updateMessageStatus(any(), MessageStatus.SENDING) }
        coVerify(exactly = 1) { messageDao.updateMessageStatus(any(), MessageStatus.SENT) }
        coVerify(exactly = 0) { messageDao.updateMessageStatus(any(), MessageStatus.FAILED) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
    }

    /**
     * All transports fail → the message is marked FAILED and a pending-queue
     * entry is persisted so a later retry can deliver the payload.
     */
    @Test
    fun `sendMessage — first failure queues pending message and marks FAILED`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.Success("cipher".toByteArray())
        coEvery { transport.sendPayload(any(), any()) } returns Result.failure(RuntimeException("down"))

        val repo = newRepo(cipher)
        val result = repo.sendMessage("peer-a", "Alice", "hello", null)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { messageDao.updateMessageStatus(any(), MessageStatus.SENDING) }
        coVerify(exactly = 1) { messageDao.updateMessageStatus(any(), MessageStatus.FAILED) }
        coVerify(exactly = 1) { pendingMessageDao.insert(any<PendingMessageEntity>()) }
        coVerify(exactly = 0) { messageDao.updateMessageStatus(any(), MessageStatus.SENT) }
    }

    /**
     * Explicit user opt-in sends the plaintext envelope — never ciphertext,
     * even when a cipher instance is available. (The security-event stream is
     * intentionally NOT asserted here: `securityEvents` is currently never
     * emitted in ChatRepositoryImpl — it is declared but unpopulated.)
     */
    @Test
    fun `sendMessageUnencrypted — explicit opt-in sends plaintext envelope`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.Success("cipher".toByteArray())

        val captured = mutableListOf<Payload>()
        coEvery { transport.sendPayload(eq("peer-a"), capture(captured)) } returns Result.success(Unit)

        val repo = newRepo(cipher)
        val result = repo.sendMessageUnencrypted("peer-a", "Alice", "plaintext-msg", null)

        assertTrue(result.isSuccess)
        assertTrue("expected exactly one wire payload", captured.size == 1)
        assertFalse(captured.single().isEncrypted)
        // The plaintext MessageEnvelope (not the 6-byte cipher stub) is on the wire.
        assertEnvelopeHeader(captured.single().data)
        coVerify(exactly = 0) { cipher.encryptFor(any(), any()) }
        coVerify(exactly = 1) { messageDao.updateMessageStatus(any(), MessageStatus.SENT) }
    }

    /**
     * retryFailedMessage → retrySingleMessage delivers the queued encrypted
     * payload, marks the row SENT, and clears the pending entry.
     */
    @Test
    fun `retry failed message — delivers from pending queue and clears pending entry`() = runTest {
        val repo = newRepo()
        val messageId = "m-1"
        coEvery { pendingMessageDao.getById(messageId) } returns PendingMessageEntity(
            id = messageId,
            recipientId = "peer-a",
            recipientName = "Alice",
            content = "hello",
            type = com.p2p.meshify.domain.model.MessageType.TEXT,
            encryptedPayload = "retry-payload".toByteArray()
        )
        coEvery { messageDao.getMessageById(messageId) } returns MessageEntity(
            id = messageId,
            chatId = "peer-a",
            senderId = "self-id",
            text = "hello",
            mediaPath = null,
            type = com.p2p.meshify.domain.model.MessageType.TEXT,
            timestamp = 1L,
            isFromMe = true,
            status = MessageStatus.FAILED
        )

        val result = repo.retryFailedMessage(messageId, "Alice")

        assertTrue(result.isSuccess)
        // finalizeSent clears the queue entry and marks the message SENT.
        coVerify(exactly = 1) { pendingMessageDao.deleteById(messageId) }
        coVerify(exactly = 1) { messageDao.updateMessageStatus(messageId, MessageStatus.SENT) }
        // The queued entry already existed, so no fresh insert happens.
        coVerify(exactly = 0) { pendingMessageDao.insert(any<PendingMessageEntity>()) }
        // A successful retry leaves the FAILED flip untouched.
        coVerify(exactly = 0) { messageDao.updateMessageStatus(messageId, MessageStatus.FAILED) }
    }

    /**
     * MULTI_PATH dedup gate: the same payload.id delivered twice (LAN + BLE)
     * inserts exactly one message row — the second delivery is dropped before
     * any DAO write.
     */
    @Test
    fun `handleIncomingPayload — MULTI_PATH duplicate payload id inserts only once`() = runTest {
        val repo = newRepo()
        val payload = Payload(
            id = "multi-path-1",
            senderId = "peer-a",
            type = Payload.PayloadType.TEXT,
            data = serializeEnvelope("peer-a", "self-id", "hello over mesh", 1_700_000_000_000L)
        )

        repo.handleIncomingPayload("peer-a", payload)
        repo.handleIncomingPayload("peer-a", payload)

        coVerify(exactly = 1) { messageDao.insertMessage(any()) }
    }

    /** Wire-valid TEXT envelope: [short senderLen][sender][short recLen][recipient][int textLen][text][long ts][short typeLen][type]. */
    private fun serializeEnvelope(
        senderId: String,
        recipientId: String,
        text: String,
        timestamp: Long
    ): ByteArray {
        val s = senderId.toByteArray(Charsets.UTF_8)
        val r = recipientId.toByteArray(Charsets.UTF_8)
        val t = text.toByteArray(Charsets.UTF_8)
        val ty = "text".toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(
            2 + s.size + 2 + r.size + 4 + t.size + 8 + 2 + ty.size
        )
        buf.putShort(s.size.toShort()); buf.put(s)
        buf.putShort(r.size.toShort()); buf.put(r)
        buf.putInt(t.size); buf.put(t)
        buf.putLong(timestamp)
        buf.putShort(ty.size.toShort()); buf.put(ty)
        return buf.array()
    }

    /** A serialized MessageEnvelope starts [short senderLen][sender bytes]… ciphertext would not match. */
    private fun assertEnvelopeHeader(data: ByteArray) {
        if (data.size < 4) {
            throw AssertionError("Expected a plaintext envelope, got ${data.size} bytes")
        }
        val senderLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        assertTrue("sender length must be sane (< 2048), was $senderLen", senderLen in 1..2048)
    }
}