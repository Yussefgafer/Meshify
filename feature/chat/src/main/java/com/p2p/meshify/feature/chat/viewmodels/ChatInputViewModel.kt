package com.p2p.meshify.feature.chat.viewmodels

import android.content.Context
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.ui.components.ForwardDialogState
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.chat.state.ChatInputUiState
import com.p2p.meshify.core.common.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel responsible for text input, draft management, message sending,
 * forwarding, and multi-select operations.
 *
 * Extracted from the monolithic ChatViewModel to reduce coupling and improve testability.
 * Handles:
 * - Text input changes and draft persistence
 * - Reply-to-message logic
 * - Send message (text only, with debouncing and error handling)
 * - Forward dialog state management (search, peer selection, multi-message forward)
 * - Multi-select mode (toggle, clear, delete selected, copy to clipboard)
 * - Back confirmation logic (reserved)
 */
@HiltViewModel
class ChatInputViewModel @Inject constructor(
    private val chatRepository: IChatRepository,
    private val peerId: String,
    private val peerName: String,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow(ChatInputUiState())
    val uiState: StateFlow<ChatInputUiState> = _uiState.asStateFlow()

    // Forward dialog state (exposed separately for Compose collection)
    private val _forwardDialogState = MutableStateFlow(ForwardDialogState())
    val forwardDialogState: StateFlow<ForwardDialogState> = _forwardDialogState.asStateFlow()

    // Multi-select mode
    private val _selectedMessages = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessages: StateFlow<Set<String>> = _selectedMessages.asStateFlow()

    val isInSelectionMode: Boolean
        get() = _selectedMessages.value.isNotEmpty()

    // ==================== Send Debouncing ====================

    /**
     * Double tap protection — prevent sending the same message twice.
     * 500ms debounce window.
     */
    private var lastSendTime = 0L
    private val sendDebounceMs = 500L

    // ==================== Input & Draft ====================

    /**
     * Updates the input text and persists it as draft.
     *
     * @param text The current text in the input field.
     */
    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text, draftText = text) }
    }

    /**
     * Restores draft text from ViewModel state.
     * Called when the Composable is first created to sync with saved state.
     *
     * @param text The draft text to restore.
     */
    fun restoreDraftText(text: String) {
        _uiState.update { it.copy(draftText = text) }
    }

    /**
     * Sets the message to reply to.
     *
     * @param message The message to reply to, or null to cancel reply.
     */
    fun setReplyTo(message: MessageEntity?) {
        _uiState.update { it.copy(replyTo = message) }
    }

    // ==================== Send Message ====================

    /**
     * Sends the current input text as a message.
     * Includes double-tap protection (500ms debounce) and error handling.
     * On failure, the input text is restored so the user does not lose their message.
     */
    fun sendMessage() {
        val state = _uiState.value
        val hasText = state.inputText.isNotBlank()

        if (!hasText) return
        if (state.isSending) return // Prevent double-send

        // Double tap protection — ignore if too soon
        val now = System.currentTimeMillis()
        if (now - lastSendTime < sendDebounceMs) {
            Logger.d("ChatInputViewModel -> Double tap detected, ignoring send")
            return
        }
        lastSendTime = now

        viewModelScope.launch {
            // Set isSending to true immediately to disable button
            _uiState.update { it.copy(isSending = true) }

            try {
                // Send text message
                chatRepository.sendMessage(peerId, peerName, state.inputText, state.replyTo?.id)
                _uiState.update { it.copy(inputText = "", draftText = "", replyTo = null, isSending = false) }
            } catch (e: Exception) {
                Logger.e("ChatInputViewModel -> Failed to send message", e)
                val errorMessage = when {
                    e.message?.contains("offline", ignoreCase = true) == true ->
                        context.getString(R.string.error_peer_offline_message_saved)
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true ->
                        context.getString(R.string.error_network_retry)
                    else -> context.getString(R.string.error_message_send_failed, e.message ?: context.getString(R.string.error_unknown))
                }
                _uiState.update {
                    it.copy(
                        isSending = false,
                        sendError = errorMessage,
                        inputText = state.inputText // Restore text on failure
                    )
                }
            }
        }
    }

    // ==================== Forward Dialog ====================

    /**
     * Opens forward dialog for a single message.
     *
     * @param messageId The ID of the message to forward.
     * @param messages The current list of messages to find the message from.
     */
    fun openForwardDialog(messageId: String, messages: List<MessageEntity>) {
        viewModelScope.launch {
            val message = messages.find { it.id == messageId }
                ?: return@launch

            _forwardDialogState.value = ForwardDialogState(
                messages = listOf(message),
                selectedPeerIds = emptySet(),
                searchQuery = "",
                isForwarding = false,
                forwardProgress = 0
            )
        }
    }

    /**
     * Opens forward dialog for multiple selected messages.
     *
     * @param messages The current list of messages to find selected messages from.
     */
    fun openForwardDialogForSelected(messages: List<MessageEntity>) {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value
            if (selectedIds.isEmpty()) return@launch

            val selectedMessages = messages.filter { it.id in selectedIds }

            _forwardDialogState.value = ForwardDialogState(
                messages = selectedMessages,
                selectedPeerIds = emptySet(),
                searchQuery = "",
                isForwarding = false,
                forwardProgress = 0
            )
        }
    }

    /**
     * Toggle selection for a peer in forward dialog.
     *
     * @param peerId The peer ID to toggle.
     */
    fun togglePeerSelection(peerId: String) {
        viewModelScope.launch {
            val currentState = _forwardDialogState.value
            val newSelectedIds = if (peerId in currentState.selectedPeerIds) {
                currentState.selectedPeerIds - peerId
            } else {
                currentState.selectedPeerIds + peerId
            }

            _forwardDialogState.value = currentState.copy(
                selectedPeerIds = newSelectedIds
            )
        }
    }

    /**
     * Update search query in forward dialog.
     *
     * @param query The search query text.
     */
    fun updateForwardSearchQuery(query: String) {
        _forwardDialogState.value = _forwardDialogState.value.copy(
            searchQuery = query
        )
    }

    /**
     * Forward selected messages to chosen peers.
     *
     * @param targetPeerIds List of peer IDs to forward messages to.
     */
    fun forwardMessages(targetPeerIds: List<String>) {
        viewModelScope.launch {
            val currentState = _forwardDialogState.value
            if (currentState.selectedPeerIds.isEmpty() || currentState.messages.isEmpty()) return@launch

            // Update state to forwarding
            _forwardDialogState.value = currentState.copy(
                isForwarding = true,
                forwardProgress = 0
            )

            val messagesToForward = currentState.messages
            val peerIds = currentState.selectedPeerIds.toList()
            var successCount = 0
            var failedCount = 0

            // Forward each message to each peer
            messagesToForward.forEach { message ->
                peerIds.forEach { targetPeerId ->
                    try {
                        val result = chatRepository.forwardMessage(message.id, listOf(targetPeerId))
                        if (result.isSuccess) {
                            successCount++
                        } else {
                            failedCount++
                            Logger.e("ChatInputViewModel -> Failed to forward message ${message.id} to $targetPeerId: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        failedCount++
                        Logger.e("ChatInputViewModel -> Exception forwarding message ${message.id} to $targetPeerId", e)
                    }

                    // Update progress
                    _forwardDialogState.value = _forwardDialogState.value.copy(
                        forwardProgress = successCount + failedCount
                    )
                }
            }

            // Show result
            if (failedCount == 0) {
                Logger.d("ChatInputViewModel -> Successfully forwarded $successCount messages to ${peerIds.size} peers")
            } else {
                Logger.w("ChatInputViewModel -> Forwarded $successCount messages, $failedCount failed")
            }

            // Reset state after delay
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.delay(1000)
                _forwardDialogState.value = ForwardDialogState()
                clearSelection() // Clear multi-select mode
            }
        }
    }

    /**
     * Dismiss forward dialog.
     */
    fun dismissForwardDialog() {
        _forwardDialogState.value = ForwardDialogState()
    }

    // ==================== Multi-Select Mode ====================

    /**
     * Toggle message selection for multi-select mode.
     *
     * @param messageId The ID of the message to toggle.
     */
    fun toggleMessageSelection(messageId: String) {
        viewModelScope.launch {
            val current = _selectedMessages.value
            _selectedMessages.value = if (messageId in current) {
                current - messageId
            } else {
                current + messageId
            }
        }
    }

    /**
     * Clear all selected messages.
     */
    fun clearSelection() {
        _selectedMessages.value = emptySet()
    }

    /**
     * Delete all selected messages.
     *
     * @param deleteType Whether to delete for self only or for everyone.
     */
    fun deleteSelectedMessages(deleteType: DeleteType) {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value.toList()
            selectedIds.forEach { messageId ->
                chatRepository.deleteMessage(messageId, deleteType)
            }
            clearSelection()
        }
    }

    /**
     * Copy all selected messages to clipboard.
     *
     * @param messages The current list of messages to find selected messages from.
     * @param clipboard The ClipboardManager to use for copying.
     */
    fun copySelectedMessagesToClipboard(
        messages: List<MessageEntity>,
        clipboard: ClipboardManager
    ) {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value
            if (selectedIds.isEmpty()) return@launch

            val selectedMessageEntities = messages.filter { it.id in selectedIds && it.text != null }
            val textToCopy = selectedMessageEntities.joinToString("\n\n") { it.text ?: "" }

            if (textToCopy.isNotBlank()) {
                clipboard.setText(AnnotatedString(textToCopy))
                Logger.d("ChatInputViewModel -> Copied ${selectedMessageEntities.size} messages to clipboard")
            }

            clearSelection()
        }
    }

    // ==================== State Clearing ====================

    /**
     * Clears the send error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(sendError = null) }
    }
}
