package com.p2p.meshify.core.data.repository

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PendingMessageRepositoryTest — Mocks PendingMessageDao, MessageDao,
 * TransportManager, and IFileManager. Limitations: this verifies the
 * repository's own queue/refresh/cleanup logic. Actual transport-level
 * retry/backoff behavior under partial failure is exercised end-to-end on
 * real hardware via feature:real-device-testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingMessageRepositoryTest {

    @Before
    fun mockAndroidLog() {
        // PendingMessageRepository calls Logger.e on retry failures, which
        // delegates to android.util.Log — not mocked on plain JVM by default.
        // mockkStatic alone declares intent; we must stub the overloads we hit
        // or the real Log.e throws "not mocked" on the first failure path.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any<String>()) } returns 0
    }

    @After
    fun unmockAndroidLog() {
        unmockkStatic(android.util.Log::class)
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
        val sentPayloads = mutableListOf<Payload>()
        override suspend fun start() {}
        override suspend fun stop() {}
        override suspend fun startDiscovery() {}
        override suspend fun stopDiscovery() {}
        override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
            sentPayloads += payload
            return sendResult
        }
    }

    private class TestSetup(
        val repo: PendingMessageRepository,
        val pendingDao: PendingMessageDao,
        val messageDao: MessageDao,
        val transport: FakeTransport?
    )

    private fun setup(
        scheduler: TestCoroutineScheduler,
        transport: FakeTransport?,
        pendingList: List<PendingMessageEntity> = emptyList(),
        messagesById: Map<String, MessageEntity> = emptyMap()
    ): TestSetup {
        val pendingDao = mockk<PendingMessageDao>(relaxed = true)
        coEvery { pendingDao.getAll() } returns pendingList
        coEvery { pendingDao.getByRecipient(any()) } returns pendingList
        coEvery { pendingDao.getById(any()) } returns pendingList.firstOrNull()
        coEvery { pendingDao.deleteById(any()) } returns Unit

        val messageDao = mockk<MessageDao>(relaxed = true)
        coEvery { messageDao.getMessagesByIds(any()) } returns messagesById.values.toList()
        coEvery { messageDao.getMessageById(any()) } answers {
            messagesById[firstArg()]
        }
        coEvery { messageDao.updateMessageStatus(any(), any()) } returns Unit

        val fileManager = mockk<IFileManager>(relaxed = true)

        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)

        val context = mockk<android.content.Context>(relaxed = true)
        val manager = TransportManager(
            context = context,
            settingsRepository = settings,
            injectedManagerScope = TestScope(scheduler)
        )
        if (transport != null) {
            manager.registerTransport("lan", transport)
        }

        val repo = PendingMessageRepository(pendingDao, messageDao, manager, fileManager)
        return TestSetup(repo, pendingDao, messageDao, transport)
    }

    private fun textPending(
        id: String = "msg1",
        recipientId: String = "peerA",
        text: String = "hello"
    ): Pair<PendingMessageEntity, MessageEntity> {
        val pm = PendingMessageEntity(
            id = id,
            recipientId = recipientId,
            recipientName = "Bob",
            content = text,
            type = MessageType.TEXT,
            timestamp = 1_700_000_000_000L,
            status = MessageStatus.QUEUED
        )
        val msg = MessageEntity(
            id = id,
            chatId = recipientId,
            senderId = "me",
            text = text,
            type = MessageType.TEXT,
            timestamp = 1_700_000_000_000L,
            isFromMe = true,
            status = MessageStatus.QUEUED
        )
        return pm to msg
    }

    @Test
    fun retryPendingMessages_noPending_returnsSuccess() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"), pendingList = emptyList())
        val result = s.repo.retryPendingMessages("peerA")
        assertTrue(result.isSuccess)
    }

    @Test
    fun retryPendingMessages_textSent_updatesStateToSent() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val (pm, msg) = textPending()
        val s = setup(
            scheduler,
            lan,
            pendingList = listOf(pm),
            messagesById = mapOf(msg.id to msg)
        )

        val result = s.repo.retryPendingMessages("peerA")
        assertTrue(result.isSuccess)
        coVerify { s.messageDao.updateMessageStatus("msg1", MessageStatus.SENT) }
        coVerify { s.pendingDao.deleteById("msg1") }
        assertEquals(1, lan.sentPayloads.size)
        assertEquals(Payload.PayloadType.TEXT, lan.sentPayloads.first().type)
    }

    @Test
    fun retryPendingMessages_pendingRowMissingMessage_removesOrphan() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val (pm, _) = textPending(id = "ghost")
        val s = setup(
            scheduler,
            lan,
            pendingList = listOf(pm),
            messagesById = emptyMap()
        )

        val result = s.repo.retryPendingMessages("peerA")
        assertTrue(result.isFailure) // failureCount > 0
        coVerify { s.pendingDao.deleteById("ghost") }
        assertEquals(0, lan.sentPayloads.size)
    }

    @Test
    fun retrySingleMessage_unknownId_returnsFailure() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val s = setup(scheduler, lan, pendingList = emptyList())

        val result = s.repo.retrySingleMessage("ghost")
        assertTrue(result.isFailure)
    }

    @Test
    fun retrySingleMessage_transportFailure_doesNotDeletePendingRow() = runTest {
        // Documented contract: retrySingleMessage uses cleanupOnGiveUp=false so
        // a failed attempt must keep the queued row (and any staged media)
        // intact for the next Retry tap.
        val scheduler = testScheduler
        val lan = FakeTransport("lan", sendResult = Result.failure(Exception("nope")))
        val (pm, msg) = textPending()
        val s = setup(
            scheduler,
            lan,
            pendingList = listOf(pm),
            messagesById = mapOf(msg.id to msg)
        )

        val result = s.repo.retrySingleMessage("msg1")
        assertTrue(result.isFailure)
        // Pending row should NOT be deleted on a retry-attempt failure.
        coVerify(exactly = 0) { s.pendingDao.deleteById("msg1") }
    }

    @Test
    fun pendingCount_initiallyZero() = runTest {
        val scheduler = testScheduler
        val s = setup(scheduler, FakeTransport("lan"))
        assertEquals(0, s.repo.pendingCount.value)
        assertTrue(s.repo.pendingMessages.value.isEmpty())
    }
}
