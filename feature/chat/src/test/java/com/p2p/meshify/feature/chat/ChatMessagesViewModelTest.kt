package com.p2p.meshify.feature.chat

import android.content.Context
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
        every { mockContext.getString(R.string.error_message_send_failed, any<String>()) } returns "Send failed"
        every { mockContext.getString(R.string.chat_transport_ble_desc) } returns "BLE"
        every { mockContext.getString(R.string.chat_transport_multipath_desc) } returns "Multipath"

        coEvery { mockRepository.getMessages(any()) } returns messagesFlow
        every { mockRepository.onlinePeers } returns onlinePeersFlow
        every { mockRepository.securityEvents } returns securityEventsFlow

        viewModel = ChatMessagesViewModel(mockContext, mockRepository, testPeerId)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state should have loading true and empty messages`() = runTest {
        val state = viewModel.uiState.value

        assertTrue(state.isLoading)
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isOnline)
        assertFalse(state.hasMoreMessages)
        assertNull(state.sendError)
    }

    // ==================== Message Loading Tests ====================

    @Test
    fun `receiving messages from repository should update state with messages and stop loading`() = runTest {
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
        messagesFlow.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `messages flow should trigger multiple state updates`() = runTest {
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
        onlinePeersFlow.value = emptySet()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOnline)

        onlinePeersFlow.value = setOf(testPeerId, "other-peer")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnline)
    }

    @Test
    fun `should mark peer as offline when peer disappears from onlinePeers flow`() = runTest {
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
        val newMessages = listOf(testMessage(id = "older-1"))
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(newMessages)

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 0) }
    }

    @Test
    fun `loadMoreMessages should set isLoadingMore to true while loading`() = runTest {
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns flowOf(emptyList())

        viewModel.loadMoreMessages()

        val state = viewModel.uiState.value
        assertTrue(state.isLoadingMore)
    }

    @Test
    fun `loadMoreMessages should not load when already loading`() = runTest {
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns flowOf(emptyList())

        viewModel.loadMoreMessages()
        viewModel.loadMoreMessages()
        viewModel.loadMoreMessages()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.getMessagesPaged(any(), any(), any()) }
    }

    @Test
    fun `loadMoreMessages should prepend new messages to existing list`() = runTest {
        val initialMessages = listOf(testMessage(id = "msg-1"))
        messagesFlow.value = initialMessages
        advanceUntilIdle()

        val olderMessages = listOf(testMessage(id = "older-1"))
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(olderMessages)

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.id == "older-1" })
        assertTrue(state.messages.any { it.id == "msg-1" })
    }

    @Test
    fun `loadMoreMessages should set hasMoreMessages false when no more messages`() = runTest {
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

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasMoreMessages)
    }

    @Test
    fun `loadMoreMessages should increment page after successful load`() = runTest {
        val page1 = listOf(testMessage(id = "older-1"))
        val page2 = listOf(testMessage(id = "older-2"))
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 0) } returns flowOf(page1)
        coEvery { mockRepository.getMessagesPaged(testPeerId, 50, 50) } returns flowOf(page2)

        viewModel.loadMoreMessages()
        advanceUntilIdle()
        viewModel.loadMoreMessages()
        advanceUntilIdle()

        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 0) }
        coVerify { mockRepository.getMessagesPaged(testPeerId, 50, 50) }
    }

    // ==================== Security Events Tests ====================

    @Test
    fun `MessageSendFailed security event should set sendError in state`() = runTest {
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
        viewModel.clearUploadError()

        assertNull(viewModel.uiState.value.uploadError)
    }

    // ==================== deleteMessage Tests ====================

    @Test
    fun `deleteMessage should delegate to repository with correct deleteType`() = runTest {
        val messageId = "msg-to-delete"

        viewModel.deleteMessage(messageId, DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        coVerify { mockRepository.deleteMessage(messageId, DeleteType.DELETE_FOR_EVERYONE) }
    }

    @Test
    fun `deleteMessage should use DELETE_FOR_ME when specified`() = runTest {
        val messageId = "msg-to-delete"

        viewModel.deleteMessage(messageId, DeleteType.DELETE_FOR_ME)
        advanceUntilIdle()

        coVerify { mockRepository.deleteMessage(messageId, DeleteType.DELETE_FOR_ME) }
    }

    // ==================== addReaction Tests ====================

    @Test
    fun `addReaction should delegate to repository with reaction`() = runTest {
        val messageId = "msg-1"

        viewModel.addReaction(messageId, "👍")
        advanceUntilIdle()

        coVerify { mockRepository.addReaction(messageId, "👍") }
    }

    @Test
    fun `addReaction with null should delegate to repository to remove reaction`() = runTest {
        val messageId = "msg-1"

        viewModel.addReaction(messageId, null)
        advanceUntilIdle()

        coVerify { mockRepository.addReaction(messageId, null) }
    }

    // ==================== Transport Type Tests ====================

    @Test
    fun `getTransportTypeLabel should return BLE label for BLE transport`() {
        val label = viewModel.getTransportTypeLabel(TransportType.BLE)
        assertEquals("BLE", label)
    }

    @Test
    fun `getTransportTypeLabel should return Multipath label for BOTH transport`() {
        val label = viewModel.getTransportTypeLabel(TransportType.BOTH)
        assertEquals("Multipath", label)
    }

    @Test
    fun `getTransportTypeLabel should return empty string for LAN transport`() {
        val label = viewModel.getTransportTypeLabel(TransportType.LAN)
        assertEquals("", label)
    }
}
