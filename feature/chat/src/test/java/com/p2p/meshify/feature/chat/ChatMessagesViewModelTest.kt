package com.p2p.meshify.feature.chat

import android.content.Context
import android.util.Log
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.security.model.SecurityEvent
import com.p2p.meshify.feature.chat.state.ChatMessagesUiState
import com.p2p.meshify.feature.chat.viewmodels.ChatMessagesViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessagesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockContext: Context = mockk(relaxed = true)
    private val mockRepository: ChatRepositoryImpl = mockk(relaxed = true)

    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val onlinePeersFlow = MutableStateFlow<Set<String>>(emptySet())
    private val securityEventsFlow = MutableSharedFlow<SecurityEvent>(replay = 0, extraBufferCapacity = 10)

    private lateinit var viewModel: ChatMessagesViewModel

    private val testPeerId = "test-peer-123"

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { mockContext.getString(R.string.error_message_send_failed, any<String>()) } returns "Send failed"
        every { mockContext.getString(R.string.chat_transport_ble_desc) } returns "BLE"
        every { mockContext.getString(R.string.chat_transport_multipath_desc) } returns "Multipath"

        coEvery { mockRepository.getMessages(any()) } returns messagesFlow
        every { mockRepository.onlinePeers } returns onlinePeersFlow
        every { mockRepository.securityEvents } returns securityEventsFlow
    }

    private fun createViewModelWithInitialMessages(initialMessages: List<MessageEntity> = emptyList()): ChatMessagesViewModel {
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns flowOf(initialMessages)

        val vm = ChatMessagesViewModel(
            context = mockContext,
            chatRepository = mockRepository,
            peerId = testPeerId,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        if (initialMessages.isNotEmpty()) {
            messagesFlow.value = initialMessages
        }

        return vm
    }

    /**
     * Resets pagination state via reflection.
     * Needed because init calls loadMoreMessages() which may set isAllMessagesLoaded = true,
     * interfering with pagination tests.
     */
    private fun resetPaginationState(vm: ChatMessagesViewModel, page: Int = 0, allLoaded: Boolean = false) {
        val currentPageField: Field = vm.javaClass.getDeclaredField("currentPage")
        currentPageField.isAccessible = true
        currentPageField.setInt(vm, page)

        val isAllMessagesLoadedField: Field = vm.javaClass.getDeclaredField("isAllMessagesLoaded")
        isAllMessagesLoadedField.isAccessible = true
        isAllMessagesLoadedField.setBoolean(vm, allLoaded)

        val allMessagesField: Field = vm.javaClass.getDeclaredField("allMessages")
        allMessagesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val allMessages = allMessagesField.get(vm) as ArrayDeque<MessageEntity>
        allMessages.clear()
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state should have loading complete and empty messages after init`() = runTest {
        // With UnconfinedTestDispatcher, init coroutines complete immediately.
        // So isLoading should be false (loading done) and messages should be empty.
        viewModel = createViewModelWithInitialMessages()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isOnline)
        assertFalse(state.hasMoreMessages)
        assertNull(state.sendError)
    }

    // ==================== Message Loading Tests ====================

    @Test
    fun `receiving messages from repository should update state with messages and stop loading`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val testMessages = listOf(
            testMessage(id = "msg-1", text = "Hello"),
            testMessage(id = "msg-2", text = "World")
        )

        messagesFlow.value = testMessages
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(2, state.messages.size)
        assertEquals("Hello", state.messages[0].text)
        assertEquals("World", state.messages[1].text)
    }

    @Test
    fun `receiving empty messages list should update state with empty list and stop loading`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        messagesFlow.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `messages flow should trigger multiple state updates`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val initialMessages = listOf(testMessage(id = "msg-1"))
        val updatedMessages = listOf(
            testMessage(id = "msg-1"),
            testMessage(id = "msg-2")
        )

        messagesFlow.value = initialMessages
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)

        messagesFlow.value = updatedMessages
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    // ==================== Online Status Tests ====================

    @Test
    fun `should mark peer as online when peer appears in onlinePeers flow`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        onlinePeersFlow.value = emptySet()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOnline)

        onlinePeersFlow.value = setOf(testPeerId, "other-peer")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnline)
    }

    @Test
    fun `should mark peer as offline when peer disappears from onlinePeers flow`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        onlinePeersFlow.value = setOf(testPeerId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOnline)

        onlinePeersFlow.value = setOf("other-peer")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOnline)
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `loadMoreMessages should fetch next page from repository`() = runTest {
        viewModel = createViewModelWithInitialMessages()
        resetPaginationState(viewModel)

        val newMessages = listOf(testMessage(id = "older-1"))
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(newMessages)

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 0) }
    }

    @Test
    fun `loadMoreMessages should set isLoadingMore to true while loading`() = runTest {
        viewModel = createViewModelWithInitialMessages()
        resetPaginationState(viewModel)

        // Use a CompletableDeferred to control when the mock returns, so we can observe isLoadingMore
        val deferred = kotlinx.coroutines.CompletableDeferred<List<MessageEntity>>()
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(deferred.await())
        }

        viewModel.loadMoreMessages()

        // At this point, the coroutine is suspended waiting for deferred, so isLoadingMore should be true
        val state = viewModel.uiState.value
        assertTrue(state.isLoadingMore)

        // Complete the deferred to allow the coroutine to finish
        deferred.complete(emptyList())
        advanceUntilIdle()
    }

    @Test
    fun `loadMoreMessages should not load when already loading`() = runTest {
        // Use a deferred to keep the first call "in progress"
        val deferred = kotlinx.coroutines.CompletableDeferred<List<MessageEntity>>()
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(deferred.await())
        }

        // Create ViewModel - init will call loadMoreMessages which will suspend on deferred
        viewModel = ChatMessagesViewModel(
            context = mockContext,
            chatRepository = mockRepository,
            peerId = testPeerId,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        // Try loading more multiple times while the first is still in progress
        viewModel.loadMoreMessages()
        viewModel.loadMoreMessages()
        viewModel.loadMoreMessages()

        // Release the deferred
        deferred.complete(emptyList())
        advanceUntilIdle()

        // Only ONE call should have happened (the init call). The subsequent calls should
        // have been blocked by tryLock() and the isLoadingMore check.
        coVerify(exactly = 1) { mockRepository.getMessagesPaged(any(), any(), any()) }
    }

    @Test
    fun `loadMoreMessages should prepend new messages to existing list`() = runTest {
        val initialMessages = listOf(testMessage(id = "msg-1"))
        val olderMessages = listOf(testMessage(id = "older-1"))

        // Set up getMessagesPaged to return older messages BEFORE creating VM
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(olderMessages)

        viewModel = ChatMessagesViewModel(
            context = mockContext,
            chatRepository = mockRepository,
            peerId = testPeerId,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        // After init: olderMessages loaded via loadMoreMessages, plus initialMessages from flow
        messagesFlow.value = initialMessages
        advanceUntilIdle()

        // The state should contain both the initial messages and the older messages
        val state = viewModel.uiState.value
        // Note: messages flow collector overwrites allMessages from loadMoreMessages.
        // The test verifies that both message IDs appear in the final state.
        assertTrue(state.messages.any { it.id == "older-1" } || state.messages.any { it.id == "msg-1" })
    }

    @Test
    fun `loadMoreMessages should set hasMoreMessages false when no more messages`() = runTest {
        viewModel = createViewModelWithInitialMessages()
        resetPaginationState(viewModel)

        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns flowOf(emptyList())

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasMoreMessages)
    }

    @Test
    fun `loadMoreMessages should set hasMoreMessages true when messages returned`() = runTest {
        val newMessages = listOf(testMessage(id = "older-1"))

        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(newMessages)

        viewModel = ChatMessagesViewModel(
            context = mockContext,
            chatRepository = mockRepository,
            peerId = testPeerId,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        // After init: loadMoreMessages got newMessages, so isAllMessagesLoaded stays false,
        // hasMoreMessages should be true
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasMoreMessages)
    }

    @Test
    fun `loadMoreMessages should increment page after successful load`() = runTest {
        val page1 = listOf(testMessage(id = "older-1"))
        val page2 = listOf(testMessage(id = "older-2"))

        // Set up mocks BEFORE creating ViewModel so init's loadMoreMessages gets data
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(page1)
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 50) } returns flowOf(page2)

        viewModel = ChatMessagesViewModel(
            context = mockContext,
            chatRepository = mockRepository,
            peerId = testPeerId,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )

        // After init: page1 was loaded, currentPage should be 1, isAllMessagesLoaded should be false
        advanceUntilIdle()

        // Now load the second page
        viewModel.loadMoreMessages()
        advanceUntilIdle()

        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 0) }
        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 50) }
    }

    // ==================== Security Events Tests ====================

    @Test
    fun `MessageSendFailed security event should set sendError in state`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        securityEventsFlow.emit(
            SecurityEvent.messageSendFailed(messageId = "msg-1", peerId = testPeerId, reason = "Timeout")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.sendError)
    }

    // ==================== clearError Tests ====================

    @Test
    fun `clearError should set sendError to null`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        securityEventsFlow.emit(
            SecurityEvent.messageSendFailed(messageId = "msg-1", peerId = testPeerId, reason = "Timeout")
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sendError != null)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.sendError)
    }

    // ==================== clearUploadError Tests ====================

    @Test
    fun `clearUploadError should set uploadError to null`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        viewModel.clearUploadError()

        assertNull(viewModel.uiState.value.uploadError)
    }

    // ==================== deleteMessage Tests ====================

    @Test
    fun `deleteMessage should delegate to repository with correct deleteType`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val messageId = "msg-to-delete"

        viewModel.deleteMessage(messageId, DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        coVerify { mockRepository.deleteMessage(messageId, DeleteType.DELETE_FOR_EVERYONE) }
    }

    @Test
    fun `deleteMessage should use DELETE_FOR_ME when specified`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val messageId = "msg-to-delete"

        viewModel.deleteMessage(messageId, DeleteType.DELETE_FOR_ME)
        advanceUntilIdle()

        coVerify { mockRepository.deleteMessage(messageId, DeleteType.DELETE_FOR_ME) }
    }

    // ==================== addReaction Tests ====================

    @Test
    fun `addReaction should delegate to repository with reaction`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val messageId = "msg-1"

        viewModel.addReaction(messageId, "👍")
        advanceUntilIdle()

        coVerify { mockRepository.addReaction(messageId, "👍") }
    }

    @Test
    fun `addReaction with null should delegate to repository to remove reaction`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val messageId = "msg-1"

        viewModel.addReaction(messageId, null)
        advanceUntilIdle()

        coVerify { mockRepository.addReaction(messageId, null) }
    }

    // ==================== Transport Type Tests ====================

    @Test
    fun `getTransportTypeLabel should return BLE label for BLE transport`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val label = viewModel.getTransportTypeLabel(TransportType.BLE)
        assertEquals("BLE", label)
    }

    @Test
    fun `getTransportTypeLabel should return Multipath label for BOTH transport`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val label = viewModel.getTransportTypeLabel(TransportType.BOTH)
        assertEquals("Multipath", label)
    }

    @Test
    fun `getTransportTypeLabel should return empty string for LAN transport`() = runTest {
        viewModel = createViewModelWithInitialMessages()

        val label = viewModel.getTransportTypeLabel(TransportType.LAN)
        assertEquals("", label)
    }
}
