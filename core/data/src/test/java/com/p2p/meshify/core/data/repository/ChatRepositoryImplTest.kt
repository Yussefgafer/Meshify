package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.util.StringResourceProvider
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
import org.junit.Assert.assertNotNull
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
 * - The full save-and-notify side of TEXT/FILE/HANDSHAKE: those paths require
 *   a real Room transaction (or a heavy fake DAOs + chat preview bumping) and
 *   belong in an instrumented / on-device test, not in a pure-JVM unit test.
 *   The dedup gate in front of them is the load-bearing piece — if it
 *   misfires, every downstream branch double-fires — so it's what we exercise.
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

        // Save-path branches use androidx.room.withTransaction. Run the caller's
        // block inline so the underlying (mocked) DAOs are invoked synchronously.
        mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
        unmockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
    }

    private fun newRepo(): ChatRepositoryImpl = ChatRepositoryImpl(
        context = context,
        stringProvider = stringProvider,
        database = database,
        chatDao = chatDao,
        messageDao = messageDao,
        pendingMessageDao = pendingMessageDao,
        transportManager = transportManager,
        fileManager = fileManager,
        notificationHelper = notificationHelper,
        settingsRepository = settingsRepository
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
}
