package com.p2p.meshify.feature.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.domain.model.MessageType as DomainMessageType
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Focused send-path tests for [ChatViewModel]:
 * - `sendMessage` concurrent guard when `isSending=true`
 * - `stageAttachment` 10-file limit and oversized file rejection
 * - `transportUsed` cap at 100 entries
 *
 * `stageAttachment` reads bytes via `context.contentResolver.openInputStream`,
 * so a relaxed [ContentResolver] mock is installed in setUp — the same
 * JVM-only pattern used by `ChatViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSendPathTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private var currentVm: ChatViewModel? = null

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(mainRule.dispatcher)
    }

    @After
    fun tearDownDispatchers() {
        currentVm?.viewModelScope?.cancel()
        currentVm = null
        Dispatchers.resetMain()
    }

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: ChatRepositoryImpl

    private val latestMessagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val onlinePeersFlow = MutableStateFlow<Set<String>>(emptySet())
    private val securityEventsFlow = MutableSharedFlow<com.p2p.meshify.domain.security.model.SecurityEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(any<Int>()) } answers { "str_${firstArg<Int>()}" }
        every { context.getString(any<Int>(), any()) } answers { "str_${firstArg<Int>()}" }
        every { context.getString(any<Int>(), any(), any()) } answers { "str_${firstArg<Int>()}" }
        every { context.applicationContext } returns context

        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver

        repository = mockk(relaxed = true)

        every { repository.observeLatestMessages(any(), any()) } returns latestMessagesFlow
        every { repository.searchMessagesInChat(any(), any()) } returns emptyFlow()
        every { repository.onlinePeers } returns onlinePeersFlow
        every { repository.securityEvents } returns securityEventsFlow
        coEvery { repository.getMessagesBefore(any(), any(), any()) } returns emptyList()
        coEvery { repository.getMessagesByIds(any()) } returns emptyList()
        coEvery { repository.getAttachmentsForGroups(any()) } returns emptyList()
        coEvery { repository.markChatAsRead(any()) } returns Unit

        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.sendGroupedMessage(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.deleteMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.forwardMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.retryFailedMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.addReaction(any(), any()) } returns Result.success(Unit)
    }

    private fun newVm(
        peerId: String = "peer-1",
        peerName: String = "Alice"
    ): ChatViewModel {
        val handle = SavedStateHandle(mapOf("peerId" to peerId, "peerName" to peerName))
        return ChatViewModel(context, handle, repository).also { currentVm = it }
    }

    private val peerId get() = "peer-1"

    private fun messageEntity(
        id: String = "m-1",
        status: MessageStatus = MessageStatus.SENT
    ) = MessageEntity(
        id = id,
        chatId = "chat-$peerId",
        senderId = "self",
        text = "hello",
        type = DomainMessageType.TEXT,
        timestamp = 1_700_000_000L,
        isFromMe = true,
        status = status
    )

    // ===== sendMessage guard =====

    @Test
    fun `sendMessage — concurrent tap while isSending=true is swallowed`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        var firstCallEntered = false
        coEvery { repository.sendMessage(any(), any(), any(), any()) } coAnswers {
            if (!firstCallEntered) {
                firstCallEntered = true
                kotlinx.coroutines.yield()
            }
            Result.success(Unit)
        }

        vm.onInputChanged("hi")
        vm.sendMessage()
        vm.sendMessage() // second tap while isSending still true — must be dropped
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.sendMessage(any(), any(), any(), any()) }
        assertFalse(vm.uiState.value.isSending)
    }

    // ===== stageAttachment =====

    /**
     * stageAttachment reads bytes via `withContext(Dispatchers.IO)`, which in a
     * JVM test is the REAL IO thread pool — not the test scheduler. So the
     * launch-and-advance rhythm alone races the assertion. This helper polls
     * (real time, bounded) until the staging coroutine has landed back on the
     * test scheduler (`isStagingAttachment` is cleared at its very end), then
     * drains one more advance so any append done before that flag flip is
     * re-applied before the caller asserts.
     */
    private fun awaitStagingSettled(vm: ChatViewModel, scheduler: TestCoroutineScheduler, timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            scheduler.advanceUntilIdle()
            if (!vm.uiState.value.isStagingAttachment) {
                scheduler.advanceUntilIdle()
                return
            }
            Thread.sleep(20)
        }
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `stageAttachment — accepts attachment under the 10-file limit`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        every { contentResolver.openInputStream(any()) } answers { ByteArray(8).inputStream() }

        repeat(9) { index ->
            val mockUri = mockk<Uri>("uri_$index")
            vm.stageAttachment(mockUri, DomainMessageType.FILE)
            awaitStagingSettled(vm, testScheduler)
        }

        assertEquals(9, vm.uiState.value.stagedAttachments.size)
    }

    @Test
    fun `stageAttachment — 11th attachment is dropped at the 10-file limit`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        every { contentResolver.openInputStream(any()) } answers { ByteArray(8).inputStream() }

        repeat(10) { index ->
            val mockUri = mockk<Uri>("uri_$index")
            vm.stageAttachment(mockUri, DomainMessageType.FILE)
            awaitStagingSettled(vm, testScheduler)
        }
        // 11th must be ignored.
        val extraUri = mockk<Uri>("uri_extra")
        vm.stageAttachment(extraUri, DomainMessageType.FILE)
        awaitStagingSettled(vm, testScheduler)

        assertEquals(10, vm.uiState.value.stagedAttachments.size)
    }

    @Test
    fun `stageAttachment — oversized file is rejected`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // 11 MB > ~10 MB limit (MAX_FILE_SIZE_BYTES).
        every { contentResolver.openInputStream(any()) } answers { ByteArray(11 * 1024 * 1024).inputStream() }

        val mockUri = mockk<Uri>()
        vm.stageAttachment(mockUri, DomainMessageType.FILE)
        awaitStagingSettled(vm, testScheduler)

        assertFalse(vm.uiState.value.isStagingAttachment)
        assertTrue(vm.uiState.value.stagedAttachments.isEmpty())
    }

    @Test
    fun `stageAttachment — null stream is a no-op`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        every { contentResolver.openInputStream(any()) } answers { null }

        val mockUri = mockk<Uri>()
        vm.stageAttachment(mockUri, DomainMessageType.IMAGE)
        awaitStagingSettled(vm, testScheduler)

        assertFalse(vm.uiState.value.isStagingAttachment)
        assertTrue(vm.uiState.value.stagedAttachments.isEmpty())
    }

    // ===== transportUsed cap =====

    @Test
    fun `transportUsed — caps at 100 entries after 101 distinct sends`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // Each send records the newest from-me message in the observed window,
        // so feed a fresh message id per send to grow the map past its cap.
        for (i in 1..101) {
            latestMessagesFlow.value = listOf(messageEntity(id = "m-$i"))
            vm.onInputChanged("tick-$i")
            vm.sendMessage()
            advanceUntilIdle()
        }

        assertEquals(100, vm.uiState.value.transportUsed.size)
        // Oldest entry dropped.
        assertNull(vm.uiState.value.transportUsed["m-1"])
        // Newest entry retained.
        assertNotNull(vm.uiState.value.transportUsed["m-101"])
    }
}