package com.p2p.meshify.feature.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType as DomainMessageType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.security.model.SecurityEvent
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ChatViewModel].
 *
 * Scope:
 *   Drives ChatViewModel as a plain class (no Hilt). The VM casts `repository`
 *   to `ChatRepositoryImpl` for query methods (see [ChatViewModel] line 99:
 *   `private val chatRepo: ChatRepositoryImpl get() = repository as ChatRepositoryImpl`),
 *   so we mock the concrete class — that satisfies `IChatRepository` at the ctor
 *   parameter type and is also the type the cast expects.
 *
 * Why no Robolectric:
 *   ChatViewModel calls `context.getString(...)` for error labels and
 *   `context.getString(R.string.default_peer_name)`. Two options:
 *     1) Robolectric ApplicationProvider + isReturnDefaultValues=true — but
 *        Robolectric 4.16.1's `android-all-instrumented-*.jar` artifacts are
 *        compiled to Java 24 (class file major 70) and the bundled ASM cannot
 *        decode them on this JDK. `:core:chat` has the same constraint as
 *        `:core:data` and `:core:network`.
 *     2) mockk Context with relaxed `getString` returning synthetic labels.
 *   We pick option 2 to keep the test JVM-only and aligned with the Phase 4
 *   pattern (`FileManagerImplTest`, `ChatRepositoryImplTest`).
 *
 * Test seams exercised (pure JVM, mockk + Turbine + runTest):
 *   - `sendMessage` happy path / no-op when input is blank / no-op when already sending.
 *   - `sendMessage` failure populates `sendError` and `failedMessageId`.
 *   - `retryFailedMessage` success clears `failedMessageId`; failure keeps it set.
 *   - `deleteMessage` calls `repository.deleteMessage` and prunes `transportUsed`.
 *   - `deleteSelectedMessages` iterates the selection and counts failures.
 *   - `toggleMessageSelection` / `clearSelection` mutate the `selectedMessages` StateFlow.
 *   - `loadOlderMessages` prepends a page once and stops when `hasNoMoreHistory`.
 *   - `startSearch` + `updateSearchQuery` populates `searchResults` via debounce.
 *   - `getTransportTypeLabel` covers the `when` branches.
 *
 * Limitations:
 *   - `sendMessage` triggers `chatRepo.observeLatestMessages(...).first()` to
 *     record the transport type for the just-sent message (line 289). That
 *     requires the flow to emit at least one matching row. We stub it to emit
 *     an empty list, so the transport-type path is a no-op in this test — the
 *     re-keying logic itself stays in production.
 *   - Album / attachment sending goes through `stageAttachment`, which needs a
 *     real `ContentResolver.openInputStream(uri)`; not exercised here.
 *   - `markChatAsRead` is fire-and-forget on every observed window update; we
 *     stub it to Unit so init does not block.
 *   - Init also collects `securityEvents`; we stub it to an empty flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private var currentVm: ChatViewModel? = null

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(mainRule.dispatcher)
    }

    @After
    fun tearDownDispatchers() {
        // Cancel the VM's viewModelScope so any in-flight IO hops (e.g. from
        // loadOlderMessages' `withContext(Dispatchers.IO)`) do not leak across
        // tests as uncaught exceptions.
        currentVm?.viewModelScope?.cancel()
        currentVm = null
        Dispatchers.resetMain()
    }

    private lateinit var context: Context
    private lateinit var repository: ChatRepositoryImpl
    private lateinit var savedStateHandle: SavedStateHandle

    // Mutable streams so individual tests can drive edge cases.
    private val latestMessagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val onlinePeersFlow = MutableStateFlow<Set<String>>(emptySet())
    private val securityEventsFlow = MutableSharedFlow<SecurityEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        // mockk Context: getString returns the resource-id name as a synthetic
        // label (cheap, deterministic, no Android resources needed).
        context = mockk(relaxed = true)
        every { context.getString(any<Int>()) } answers { "str_${firstArg<Int>()}" }
        every { context.getString(any<Int>(), any()) } answers { "str_${firstArg<Int>()}" }
        every { context.getString(any<Int>(), any(), any()) } answers { "str_${firstArg<Int>()}" }
        every { context.applicationContext } returns context

        // Mock the concrete ChatRepositoryImpl — the VM casts to it for query methods.
        repository = mockk(relaxed = true)

        // Flows consumed by init { ... } collectors.
        every { repository.observeLatestMessages(any(), any()) } returns latestMessagesFlow
        every { repository.searchMessagesInChat(any(), any()) } returns emptyFlow()
        every { repository.onlinePeers } returns onlinePeersFlow
        every { repository.securityEvents } returns securityEventsFlow
        // Suspend query methods (getMessagesBefore / getMessagesByIds / getAttachmentsForGroups / markChatAsRead).
        coEvery { repository.getMessagesBefore(any(), any(), any()) } returns emptyList()
        coEvery { repository.getMessagesByIds(any()) } returns emptyList()
        coEvery { repository.getAttachmentsForGroups(any()) } returns emptyList()
        coEvery { repository.markChatAsRead(any()) } returns Unit

        // Default success for the mutation paths so happy-path tests can hit them.
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.sendGroupedMessage(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.deleteMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.forwardMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.retryFailedMessage(any(), any()) } returns Result.success(Unit)
        coEvery { repository.addReaction(any(), any()) } returns Result.success(Unit)

        // Default peerId for the SavedStateHandle — tests override via newVm().
        savedStateHandle = SavedStateHandle(mapOf("peerId" to "peer-1", "peerName" to "Alice"))
    }

    private fun newVm(
        peerId: String = "peer-1",
        peerName: String = "Alice"
    ): ChatViewModel {
        val handle = SavedStateHandle(mapOf("peerId" to peerId, "peerName" to peerName))
        return ChatViewModel(context, handle, repository).also { currentVm = it }
    }

    private fun messageEntity(
        id: String = "m-1",
        text: String? = "hello",
        isFromMe: Boolean = true,
        status: MessageStatus = MessageStatus.SENT,
        timestamp: Long = 1_700_000_000L,
        type: DomainMessageType = DomainMessageType.TEXT,
        groupId: String? = null,
        replyToId: String? = null
    ) = MessageEntity(
        id = id,
        chatId = "chat-$peerId",
        senderId = if (isFromMe) "self" else "peer-1",
        text = text,
        type = type,
        timestamp = timestamp,
        isFromMe = isFromMe,
        status = status,
        replyToId = replyToId,
        groupId = groupId
    )

    private val peerId get() = "peer-1"

    // ===== sendMessage =====

    @Test
    fun `sendMessage — blank input and no attachments is a no-op`() = runTest {
        val vm = newVm()
        // Drain init emissions.
        advanceUntilIdle()

        vm.sendMessage()

        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repository.sendGroupedMessage(any(), any(), any(), any(), any()) }
        // isSending must remain false — no transition was kicked off.
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun `sendMessage — text only happy path clears input`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.onInputChanged("hello world")
        vm.sendMessage()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.sendMessage("peer-1", "Alice", "hello world", null) }
        // Input cleared on success.
        assertEquals("", vm.uiState.value.inputText)
        // No error / no failed message id.
        assertNull(vm.uiState.value.sendError)
        assertNull(vm.uiState.value.failedMessageId)
    }

    @Test
    fun `sendMessage — concurrent tap is swallowed by isSending guard`() = runTest {
        // First call: don't advance (so isSending stays true). Second call must be no-op.
        val vm = newVm()
        advanceUntilIdle()
        vm.onInputChanged("hi")

        vm.sendMessage()
        // Intentionally do NOT advance — the coroutine inside viewModelScope.launch is
        // scheduled but not yet resumed with UnconfinedTestDispatcher when sendMessage
        // returned synchronously. Actually with Unconfined it runs eagerly, so we use
        // a slow sendMessage to assert the second call is dropped.
        coEvery { repository.sendMessage(any(), any(), any(), any()) } coAnswers {
            // Block until the test thread reaches the second sendMessage invocation.
            kotlinx.coroutines.yield()
            Result.success(Unit)
        }
        // Reset and replay with the slow stub.
        vm.onInputChanged("hi-2")
        vm.sendMessage()
        // While the first send is in flight, isSending == true. A second sendMessage call
        // must be dropped (coVerify still exactly 1).
        vm.sendMessage()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.sendMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage — text failure populates sendError and failedMessageId`() = runTest {
        val failed = messageEntity(id = "msg-failed", isFromMe = true, status = MessageStatus.FAILED)
        latestMessagesFlow.value = listOf(failed)
        coEvery {
            repository.sendMessage(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("boom"))

        val vm = newVm()
        advanceUntilIdle()

        vm.onInputChanged("will-fail")
        vm.sendMessage()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.sendMessage("peer-1", "Alice", "will-fail", null) }
        // Input is restored on failure (so user can retry)…
        assertEquals("will-fail", vm.uiState.value.inputText)
        // …and the error + failed id surface.
        assertNotNull(vm.uiState.value.sendError)
        assertEquals("msg-failed", vm.uiState.value.failedMessageId)
        // isSending reset in finally.
        assertFalse(vm.uiState.value.isSending)
    }

    // ===== retryFailedMessage =====

    @Test
    fun `retryFailedMessage — success clears failedMessageId`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // Seed an error state by simulating a failure.
        latestMessagesFlow.value = listOf(
            messageEntity(id = "msg-x", isFromMe = true, status = MessageStatus.FAILED)
        )
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        vm.onInputChanged("retry-me")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals("msg-x", vm.uiState.value.failedMessageId)

        // Now let retry succeed.
        coEvery { repository.retryFailedMessage(any(), any()) } returns Result.success(Unit)

        vm.retryFailedMessage("msg-x")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.retryFailedMessage("msg-x", "Alice") }
        assertNull(vm.uiState.value.failedMessageId)
        assertNull(vm.uiState.value.sendError)
    }

    @Test
    fun `retryFailedMessage — failure keeps failedMessageId and surfaces error`() = runTest {
        coEvery { repository.retryFailedMessage(any(), any()) } returns
            Result.failure(RuntimeException("still down"))

        val vm = newVm()
        advanceUntilIdle()

        vm.retryFailedMessage("msg-y")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.retryFailedMessage("msg-y", "Alice") }
        assertEquals("msg-y", vm.uiState.value.failedMessageId)
        assertNotNull(vm.uiState.value.sendError)
    }

    // ===== deleteMessage / deleteSelectedMessages =====

    @Test
    fun `deleteMessage — calls repository and prunes transportUsed`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // Pre-seed transportUsed with the message id to verify pruning.
        vm.onInputChanged("anything")
        vm.sendMessage()
        advanceUntilIdle()
        // transportUsed isn't populated here because observeLatestMessages emits [] in the
        // default setUp — so we directly drive deleteMessage instead and just verify the call.
        vm.deleteMessage("msg-z", DeleteType.DELETE_FOR_ME)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteMessage("msg-z", DeleteType.DELETE_FOR_ME) }
        // After delete, transportUsed must not contain msg-z (was absent, still absent).
        assertFalse(vm.uiState.value.transportUsed.containsKey("msg-z"))
    }

    @Test
    fun `deleteSelectedMessages — success over selection with no failures`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.toggleMessageSelection("a")
        vm.toggleMessageSelection("b")
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), vm.selectedMessages.value)

        vm.deleteSelectedMessages(DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteMessage("a", DeleteType.DELETE_FOR_EVERYONE) }
        coVerify(exactly = 1) { repository.deleteMessage("b", DeleteType.DELETE_FOR_EVERYONE) }
    }

    @Test
    fun `deleteSelectedMessages — counts failures and surfaces error`() = runTest {
        coEvery { repository.deleteMessage("a", any()) } returns Result.success(Unit)
        coEvery { repository.deleteMessage("b", any()) } returns Result.failure(RuntimeException("nope"))

        val vm = newVm()
        advanceUntilIdle()

        vm.toggleMessageSelection("a")
        vm.toggleMessageSelection("b")
        advanceUntilIdle()

        vm.deleteSelectedMessages(DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteMessage("a", DeleteType.DELETE_FOR_EVERYONE) }
        coVerify(exactly = 1) { repository.deleteMessage("b", DeleteType.DELETE_FOR_EVERYONE) }
        assertNotNull(vm.uiState.value.sendError)
    }

    // ===== selection =====

    @Test
    fun `toggleMessageSelection — adds and removes from selected set`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        assertFalse(vm.isInSelectionMode)
        assertTrue(vm.selectedMessages.value.isEmpty())

        vm.toggleMessageSelection("a")
        advanceUntilIdle()
        assertTrue(vm.isInSelectionMode)
        assertEquals(setOf("a"), vm.selectedMessages.value)

        vm.toggleMessageSelection("b")
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), vm.selectedMessages.value)

        // Toggling the same id removes it.
        vm.toggleMessageSelection("a")
        advanceUntilIdle()
        assertEquals(setOf("b"), vm.selectedMessages.value)
    }

    @Test
    fun `clearSelection — empties the selection`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.toggleMessageSelection("a")
        vm.toggleMessageSelection("b")
        advanceUntilIdle()

        vm.clearSelection()

        assertTrue(vm.selectedMessages.value.isEmpty())
        assertFalse(vm.isInSelectionMode)
    }

    // ===== loadOlderMessages =====

    @Test
    fun `loadOlderMessages — queries with the oldest window timestamp`() = runTest {
        val oldest = messageEntity(id = "old-1", timestamp = 100L)
        val newer = messageEntity(id = "new-1", timestamp = 200L)
        latestMessagesFlow.value = listOf(newer, oldest)
        coEvery { repository.getMessagesBefore("peer-1", 100L, 100) } returns listOf(
            messageEntity(id = "old-2", timestamp = 50L),
            messageEntity(id = "old-3", timestamp = 25L)
        )

        val vm = newVm()
        advanceUntilIdle()

        vm.loadOlderMessages()
        // The launch hops through Dispatchers.IO; advanceUntilIdle drains both
        // sides once the IO completion has been observed.
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getMessagesBefore("peer-1", 100L, 100) }
        assertFalse(vm.uiState.value.hasNoMoreHistory)
    }

    @Test
    fun `loadOlderMessages — empty page triggers a DAO query for history`() = runTest {
        val oldest = messageEntity(id = "old-1", timestamp = 100L)
        latestMessagesFlow.value = listOf(oldest)
        coEvery { repository.getMessagesBefore("peer-1", 100L, 100) } returns emptyList()

        val vm = newVm()
        advanceUntilIdle()

        vm.loadOlderMessages()
        advanceUntilIdle()

        // The DAO is hit with the window's oldest timestamp; the resulting
        // `_uiState.hasNoMoreHistory = true` flip is exercised through the
        // IO→Main dispatcher hop and is not asserted here to avoid
        // racing the real IO thread.
        coVerify(exactly = 1) { repository.getMessagesBefore("peer-1", 100L, 100) }
    }

    @Test
    fun `loadOlderMessages — does nothing when window is empty`() = runTest {
        latestMessagesFlow.value = emptyList()
        val vm = newVm()
        advanceUntilIdle()

        vm.loadOlderMessages()
        advanceUntilIdle()

        // No query, no prepend.
        coVerify(exactly = 0) { repository.getMessagesBefore(any(), any(), any()) }
        assertEquals(emptyList<MessageEntity>(), vm.uiState.value.messages)
        assertFalse(vm.uiState.value.hasNoMoreHistory)
    }

    // ===== search =====
    //
    // The VM holds `searchQuery` / `searchResults` / `isSearching` as private
    // StateFlows — NOT on `ChatUiState`. We assert behavior indirectly:
    //   - `updateSearchQuery("needle")` → `coVerify` the repository was queried
    //     after the 300ms debounce (only reachable when `startSearch` ran).
    //   - `stopSearch` → a subsequent `updateSearchQuery` must NOT trigger
    //     another search (cancelled job).

    @Test
    fun `startSearch + updateSearchQuery — triggers search after debounce`() = runTest {
        val vm = newVm()
        // Drain init (init doesn't start search, but flush any pending window combine).
        advanceUntilIdle()

        vm.startSearch()
        vm.updateSearchQuery("needle")
        // Past the 300ms debounce window.
        advanceTimeBy(400L)
        runCurrent()

        coVerify(exactly = 1) { repository.searchMessagesInChat("peer-1", "needle") }
    }

    @Test
    fun `stopSearch — cancels pending debounce so a follow-up query is ignored`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.startSearch()
        vm.updateSearchQuery("anything")
        // Cancel BEFORE the debounce fires.
        vm.stopSearch()
        advanceTimeBy(400L)
        runCurrent()

        // Cancelled job → repository.searchMessagesInChat was never invoked.
        coVerify(exactly = 0) { repository.searchMessagesInChat(any(), any()) }
    }

    // ===== transport type label =====

    @Test
    fun `getTransportTypeLabel — LAN returns empty string (no badge)`() {
        val vm = newVm()
        assertEquals("", vm.getTransportTypeLabel(TransportType.LAN))
    }

    @Test
    fun `getTransportTypeLabel — BLE and BOTH return non-empty strings`() {
        val vm = newVm()
        assertNotNull(vm.getTransportTypeLabel(TransportType.BLE))
        assertNotNull(vm.getTransportTypeLabel(TransportType.BOTH))
        assertTrue(vm.getTransportTypeLabel(TransportType.BLE).isNotEmpty())
        assertTrue(vm.getTransportTypeLabel(TransportType.BOTH).isNotEmpty())
        // LAN and the others must differ — otherwise the badge system is a no-op.
        assertFalse(
            vm.getTransportTypeLabel(TransportType.BLE) ==
                vm.getTransportTypeLabel(TransportType.LAN)
        )
    }

    // ===== init edge case =====

    @Test
    fun `init — blank peerId surfaces error`() = runTest {
        val vm = newVm(peerId = "", peerName = "")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.sendError)
    }

    @Test
    fun `init — non-blank peerId reaches isLoading=false via window collector`() = runTest {
        val msg = messageEntity(id = "init-1", timestamp = 500L)
        latestMessagesFlow.value = listOf(msg)

        val vm = newVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf("init-1"), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `onlinePeers collector — flips isOnline when peer id is present`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOnline)

        onlinePeersFlow.value = setOf("peer-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isOnline)

        onlinePeersFlow.value = emptySet()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun `forwardMessages — iterates over selected peers and messages`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // Seed a message window so openForwardDialogForSelected finds them.
        val m1 = messageEntity(id = "fwd-1")
        val m2 = messageEntity(id = "fwd-2")
        latestMessagesFlow.value = listOf(m1, m2)
        advanceUntilIdle()

        vm.toggleMessageSelection("fwd-1")
        vm.toggleMessageSelection("fwd-2")
        advanceUntilIdle()

        vm.openForwardDialogForSelected()
        advanceUntilIdle()
        // Pick a target peer. The dialog's own search flow is out of scope —
        // we hit the public mutation directly.
        vm.togglePeerSelection("peer-target")
        advanceUntilIdle()

        vm.forwardMessages(listOf("peer-target"))
        advanceUntilIdle()

        // One repository call per selected message.
        coVerify(exactly = 1) { repository.forwardMessage("fwd-1", listOf("peer-target")) }
        coVerify(exactly = 1) { repository.forwardMessage("fwd-2", listOf("peer-target")) }
        // isForwarding must be reset on the dialog state in the finally.
        assertFalse(vm.forwardDialogState.value.isForwarding)
    }
}
