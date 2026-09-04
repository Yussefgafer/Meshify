package com.p2p.meshify.core.data.repository

import androidx.room.withTransaction
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportMode
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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
 * MessageRepositoryTest — Verifies MessageRepository's own wiring:
 * online/offline branching, transport selection, status transitions, and
 * retry-queue insertion. The production saveAndSend path wraps its DAO writes
 * in androidx.room.withTransaction. We intercept that extension (mockkStatic of
 * androidx.room.RoomDatabaseKt__RoomDatabase_androidKt, set up in @Before) and
 * run the caller's suspend block inline, so the real online/offline branching
 * and DAO side effects execute on the test dispatcher without a live SQLite
 * database. Limitations: there is no real transactional atomicity, and large
 * file streaming / MTU negotiation / BLE reassembly is covered by
 * feature:real-device-testing on real hardware.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryTest {

    @Before
    fun mockAndroidLog() {
        // MessageRepository uses Dispatchers.IO via withContext; redirect
        // Main to an unconfined test dispatcher so suspend calls execute
        // inline on the test thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // MessageRepository calls Logger.e on send failures and on missing
        // files. Stub all android.util.Log overloads the project might hit.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any<String>()) } returns 0
        // mockkStatic is called on the runtime-side helper class via string
        // FQN: the public api stub androidx.room.RoomDatabaseKt has no
        // bytecode in the api jar (its body lives in the internal
        // RoomDatabaseKt__RoomDatabase_androidKt in the runtime jar). Both
        // forms resolve to the same compiled static method.
        mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
    }

    @After
    fun unmockAndroidLog() {
        unmockkStatic(android.util.Log::class)
        unmockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
        Dispatchers.resetMain()
    }

    private class FakeTransport(
        override val transportName: String,
        override val isAvailable: Boolean = true,
        override val capabilities: Set<TransportCapability> = emptySet(),
        val sendResult: Result<Unit> = Result.success(Unit)
    ) : IMeshTransport {
        val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
        private val _online = MutableStateFlow<Set<String>>(emptySet())
        private val _typing = MutableStateFlow<Set<String>>(emptySet())
        private val _runtime = MutableStateFlow(false)
        override val events = eventsFlow
        override val onlinePeers = _online
        override val typingPeers = _typing
        override val runtimeActive = _runtime
        fun setOnlinePeers(peers: Set<String>) { _online.value = peers }
        val sentPayloads = mutableListOf<Pair<String, Payload>>()
        override suspend fun start() {}
        override suspend fun stop() {}
        override suspend fun startDiscovery() {}
        override suspend fun stopDiscovery() {}
        override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
            sentPayloads += targetDeviceId to payload
            return sendResult
        }
    }

    private class TestSetup(
        val repo: MessageRepository,
        val database: MeshifyDatabase,
        val messageDao: MessageDao,
        val chatDao: ChatDao,
        val pendingDao: PendingMessageDao,
        val fileManager: IFileManager,
        val transport: FakeTransport?
    )

    private fun setup(
        scheduler: TestCoroutineScheduler,
        transport: FakeTransport?,
        onlinePeers: Set<String> = emptySet()
    ): TestSetup {
        val messageDao = mockk<MessageDao>(relaxed = true)
        coEvery { messageDao.insertMessage(any()) } returns Unit
        coEvery { messageDao.updateMessageStatus(any(), any()) } returns Unit
        coEvery { messageDao.getMessagesPaged(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { messageDao.getAllMessagesForChat(any()) } returns flowOf(emptyList())
        coEvery { messageDao.observeLatestMessages(any(), any()) } returns flowOf(emptyList())
        coEvery { messageDao.searchMessagesInChat(any(), any()) } returns flowOf(emptyList())
        coEvery { messageDao.getMessagesBefore(any(), any(), any()) } returns emptyList()
        coEvery { messageDao.getMessagesByIds(any()) } returns emptyList()
        coEvery { messageDao.getAttachmentsForGroups(any()) } returns emptyList()

        val chatDao = mockk<ChatDao>(relaxed = true)
        coEvery { chatDao.insertChat(any()) } returns Unit

        val pendingDao = mockk<PendingMessageDao>(relaxed = true)
        coEvery { pendingDao.insert(any()) } returns Unit

        val fileManager = mockk<IFileManager>(relaxed = true)
        coEvery { fileManager.saveMedia(any(), any()) } returns "/tmp/saved.bin"
        coEvery { fileManager.stageBytes(any(), any()) } returns "/tmp/staged.bin"
        coEvery { fileManager.stageFile(any(), any()) } returns "/tmp/staged-file.bin"

        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)
        coEvery { settings.getDeviceId() } returns "my-device-id"

        val context = mockk<android.content.Context>(relaxed = true)
        val manager = TransportManager(
            context = context,
            settingsRepository = settings,
            injectedManagerScope = TestScope(scheduler)
        )
        if (transport != null) {
            manager.registerTransport("lan", transport)
            transport.setOnlinePeers(onlinePeers)
        }

        val database = mockk<MeshifyDatabase>(relaxed = true)
        // Intercept the Room withTransaction extension and run the caller's
        // suspend block inline on the test dispatcher, so real DAO side effects
        // and branching execute without a live SQLite DB. The block is a plain
        // no-receiver suspend lambda (suspend () -> Unit), passed as the 2nd arg
        // of the static function (the database is the 1st arg / receiver).
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            val block = secondArg<suspend () -> Unit>()
            block()
        }

        val repo = MessageRepository(
            database = database,
            messageDao = messageDao,
            chatDao = chatDao,
            pendingMessageDao = pendingDao,
            transportManager = manager,
            fileManager = fileManager,
            settingsRepository = settings
        )

        return TestSetup(repo, database, messageDao, chatDao, pendingDao, fileManager, transport)
    }

    @Test
    fun sendFileMessage_peerOffline_queuesAndReturnsSuccess() = runTest(UnconfinedTestDispatcher()) {
        // No transport online for peerA -> saveAndSend must write the message,
        // stage the file bytes, queue a pending row, and return success
        // without calling sendPayload.
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val s = setup(scheduler, lan, onlinePeers = emptySet())

        val result = s.repo.sendFileMessage(
            peerId = "peerA",
            peerName = "Bob",
            fileBytes = "hello".toByteArray(),
            fileName = "x.bin",
            fileType = MessageType.FILE,
            replyToId = null
        )

        assertTrue("offline send must succeed (queued)", result.isSuccess)
        assertEquals("no sendPayload when offline", 0, lan.sentPayloads.size)
        coVerify { s.messageDao.insertMessage(any()) }
        coVerify { s.pendingDao.insert(any()) }
        coVerify { s.fileManager.stageBytes(any(), any()) }
    }

    @Test
    fun sendFileMessage_peerOnline_transportSucceeds_marksSent() = runTest(UnconfinedTestDispatcher()) {
        // Peer online + transport success -> payload is sent and the message
        // is marked SENT (no pending row).
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val s = setup(scheduler, lan, onlinePeers = setOf("peerA"))

        val result = s.repo.sendFileMessage(
            peerId = "peerA",
            peerName = "Bob",
            fileBytes = "hello".toByteArray(),
            fileName = "x.bin",
            fileType = MessageType.IMAGE,
            replyToId = null
        )
        assertTrue("online send must succeed", result.isSuccess)
        assertEquals(1, lan.sentPayloads.size)
        coVerify { s.messageDao.updateMessageStatus(any(), MessageStatus.SENT) }
        coVerify(exactly = 0) { s.pendingDao.insert(any()) }
    }

    @Test
    fun sendFileMessage_peerOnline_transportFails_queuesForRetry() = runTest(UnconfinedTestDispatcher()) {
        // Transport rejects the payload -> message marked FAILED and a pending
        // row is inserted for retry; the call returns a failure Result.
        val scheduler = testScheduler
        val lan = FakeTransport("lan", sendResult = Result.failure(Exception("nope")))
        val s = setup(scheduler, lan, onlinePeers = setOf("peerA"))

        val result = s.repo.sendFileMessage(
            peerId = "peerA",
            peerName = "Bob",
            fileBytes = "hello".toByteArray(),
            fileName = "x.bin",
            fileType = MessageType.FILE,
            replyToId = null
        )
        assertTrue("transport failure must surface as error", result.isFailure)
        coVerify { s.messageDao.updateMessageStatus(any(), MessageStatus.FAILED) }
        coVerify { s.pendingDao.insert(any()) }
        coVerify { s.fileManager.stageBytes(any(), any()) }
    }

    @Test
    fun sendFileMessage_noTransport_queuesAndSucceeds() = runTest(UnconfinedTestDispatcher()) {
        // No transport registered at all -> getAllTransports() is empty so
        // saveAndSend treats the peer as OFFLINE (the online check is
        // "any transport lists this peer in its onlinePeers"), and defers the
        // message: it stages the file bytes, inserts a pending row, and
        // returns SUCCESS (safely queued for retry). The FAILED+queue branch
        // (selectBestTransport == null) is only reachable when a peer reports
        // online yet no transport is selectable; TransportManager never yields
        // that today, so it is not asserted here.
        val scheduler = testScheduler
        val s = setup(scheduler, transport = null, onlinePeers = emptySet())

        val result = s.repo.sendFileMessage(
            peerId = "peerA",
            peerName = "Bob",
            fileBytes = "hello".toByteArray(),
            fileName = "x.bin",
            fileType = MessageType.FILE,
            replyToId = null
        )
        assertTrue("no transport -> safely queued, not an error", result.isSuccess)
        coVerify { s.pendingDao.insert(any()) }
        coVerify { s.fileManager.stageBytes(any(), any()) }
        coVerify(exactly = 0) { s.messageDao.updateMessageStatus(any(), MessageStatus.FAILED) }
    }

    @Test
    fun getMessages_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val expected = listOf(
            MessageEntity(
                id = "m1", chatId = "peerA", senderId = "other",
                text = "hi", type = MessageType.TEXT,
                timestamp = 1L, isFromMe = false, status = MessageStatus.RECEIVED
            )
        )
        coEvery { s.messageDao.getAllMessagesForChat("peerA") } returns flowOf(expected)
        val collected = mutableListOf<List<MessageEntity>>()
        s.repo.getMessages("peerA").collect { collected += it }
        assertEquals(1, collected.size)
        assertEquals("hi", collected.first().first().text)
    }

    @Test
    fun getMessagesPaged_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.getMessagesPaged("peerA", limit = 10, offset = 0)
        val collected = mutableListOf<List<MessageEntity>>()
        out.collect { collected += it }
        assertEquals(1, collected.size)
    }

    @Test
    fun observeLatestMessages_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.observeLatestMessages("peerA", limit = 20)
        val collected = mutableListOf<List<MessageEntity>>()
        out.collect { collected += it }
        assertEquals(1, collected.size)
    }

    @Test
    fun getMessagesBefore_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.getMessagesBefore("peerA", beforeTimestamp = 100L, limit = 5)
        assertNotNull(out)
    }

    @Test
    fun getAttachmentsForGroups_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.getAttachmentsForGroups(listOf("g1"))
        assertNotNull(out)
    }

    @Test
    fun getMessagesByIds_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.getMessagesByIds(listOf("m1"))
        assertNotNull(out)
    }

    @Test
    fun searchMessagesInChat_delegatesToMessageDao() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        val out = s.repo.searchMessagesInChat("peerA", "needle")
        val collected = mutableListOf<List<MessageEntity>>()
        out.collect { collected += it }
        assertEquals(1, collected.size)
    }
}
