package com.p2p.meshify.feature.chat.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.security.model.SecurityEvent
import com.p2p.meshify.feature.chat.state.ChatMessagesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ViewModel responsible for message display, pagination, connection status,
 * and security event handling.
 *
 * Extracted from the monolithic ChatViewModel to reduce coupling and improve testability.
 * Handles:
 * - Loading messages from repository (with pagination)
 * - Observing online peers
 * - Observing peer typing status (reserved — not yet collected)
 * - Observing security events
 * - Pagination logic (loadMoreMessages)
 * - Message deletion (delegates to repository)
 * - Reaction adding (delegates to repository)
 */
class ChatMessagesViewModel(
    private val context: Context,
    private val chatRepository: ChatRepositoryImpl,
    private val peerId: String
) : ViewModel() {

    // ==================== Pagination State ====================

    private var currentPage = 0
    private val pageSize = 50
    private var isAllMessagesLoaded = false
    private val allMessages = ArrayDeque<MessageEntity>(initialCapacity = 100)

    /**
     * Maximum messages to keep in memory.
     * Prevents memory leaks in long conversations.
     * 200 messages = ~4MB vs 500 messages = ~10MB.
     */
    private val paginationMutex = Mutex()

    // ==================== Transport Type Tracking ====================

    private var _transportTypeProvider: (() -> TransportType)? = null

    fun setTransportTypeProvider(provider: () -> TransportType) {
        _transportTypeProvider = provider
    }

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow(ChatMessagesUiState())
    val uiState: StateFlow<ChatMessagesUiState> = _uiState.asStateFlow()

    // ==================== Initialization ====================

    init {
        // Load initial page of messages
        loadMoreMessages()

        // Collect messages flow with distinctUntilChanged to reduce recompositions.
        // This ensures real-time updates when messages are received from the network.
        viewModelScope.launch {
            chatRepository.getMessages(peerId)
                .distinctUntilChanged()
                .collect { messages ->
                    Logger.d("ChatMessagesViewModel -> Messages updated: ${messages.size} messages for peer $peerId")

                    // Only update UI state; allMessages is managed separately for pagination.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            messages = messages,
                            hasMoreMessages = !isAllMessagesLoaded
                        )
                    }
                }
        }

        // Collect online status
        viewModelScope.launch {
            chatRepository.onlinePeers.collect { online ->
                _uiState.update { it.copy(isOnline = online.contains(peerId)) }
            }
        }

        // Collect security events from repository.
        // SharedFlow is hot and never terminated — errors are handled in repository before emit.
        viewModelScope.launch {
            chatRepository.securityEvents.collect { event ->
                when (event) {
                    is SecurityEvent.DecryptionFailed -> {
                        val warningText = context.getString(
                            R.string.security_warning_decryption_failed,
                            event.peerId.take(8),
                            event.reason
                        )
                        _uiState.update { it.copy(securityWarning = warningText) }
                        Logger.w("ChatMessagesViewModel -> Decryption failed from ${event.peerId.take(8)}: ${event.reason}")
                    }
                    is SecurityEvent.TofuViolation -> {
                        val warningText = context.getString(
                            R.string.security_warning_tofu_violation,
                            event.peerId.take(8)
                        )
                        _uiState.update { it.copy(securityWarning = warningText) }
                        Logger.e("ChatMessagesViewModel -> TOFU violation for ${event.peerId.take(8)}")
                    }
                    is SecurityEvent.SessionExpired -> {
                        val warningText = context.getString(
                            R.string.security_warning_session_expired,
                            event.peerId.take(8)
                        )
                        _uiState.update { it.copy(securityWarning = warningText) }
                        Logger.w("ChatMessagesViewModel -> Session expired for ${event.peerId.take(8)}")
                    }
                    is SecurityEvent.MessageSendFailed -> {
                        _uiState.update {
                            it.copy(
                                sendError = context.getString(
                                    R.string.error_message_send_failed,
                                    event.reason
                                )
                            )
                        }
                        Logger.e("ChatMessagesViewModel -> Message send failed: ${event.messageId}")
                    }
                }
            }
        }
    }

    // ==================== Pagination ====================

    /**
     * Loads older messages for pagination.
     * Called initially during init and when the user scrolls to the top.
     * Uses a mutex to prevent concurrent load attempts.
     */
    fun loadMoreMessages() {
        viewModelScope.launch {
            // Use tryLock to avoid waiting if already loading
            if (!paginationMutex.tryLock()) return@launch

            try {
                if (isAllMessagesLoaded || _uiState.value.isLoadingMore) return@launch

                _uiState.update { it.copy(isLoadingMore = true) }

                try {
                    val newPage = withContext(Dispatchers.IO) {
                        chatRepository.getMessagesPaged(peerId, pageSize, currentPage * pageSize)
                            .take(1)
                            .firstOrNull()
                            ?: emptyList()
                    }

                    if (newPage.isEmpty()) {
                        isAllMessagesLoaded = true
                    } else {
                        // Prepend new messages efficiently using ArrayDeque
                        allMessages.addAll(0, newPage)

                        // Remove oldest messages if exceeding max to prevent memory leaks
                        while (allMessages.size > MAX_MESSAGES_IN_MEMORY) {
                            allMessages.removeLast()
                        }

                        currentPage++
                    }

                    _uiState.update {
                        it.copy(
                            messages = allMessages.toList(),
                            hasMoreMessages = !isAllMessagesLoaded,
                            isLoadingMore = false
                        )
                    }
                } catch (e: Exception) {
                    Logger.e("ChatMessagesViewModel -> Failed to load messages", e)
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } finally {
                paginationMutex.unlock()
            }
        }
    }

    // ==================== Message Operations ====================

    /**
     * Deletes a message by delegating to the repository.
     *
     * @param messageId The ID of the message to delete.
     * @param deleteType Whether to delete for self only or for everyone.
     */
    fun deleteMessage(messageId: String, deleteType: DeleteType) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId, deleteType)
        }
    }

    /**
     * Adds or removes a reaction on a message by delegating to the repository.
     *
     * @param messageId The ID of the message to react to.
     * @param reaction The emoji reaction, or null to remove an existing reaction.
     */
    fun addReaction(messageId: String, reaction: String?) {
        viewModelScope.launch {
            chatRepository.addReaction(messageId, reaction)
        }
    }

    // ==================== State Clearing ====================

    /**
     * Clears the send error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(sendError = null) }
    }

    /**
     * Clears the upload error message from the UI state.
     */
    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }

    /**
     * Clears the security warning from the UI state after it has been shown.
     */
    fun clearSecurityWarning() {
        _uiState.update { it.copy(securityWarning = null) }
    }

    // ==================== Transport Type Utilities ====================

    /**
     * Get the transport type label resource for a given transport type.
     */
    fun getTransportTypeLabel(transportType: TransportType): String = when (transportType) {
        TransportType.BLE -> context.getString(R.string.chat_transport_ble_desc)
        TransportType.BOTH -> context.getString(R.string.chat_transport_multipath_desc)
        TransportType.LAN -> "" // LAN is default — no badge needed
    }

    companion object {
        private const val MAX_MESSAGES_IN_MEMORY = 200
    }
}
