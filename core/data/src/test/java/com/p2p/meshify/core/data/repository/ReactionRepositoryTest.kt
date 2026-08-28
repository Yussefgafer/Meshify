package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReactionRepositoryTest — Mocks the MessageDao, ISettingsRepository, and
 * TransportManager. Limitations: this verifies ReactionRepository's own wiring
 * (payload construction, transport selection, DB update), not the transport's
 * actual wire-format or peer reachability — on-device behavior is verified
 * via feature:real-device-testing on real hardware.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionRepositoryTest {

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

    private fun makeRepo(
        scheduler: TestCoroutineScheduler,
        message: MessageEntity?,
        transport: FakeTransport?
    ): Triple<ReactionRepository, MessageDao, TransportManager> {
        val messageDao = mockk<MessageDao>(relaxed = true)
        coEvery { messageDao.getMessageById(any()) } returns message
        coEvery { messageDao.updateReaction(any(), any()) } returns Unit

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
        }

        val repo = ReactionRepository(messageDao, manager, settings)
        return Triple(repo, messageDao, manager)
    }

    private fun textMessage(chatId: String = "peerA"): MessageEntity = MessageEntity(
        id = "msg1",
        chatId = chatId,
        senderId = "other",
        text = "hello",
        type = MessageType.TEXT,
        timestamp = 1_700_000_000_000L,
        isFromMe = false,
        status = MessageStatus.RECEIVED
    )

    @Test
    fun addReaction_persistsAndSendsReactionPayload() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val (repo, messageDao, _) = makeRepo(scheduler, textMessage(), lan)
        val payloadSlot = slot<Payload>()

        val result = repo.addReaction("msg1", "\uD83D\uDE00")
        assertTrue(result.isSuccess)
        coVerify { messageDao.updateReaction("msg1", "\uD83D\uDE00") }
        assertEquals(1, lan.sentPayloads.size)
        val (recipient, payload) = lan.sentPayloads.first()
        assertEquals("peerA", recipient)
        assertEquals(Payload.PayloadType.REACTION, payload.type)
        val json = String(payload.data)
        assertTrue("payload carries reaction emoji", json.contains("\uD83D\uDE00"))
        assertTrue("payload carries messageId", json.contains("msg1"))
    }

    @Test
    fun addReaction_clearByNull_reactionUpdateIsNull() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val (repo, _, _) = makeRepo(scheduler, textMessage(), lan)

        val result = repo.addReaction("msg1", null)
        assertTrue(result.isSuccess)
        assertEquals(1, lan.sentPayloads.size)
        val (_, payload) = lan.sentPayloads.first()
        val json = String(payload.data)
        assertTrue("null reaction encoded", json.contains("\"reaction\":null"))
    }

    @Test
    fun addReaction_unknownMessage_returnsFailure() = runTest {
        val scheduler = testScheduler
        val lan = FakeTransport("lan")
        val (repo, _, _) = makeRepo(scheduler, message = null, transport = lan)

        val result = repo.addReaction("ghost", "\uD83D\uDC4D")
        assertTrue(result.isFailure)
        assertEquals(0, lan.sentPayloads.size)
    }

    @Test
    fun addReaction_noTransport_returnsFailure() = runTest {
        val scheduler = testScheduler
        val (repo, _, _) = makeRepo(scheduler, textMessage(), transport = null)

        val result = repo.addReaction("msg1", "\uD83D\uDC4D")
        assertTrue(result.isFailure)
    }

    @Test
    fun addReaction_transportFailure_silentlySucceeds() = runTest {
        // Documenting current behavior: ReactionRepository.addReaction does
        // not check the sendPayload Result — it returns success even when the
        // transport rejects the payload (the local DB update has already been
        // applied by then). This is asymmetric with MessageRepository, which
        // DOES propagate transport failures. A future bug-fix should make
        // these consistent; until then this test pins the current contract.
        val scheduler = testScheduler
        val lan = FakeTransport("lan", sendResult = Result.failure(Exception("nope")))
        val (repo, _, _) = makeRepo(scheduler, textMessage(), lan)

        val result = repo.addReaction("msg1", "\uD83D\uDC4D")
        assertTrue("addReaction currently swallows transport failure", result.isSuccess)
        assertEquals(1, lan.sentPayloads.size)
    }
}
