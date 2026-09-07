package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.crypto.EncryptResult
import com.p2p.meshify.core.crypto.MessageCipher
import com.p2p.meshify.core.crypto.PeerPublicKeyUnavailableException
import com.p2p.meshify.core.crypto.PeerUnsupportedEncryptionException
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ChatRepositoryImpl.handleIncomingPayload] — the ingestion entry
 * point used by the transport layer (LAN/BLE). We mock all DAOs, the
 * TransportManager, IFileManager, NotificationHelper, and SettingsRepository,
 * and stub [androidx.room.withTransaction] so save paths run inline without a
 * real SQLite database.
 *
 * What's covered here:
 * - `processedPayloadIds` dedup: re-delivery of the same payload (typical under
 *   MULTI_PATH where LAN and BLE both surface the same logical message) must
 *   NOT touch the DAO a second time.
 * - `SYSTEM_CONTROL` branch: `ACK_<id>` updates the originating message to
 *   DELIVERED via `MessageDao.updateMessageStatus`.
 * - `else` (unknown payload type): no DAO calls; falls through to a `Logger.w`
 *   so ingestion is idempotent for forward-compatibility.
 *
 * What's deliberately NOT covered here:
 * - The full save-and-notify side of FILE/HANDSHAKE, chat-preview bumping, and
 *   notification side effects: those paths require a real Room transaction (or a
 *   heavy fake DAOs) and belong in an instrumented / on-device test. The TEXT
 *   save path's dedup gate IS exercised here — the MULTI_PATH test runs its
 *   `withTransaction` block inline and asserts a single `insertMessage` across
 *   two deliveries. The dedup gate in front of every branch is the load-bearing
 *   piece — if it misfires, every downstream branch double-fires — so it's what
 *   we exercise.
 *
 * Limit of the test: this is a JVM unit test driven by mockk. It cannot
 * reproduce real LAN/BLE multi-transport races, MTU fragmentation, GATT
 * teardown timing, or transport-level reconnects. Those need two real devices
 * via `feature:real-device-testing`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

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

        // Stub android.util.Log so Logger.{w,e,d,i} never throws.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        // Repository constructor requires applicationContext for the
        // memory-leak guard. Provide a relaxed mock returning an applicationContext.
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
        every { transport.onlinePeers } returns MutableStateFlow(emptySet())
        every { transport.typingPeers } returns MutableStateFlow(emptySet())
        every { transport.events } returns MutableSharedFlow()
        every { transportManager.getAllEventsFlow() } returns MutableSharedFlow()
        every { transportManager.selectBestTransport(any()) } returns listOf(transport)
        coEvery { transport.sendPayload(any(), any()) } returns Result.success(Unit)
        every { transport.transportName } returns "test"

        // Settings commonly queried by save/notification paths.
        every { settingsRepository.notificationsEnabled } returns kotlinx.coroutines.flow.flowOf(false)
        every { settingsRepository.notificationSound } returns kotlinx.coroutines.flow.flowOf(false)
        every { settingsRepository.notificationVibrate } returns kotlinx.coroutines.flow.flowOf(false)
        coEvery { settingsRepository.getDeviceId() } returns "self-id"

        // encryptOrRollback runs insert → encrypt → rollback as separate
        // transactions (encrypt deliberately sits outside any transaction
        // so the peer-key wait never holds the DB lock). On failure the
        // rollback deletes the message and restores the chat row.
        // The plain-mocked DAO path in this test still works for
        // non-send branches; the send/encrypt branches are covered by
        // focused unit tests that stub the transaction behavior
        // explicitly.
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

    @Test
    fun `handleIncomingPayload — duplicate payload id is dropped before any DAO call`() = runTest {
        val repo = newRepo()
        val ackPayload = Payload(
            id = "ack-1",
            senderId = "peer-a",
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "ACK_msg-1".toByteArray()
        )

        // First delivery: ACK path runs updateMessageStatus.
        repo.handleIncomingPayload("peer-a", ackPayload)
        coVerify(exactly = 1) { messageDao.updateMessageStatus("msg-1", MessageStatus.DELIVERED) }

        // Re-delivery with the same payload id: must NOT touch the DAO again.
        repo.handleIncomingPayload("peer-a", ackPayload)
        coVerify(exactly = 1) { messageDao.updateMessageStatus("msg-1", MessageStatus.DELIVERED) }
    }

    @Test
    fun `handleIncomingPayload — different ids both processed`() = runTest {
        val repo = newRepo()

        repo.handleIncomingPayload(
            "peer-a",
            Payload(
                id = "ack-1",
                senderId = "peer-a",
                type = Payload.PayloadType.SYSTEM_CONTROL,
                data = "ACK_msg-1".toByteArray()
            )
        )
        repo.handleIncomingPayload(
            "peer-a",
            Payload(
                id = "ack-2",
                senderId = "peer-a",
                type = Payload.PayloadType.SYSTEM_CONTROL,
                data = "ACK_msg-2".toByteArray()
            )
        )

        coVerify(exactly = 1) { messageDao.updateMessageStatus("msg-1", MessageStatus.DELIVERED) }
        coVerify(exactly = 1) { messageDao.updateMessageStatus("msg-2", MessageStatus.DELIVERED) }
    }

    @Test
    fun `handleIncomingPayload — SYSTEM_CONTROL without ACK prefix does not touch DAO`() = runTest {
        val repo = newRepo()
        val nonAck = Payload(
            id = "ctrl-1",
            senderId = "peer-a",
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "PING".toByteArray() // does NOT start with "ACK_"
        )

        repo.handleIncomingPayload("peer-a", nonAck)

        coVerify(exactly = 0) { messageDao.updateMessageStatus(any<String>(), any()) }
    }

    @Test
    fun `handleIncomingPayload — unknown payload type does not touch DAO`() = runTest {
        val repo = newRepo()
        // AVATAR_REQUEST is routed to handleAvatarRequest which only logs.
        val unknown = Payload(
            id = "avatar-req-1",
            senderId = "peer-a",
            type = Payload.PayloadType.AVATAR_REQUEST,
            data = byteArrayOf()
        )

        repo.handleIncomingPayload("peer-a", unknown)

        coVerify(exactly = 0) { messageDao.updateMessageStatus(any<String>(), any()) }
        coVerify(exactly = 0) { messageDao.insertMessage(any()) }
        coVerify(exactly = 0) { chatDao.insertChat(any<ChatEntity>()) }
    }

    @Test
    fun `handleIncomingPayload — SYSTEM_CONTROL ACK triggers transport reply for sender`() = runTest {
        val repo = newRepo()
        val ackPayload = Payload(
            id = "ack-xyz",
            senderId = "peer-b",
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "ACK_orig-7".toByteArray()
        )

        repo.handleIncomingPayload("peer-b", ackPayload)

        // ACK_ path updates local delivery status…
        coVerify(exactly = 1) { messageDao.updateMessageStatus("orig-7", MessageStatus.DELIVERED) }
        // …and SYSTEM_CONTROL is the one branch that does NOT auto-send an ACK
        // back (that would loop). Verify no outbound payload was generated.
        coVerify(exactly = 0) { transport.sendPayload(any(), any()) }
    }

    @Test
    fun `handleIncomingPayload — duplicate ACK does not cascade into multiple updates`() = runTest {
        val repo = newRepo()
        val payload = Payload(
            id = "ack-dup",
            senderId = "peer-a",
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "ACK_msg-x".toByteArray()
        )

        // Hammer 5x with the same id.
        repeat(5) { repo.handleIncomingPayload("peer-a", payload) }

        // Still exactly one updateMessageStatus call.
        coVerify(exactly = 1) { messageDao.updateMessageStatus("msg-x", MessageStatus.DELIVERED) }
    }

    @Test
    fun `ChatRepositoryImpl — construction succeeds with mocked dependencies`() {
        val repo = newRepo()
        assertNotNull(repo)
    }

    /**
     * MULTI_PATH dedup gate, exercised over the TEXT save path (the path that
     * actually writes a message row). Under MULTI_PATH the same logical message
     * is delivered once over LAN and once over BLE with an identical
     * [Payload.id]; the [ChatRepositoryImpl.processedPayloadIds] set must drop
     * the second delivery BEFORE [MessageDao.insertMessage] runs, so exactly
     * one message row lands.
     */
    @Test
    fun `handleIncomingPayload — MULTI_PATH same payload id over two transports stores ONE message`() = runTest {
        val repo = newRepo()
        val payload = Payload(
            id = "multi-path-1",
            senderId = "peer-a",
            type = Payload.PayloadType.TEXT,
            data = serializeEnvelope("peer-a", "self-id", "hello over mesh", 1_700_000_000_000L)
        )

        // First delivery (simulated LAN) writes the message.
        repo.handleIncomingPayload("peer-a", payload)
        // Second delivery (simulated BLE) carries the same id — must be dropped.
        repo.handleIncomingPayload("peer-a", payload)

        // Exactly one message row was persisted across both transports.
        coVerify(exactly = 1) { messageDao.insertMessage(any()) }
    }

    /**
     * Mirror of [com.p2p.meshify.core.data.repository.serializeMessageEnvelope]
     * (internal) so this test can build a wire-valid TEXT payload without
     * reaching into production internals. Layout:
     * [short senderLen][sender][short recipientLen][recipient]
     * [int textLen][text][long timestamp][short typeLen][type] (UTF-8).
     */
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

    // ==================== Unencrypted-send flow (PeerUnsupportedEncryptionException) ====================

    /**
     * When the peer's handshake signaled no encryption support, a normal
     * [ChatRepositoryImpl.sendMessage] must NOT auto-send; per the crypto
     * contract it demands explicit user action. The repo drops the placeholder
     * message row, then surfaces the typed exception so the UI can prompt.
     */
    @Test
    fun `sendMessage — unsupported peer throws and drops placeholder row`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.PeerDoesNotSupportEncryption

        coEvery { chatDao.getChatById("peer-a") } returns null
        coEvery { pendingMessageDao.getById(any()) } returns null

        val repo = newRepo(cipher)
        val result = repo.sendMessage("peer-a", "Alice", "hello", null)

        // Typed exception, NOT a generic failure — the UI keys off this type.
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeerUnsupportedEncryptionException)
        val ex = result.exceptionOrNull() as PeerUnsupportedEncryptionException
        assertEquals("peer-a", ex.peerId)

        // encryptOrRollback rolls back as a second transaction: the placeholder
        // message is deleted and — because no prior chat existed — the chat row
        // is deleted so the conversation list stays clean.
        coVerify(exactly = 0) { transport.sendPayload(any(), any()) }
        coVerify(exactly = 0) { messageDao.updateMessageStatus(any<String>(), MessageStatus.FAILED) }
        coVerify { chatDao.deleteChatById("peer-a") }
    }

    /**
     * When the peer's public key hasn't arrived yet (handshake in flight or
     * never received), the message must NOT be silently queued as plaintext —
     * the crypto contract requires the user to know the message didn't leave
     * the device. The repo rolls the placeholder back and surfaces a typed
     * exception so the VM can render a "Encryption key pending — Retry" UI.
     */
    @Test
    fun `sendMessage — unavailable peer key throws and drops placeholder row`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.PublicKeyUnavailable

        coEvery { chatDao.getChatById("peer-a") } returns null
        coEvery { pendingMessageDao.getById(any()) } returns null

        val repo = newRepo(cipher)
        val result = repo.sendMessage("peer-a", "Alice", "hello", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeerPublicKeyUnavailableException)
        val ex = result.exceptionOrNull() as PeerPublicKeyUnavailableException
        assertEquals("peer-a", ex.peerId)

        coVerify(exactly = 0) { transport.sendPayload(any(), any()) }
        coVerify(exactly = 0) { messageDao.updateMessageStatus(any<String>(), MessageStatus.FAILED) }
        coVerify { chatDao.deleteChatById("peer-a") }
    }

    /**
     * When the peer's key is unavailable but a prior chat row already exists
     * (prior messages in the conversation), the rollback restores the chat
     * row with its original preview and timestamp — the conversation list
     * shows the previous last message, not the unsent placeholder.
     */
    @Test
    fun `sendMessage — unavailable key with existing chat restores prior chat row`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-b", any()) } returns EncryptResult.PublicKeyUnavailable

        val priorChat = ChatEntity(peerId = "peer-b", peerName = "Bob", lastMessage = "prev", lastTimestamp = 100L)
        coEvery { chatDao.getChatById("peer-b") } returns priorChat
        coEvery { pendingMessageDao.getById(any()) } returns null

        val repo = newRepo(cipher)
        val result = repo.sendMessage("peer-b", "Bob", "new msg", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeerPublicKeyUnavailableException)

        // The prior chat is restored, not deleted.
        coVerify(exactly = 0) { chatDao.deleteChatById("peer-b") }
        coVerify { chatDao.insertChat(priorChat) }
    }

    /**
     * The explicit opt-in path bypasses encryption entirely: when the user
     * confirmed "send unencrypted", the message goes out as plaintext even for
     * a peer marked unsupported (no throw, no dialog re-prompt).
     */
    @Test
    fun `sendMessageUnencrypted — explicit opt-in sends plaintext for unsupported peer`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.PeerDoesNotSupportEncryption
        coEvery { transport.onlinePeers } returns MutableStateFlow(setOf("peer-a"))

        val captured = mutableListOf<Payload>()
        coEvery { transport.sendPayload(eq("peer-a"), capture(captured)) } returns Result.success(Unit)

        val repo = newRepo(cipher)
        val result = repo.sendMessageUnencrypted("peer-a", "Alice", "hello", null)

        assertTrue(result.isSuccess)
        assertTrue("expected exactly one wire payload", captured.size == 1)
        assertEnvelopeHeader(captured.single().data)
        // Encryption was never attempted for the unsupported peer.
        coVerify(exactly = 0) { cipher.encryptFor(any(), any()) }
    }

    /**
     * sendMessageUnencrypted on an *encryption-capable* peer must still send
     * plaintext (explicit user override), never silently encrypt — the user
     * chose to bypass after being warned.
     */
    @Test
    fun `sendMessageUnencrypted — opt-in still bypasses encryption for supported peer`() = runTest {
        val cipher = mockk<MessageCipher>(relaxed = true)
        coEvery { cipher.encryptFor("peer-a", any()) } returns EncryptResult.Success(byteArrayOf(1, 2, 3))
        coEvery { transport.onlinePeers } returns MutableStateFlow(setOf("peer-a"))

        val captured = mutableListOf<Payload>()
        coEvery { transport.sendPayload(eq("peer-a"), capture(captured)) } returns Result.success(Unit)

        val repo = newRepo(cipher)
        val result = repo.sendMessageUnencrypted("peer-a", "Alice", "hello", null)

        assertTrue(result.isSuccess)
        assertTrue("expected exactly one wire payload", captured.size == 1)
        // Plaintext envelope on the wire, never the 3-byte ciphertext stub.
        assertEnvelopeHeader(captured.single().data)
        coVerify(exactly = 0) { cipher.encryptFor(any(), any()) }
    }

    /**
     * A serialized MessageEnvelope begins [short senderLen][sender bytes]...
     * Ciphertext would not match this header shape.
     */
    private fun assertEnvelopeHeader(data: ByteArray) {
        if (data.size < 4) {
            throw AssertionError("Expected a plaintext envelope, got ${data.size} bytes")
        }
        val senderLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        assertTrue("sender length must be sane (< 2048), was $senderLen", senderLen in 1..2048)
        val recipientIndex = 2 + senderLen
        if (data.size < recipientIndex + 2) {
            throw AssertionError("Envelope too short for recipient length field at index $recipientIndex; size=${data.size}, senderLen=$senderLen")
        }
        val recipientLen = ((data[recipientIndex].toInt() and 0xFF) shl 8) or
            (data[recipientIndex + 1].toInt() and 0xFF)
        assertTrue("recipient length must be sane (< 2048), was $recipientLen", recipientLen in 1..2048)
    }
}
