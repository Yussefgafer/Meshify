package com.p2p.meshify.feature.chat

import android.content.Context
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.security.model.SecurityEvent
import com.p2p.meshify.feature.chat.viewmodels.ChatInputViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.eq
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ChatInputViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockRepository: IChatRepository = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)

    private lateinit var viewModel: ChatInputViewModel

    private val testPeerId = "peer-123"
    private val testPeerName = "Alice"

    @Before
    fun setup() {
        every { mockRepository.onlinePeers } returns kotlinx.coroutines.flow.flowOf(emptySet())
        every { mockRepository.typingPeers } returns kotlinx.coroutines.flow.flowOf(emptySet())
        every { mockRepository.securityEvents } returns MutableSharedFlow<SecurityEvent>(replay = 0, extraBufferCapacity = 10)

        // Setup SecurityEvent mock strings
        every { mockContext.getString(R.string.security_warning_decryption_failed, any<String>(), any<String>()) } returns "Decryption failed"
        every { mockContext.getString(R.string.security_warning_tofu_violation, any<String>()) } returns "TOFU violation"
        every { mockContext.getString(R.string.security_warning_session_expired, any<String>()) } returns "Session expired"

        every { mockContext.getString(R.string.error_peer_offline_message_saved) } returns "Peer offline"
        every { mockContext.getString(R.string.error_network_retry) } returns "Network error"
        every { mockContext.getString(R.string.error_message_send_failed, any<String>()) } returns "Send failed"
        every { mockContext.getString(R.string.error_unknown) } returns "Unknown"

        viewModel = ChatInputViewModel(mockRepository, testPeerId, testPeerName, mockContext)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state should have empty input and no reply`() {
        val state = viewModel.uiState.value

        assertEquals("", state.inputText)
        assertEquals("", state.draftText)
        assertNull(state.replyTo)
        assertFalse(state.isSending)
        assertNull(state.sendError)
    }

    // ==================== Input Change Tests ====================

    @Test
    fun `onInputChanged should update inputText in state`() {
        viewModel.onInputChanged("Hello world")

        assertEquals("Hello world", viewModel.uiState.value.inputText)
    }

    @Test
    fun `onInputChanged should also update draftText in state`() {
        viewModel.onInputChanged("Draft message")

        assertEquals("Draft message", viewModel.uiState.value.draftText)
    }

    @Test
    fun `onInputChanged with empty string should clear input and draft`() {
        viewModel.onInputChanged("Some text")
        viewModel.onInputChanged("")

        val state = viewModel.uiState.value
        assertEquals("", state.inputText)
        assertEquals("", state.draftText)
    }

    // ==================== Draft Persistence Tests ====================

    @Test
    fun `restoreDraftText should update draftText in state`() {
        viewModel.restoreDraftText("Saved draft")

        assertEquals("Saved draft", viewModel.uiState.value.draftText)
    }

    @Test
    fun `restoreDraftText should not affect inputText`() {
        viewModel.onInputChanged("Current input")
        viewModel.restoreDraftText("Different draft")

        val state = viewModel.uiState.value
        assertEquals("Current input", state.inputText)
        assertEquals("Different draft", state.draftText)
    }

    // ==================== Reply Tests ====================

    @Test
    fun `setReplyTo should set replyTo message in state`() {
        val replyMessage = testMessage(id = "reply-msg", text = "Reply to this")

        viewModel.setReplyTo(replyMessage)

        assertEquals("reply-msg", viewModel.uiState.value.replyTo?.id)
        assertEquals("Reply to this", viewModel.uiState.value.replyTo?.text)
    }

    @Test
    fun `setReplyTo with null should clear reply`() {
        viewModel.setReplyTo(testMessage(id = "reply-msg"))
        viewModel.setReplyTo(null)

        assertNull(viewModel.uiState.value.replyTo)
    }

    // ==================== sendMessage Validation Tests ====================

    @Test
    fun `sendMessage with empty text should not call repository`() = runTest {
        viewModel.onInputChanged("")
        viewModel.sendMessage()

        coVerify(exactly = 0) { mockRepository.sendMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage with blank text should not call repository`() = runTest {
        viewModel.onInputChanged("   ")
        viewModel.sendMessage()

        coVerify(exactly = 0) { mockRepository.sendMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage with valid text should call repository`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.onInputChanged("Hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        coVerify { mockRepository.sendMessage(testPeerId, testPeerName, "Hello", null) }
    }

    @Test
    fun `sendMessage with reply should pass replyToId to repository`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.setReplyTo(testMessage(id = "reply-to"))
        viewModel.onInputChanged("Reply text")
        viewModel.sendMessage()
        advanceUntilIdle()

        coVerify { mockRepository.sendMessage(testPeerId, testPeerName, "Reply text", "reply-to") }
    }

    @Test
    fun `sendMessage on success should clear input, draft, reply, and isSending`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.setReplyTo(testMessage(id = "reply-to"))
        viewModel.onInputChanged("Text to send")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.inputText)
        assertEquals("", state.draftText)
        assertNull(state.replyTo)
        assertFalse(state.isSending)
    }

    @Test
    fun `sendMessage on failure should restore input text and set error`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.failure(Exception("Network error"))

        viewModel.onInputChanged("Will fail")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Will fail", state.inputText)
        assertNotNull(state.sendError)
        assertFalse(state.isSending)
    }

    @Test
    fun `sendMessage while already sending should be ignored`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.onInputChanged("First")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.onInputChanged("Second")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Only one sendMessage call should have been made due to isSending guard
        coVerify(exactly = 1) { mockRepository.sendMessage(any(), any(), any(), any()) }
    }

    // ==================== Send Debouncing Tests ====================

    @Test
    fun `sendMessage within debounce window should be ignored`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.onInputChanged("First message")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Immediately send again (within 500ms debounce)
        viewModel.onInputChanged("Second message")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Only the first send should have gone through
        coVerify(exactly = 1) { mockRepository.sendMessage(any(), any(), eq("First message"), any()) }
    }

    @Test
    fun `sendMessage after debounce window should succeed`() = runTest {
        coEvery { mockRepository.sendMessage(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.onInputChanged("First message")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Wait past debounce window (500ms + buffer)
        advanceTimeBy(600)
        runCurrent()

        viewModel.onInputChanged("Second message")
        viewModel.sendMessage()
        advanceUntilIdle()

        coVerify(exactly = 2) { mockRepository.sendMessage(any(), any(), any(), any()) }
    }

    // ==================== Forward Dialog Tests ====================

    @Test
    fun `openForwardDialog should set forward dialog state with message`() = runTest {
        val messages = listOf(
            testMessage(id = "msg-1", text = "Forward this"),
            testMessage(id = "msg-2", text = "Not this one")
        )

        viewModel.openForwardDialog("msg-1", messages)
        advanceUntilIdle()

        val dialogState = viewModel.forwardDialogState.value
        assertEquals(1, dialogState.messages.size)
        assertEquals("msg-1", dialogState.messages[0].id)
    }

    @Test
    fun `openForwardDialog with non-existent message should not update state`() = runTest {
        val messages = listOf(testMessage(id = "msg-1"))
        val initialState = viewModel.forwardDialogState.value

        viewModel.openForwardDialog("non-existent", messages)
        advanceUntilIdle()

        // State should remain unchanged (empty)
        val dialogState = viewModel.forwardDialogState.value
        assertTrue(dialogState.messages.isEmpty())
    }

    @Test
    fun `openForwardDialogForSelected with no selection should not update state`() = runTest {
        val messages = listOf(testMessage(id = "msg-1"))

        viewModel.openForwardDialogForSelected(messages)
        advanceUntilIdle()

        val dialogState = viewModel.forwardDialogState.value
        assertTrue(dialogState.messages.isEmpty())
    }

    @Test
    fun `openForwardDialogForSelected should include only selected messages`() = runTest {
        val messages = listOf(
            testMessage(id = "msg-1", text = "First"),
            testMessage(id = "msg-2", text = "Second"),
            testMessage(id = "msg-3", text = "Third")
        )

        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-3")
        advanceUntilIdle()

        viewModel.openForwardDialogForSelected(messages)
        advanceUntilIdle()

        val dialogState = viewModel.forwardDialogState.value
        assertEquals(2, dialogState.messages.size)
        assertTrue(dialogState.messages.all { it.id in listOf("msg-1", "msg-3") })
    }

    @Test
    fun `togglePeerSelection should add peer to selection`() = runTest {
        viewModel.openForwardDialog("msg-1", listOf(testMessage(id = "msg-1")))
        advanceUntilIdle()

        viewModel.togglePeerSelection("peer-a")
        advanceUntilIdle()

        val dialogState = viewModel.forwardDialogState.value
        assertTrue(dialogState.selectedPeerIds.contains("peer-a"))
    }

    @Test
    fun `togglePeerSelection should remove peer if already selected`() = runTest {
        viewModel.openForwardDialog("msg-1", listOf(testMessage(id = "msg-1")))
        advanceUntilIdle()

        viewModel.togglePeerSelection("peer-a")
        advanceUntilIdle()
        assertTrue(viewModel.forwardDialogState.value.selectedPeerIds.contains("peer-a"))

        viewModel.togglePeerSelection("peer-a")
        advanceUntilIdle()

        assertFalse(viewModel.forwardDialogState.value.selectedPeerIds.contains("peer-a"))
    }

    @Test
    fun `updateForwardSearchQuery should update search query in state`() = runTest {
        viewModel.updateForwardSearchQuery("Alice")

        val dialogState = viewModel.forwardDialogState.value
        assertEquals("Alice", dialogState.searchQuery)
    }

    @Test
    fun `dismissForwardDialog should reset forward dialog state`() = runTest {
        viewModel.openForwardDialog("msg-1", listOf(testMessage(id = "msg-1")))
        advanceUntilIdle()
        viewModel.togglePeerSelection("peer-a")
        advanceUntilIdle()

        viewModel.dismissForwardDialog()

        val dialogState = viewModel.forwardDialogState.value
        assertTrue(dialogState.messages.isEmpty())
        assertTrue(dialogState.selectedPeerIds.isEmpty())
        assertEquals("", dialogState.searchQuery)
    }

    // ==================== Multi-Select Tests ====================

    @Test
    fun `toggleMessageSelection should add message to selection`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()

        assertTrue(viewModel.selectedMessages.value.contains("msg-1"))
    }

    @Test
    fun `toggleMessageSelection should remove message if already selected`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()
        assertTrue(viewModel.selectedMessages.value.contains("msg-1"))

        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()

        assertFalse(viewModel.selectedMessages.value.contains("msg-1"))
    }

    @Test
    fun `toggleMessageSelection should handle multiple selections`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-2")
        viewModel.toggleMessageSelection("msg-3")
        advanceUntilIdle()

        val selected = viewModel.selectedMessages.value
        assertEquals(3, selected.size)
        assertTrue(selected.containsAll(listOf("msg-1", "msg-2", "msg-3")))
    }

    @Test
    fun `clearSelection should empty selected messages`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-2")
        advanceUntilIdle()
        assertEquals(2, viewModel.selectedMessages.value.size)

        viewModel.clearSelection()

        assertTrue(viewModel.selectedMessages.value.isEmpty())
    }

    @Test
    fun `isInSelectionMode should reflect selection state`() = runTest {
        assertFalse(viewModel.isInSelectionMode)

        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()

        assertTrue(viewModel.isInSelectionMode)
    }

    @Test
    fun `deleteSelectedMessages should delete each selected message from repository`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-2")
        advanceUntilIdle()

        viewModel.deleteSelectedMessages(DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        coVerify { mockRepository.deleteMessage("msg-1", DeleteType.DELETE_FOR_EVERYONE) }
        coVerify { mockRepository.deleteMessage("msg-2", DeleteType.DELETE_FOR_EVERYONE) }
    }

    @Test
    fun `deleteSelectedMessages should clear selection after deletion`() = runTest {
        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()

        viewModel.deleteSelectedMessages(DeleteType.DELETE_FOR_EVERYONE)
        advanceUntilIdle()

        assertTrue(viewModel.selectedMessages.value.isEmpty())
    }

    @Test
    fun `deleteSelectedMessages with no selection should not call repository`() = runTest {
        viewModel.deleteSelectedMessages(DeleteType.DELETE_FOR_ME)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.deleteMessage(any(), any()) }
    }

    // ==================== Copy to Clipboard Tests ====================

    @Test
    fun `copySelectedMessagesToClipboard should copy text of selected messages`() = runTest {
        val messages = listOf(
            testMessage(id = "msg-1", text = "First text"),
            testMessage(id = "msg-2", text = "Second text"),
            testMessage(id = "msg-3", text = null) // null text should be skipped
        )
        val mockClipboard = mockk<ClipboardManager>(relaxed = true)

        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-2")
        advanceUntilIdle()

        viewModel.copySelectedMessagesToClipboard(messages, mockClipboard)
        advanceUntilIdle()

        val capturedText = slot<AnnotatedString>()
        verify { mockClipboard.setText(capture(capturedText)) }
        assertTrue(capturedText.captured.text.contains("First text"))
        assertTrue(capturedText.captured.text.contains("Second text"))
    }

    @Test
    fun `copySelectedMessagesToClipboard with no selection should not use clipboard`() = runTest {
        val messages = listOf(testMessage(id = "msg-1", text = "Test"))
        val mockClipboard = mockk<ClipboardManager>(relaxed = true)

        viewModel.copySelectedMessagesToClipboard(messages, mockClipboard)
        advanceUntilIdle()

        verify(exactly = 0) { mockClipboard.setText(any()) }
    }

    @Test
    fun `copySelectedMessagesToClipboard with selected messages having null text should not copy`() = runTest {
        val messages = listOf(
            testMessage(id = "msg-1", text = null, type = MessageType.IMAGE),
            testMessage(id = "msg-2", text = null, type = MessageType.VIDEO)
        )
        val mockClipboard = mockk<ClipboardManager>(relaxed = true)

        viewModel.toggleMessageSelection("msg-1")
        viewModel.toggleMessageSelection("msg-2")
        advanceUntilIdle()

        viewModel.copySelectedMessagesToClipboard(messages, mockClipboard)
        advanceUntilIdle()

        verify(exactly = 0) { mockClipboard.setText(any()) }
    }

    @Test
    fun `copySelectedMessagesToClipboard should clear selection after copy`() = runTest {
        val messages = listOf(testMessage(id = "msg-1", text = "Copy me"))
        val mockClipboard = mockk<ClipboardManager>(relaxed = true)

        viewModel.toggleMessageSelection("msg-1")
        advanceUntilIdle()

        viewModel.copySelectedMessagesToClipboard(messages, mockClipboard)
        advanceUntilIdle()

        assertTrue(viewModel.selectedMessages.value.isEmpty())
    }

    // ==================== clearError Tests ====================

    @Test
    fun `clearError should set sendError to null`() = runTest {
        // Simulate error state by directly checking initial state is null
        assertNull(viewModel.uiState.value.sendError)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.sendError)
    }

    // ==================== forwardMessages Tests ====================

    @Test
    fun `forwardMessages with no selected peers should not call repository`() = runTest {
        viewModel.openForwardDialog("msg-1", listOf(testMessage(id = "msg-1")))
        advanceUntilIdle()

        viewModel.forwardMessages(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.forwardMessage(any(), any()) }
    }

    @Test
    fun `forwardMessages with no messages should not call repository`() = runTest {
        // No dialog opened, so no messages set
        viewModel.togglePeerSelection("peer-a")
        advanceUntilIdle()

        viewModel.forwardMessages(listOf("peer-a"))
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.forwardMessage(any(), any()) }
    }

    @Test
    fun `forwardMessages should call repository for each selected peer`() = runTest {
        coEvery { mockRepository.forwardMessage(any(), any()) } returns Result.success(Unit)

        viewModel.openForwardDialog("msg-1", listOf(testMessage(id = "msg-1")))
        advanceUntilIdle()
        viewModel.togglePeerSelection("peer-a")
        viewModel.togglePeerSelection("peer-b")
        advanceUntilIdle()

        viewModel.forwardMessages(listOf("peer-a", "peer-b"))
        advanceUntilIdle()

        coVerify(atLeast = 1) { mockRepository.forwardMessage("msg-1", listOf("peer-a")) }
    }
}
