package com.p2p.meshify.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.core.ui.model.StagedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isOnline: Boolean = false,
    val isPeerTyping: Boolean = false,
    val inputText: String = "",
    val replyTo: MessageEntity? = null,
    val stagedAttachments: List<StagedAttachment> = emptyList(),
    val hasMoreMessages: Boolean = false,
    val isLoadingMore: Boolean = false
)

class ChatViewModel(
    private val peerId: String,
    private val peerName: String,
    private val repository: ChatRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val stageMutex = Mutex()
    private val paginationMutex = Mutex()

    // Pagination state - using ArrayDeque for O(1) prepend operations
    private var currentPage = 0
    private val pageSize = 50
    private var isAllMessagesLoaded = false
    private val allMessages = ArrayDeque<MessageEntity>(initialCapacity = 100)

    // Maximum messages to keep in memory (prevent memory leaks in long conversations)
    companion object {
        private const val MAX_MESSAGES_IN_MEMORY = 500
    }

    init {
        // Load initial page of messages
        loadMoreMessages()

        // ✅ FIX: Collect messages flow to update UI when new messages arrive
        // This ensures real-time updates when messages are received from the network
        viewModelScope.launch {
            repository.getMessages(peerId).collect { messages ->
                Logger.d("ChatViewModel -> Messages updated: ${messages.size} messages for peer $peerId")

                // ✅ FIX: Only update UI state, don't manipulate allMessages here
                // allMessages is only for pagination (loadMoreMessages)
                _uiState.update {
                    it.copy(
                        messages = messages,
                        hasMoreMessages = !isAllMessagesLoaded
                    )
                }
            }
        }

        // Collect online status
        viewModelScope.launch {
            repository.onlinePeers.collect { online ->
                _uiState.update { it.copy(isOnline = online.contains(peerId)) }
            }
        }
    }

    /**
     * Loads more messages for pagination.
     * Called initially and when user scrolls to top.
     */
    fun loadMoreMessages() {
        viewModelScope.launch {
            // Use tryLock to avoid waiting if already loading
            if (!paginationMutex.tryLock()) return@launch

            try {
                if (isAllMessagesLoaded || _uiState.value.isLoadingMore) return@launch

                _uiState.update { it.copy(isLoadingMore = true) }

                try {
                    // ✅ CRITICAL FIX: Use withContext(Dispatchers.IO) for blocking .first() call
                    // This prevents potential UI thread blocking
                    val newPage = withContext(Dispatchers.IO) {
                        repository.getMessagesPaged(peerId, pageSize, currentPage * pageSize).first()
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
                    Logger.e("ChatViewModel -> Failed to load messages", e)
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } finally {
                paginationMutex.unlock()
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setReplyTo(message: MessageEntity?) {
        _uiState.update { it.copy(replyTo = message) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val hasText = state.inputText.isNotBlank()
        val hasAttachments = state.stagedAttachments.isNotEmpty()

        if (!hasText && !hasAttachments) return

        viewModelScope.launch {
            if (hasAttachments) {
                // Send grouped message with attachments
                val attachments = state.stagedAttachments.map { it.bytes to it.type }
                repository.sendGroupedMessage(peerId, peerName, state.inputText, attachments, state.replyTo?.id)
                _uiState.update { it.copy(inputText = "", replyTo = null, stagedAttachments = emptyList()) }
            } else {
                // Send text message
                repository.sendMessage(peerId, peerName, state.inputText, state.replyTo?.id)
                _uiState.update { it.copy(inputText = "", replyTo = null) }
            }
        }
    }

    fun stageAttachment(uri: Uri, bytes: ByteArray, type: MessageType) {
        viewModelScope.launch {
            stageMutex.withLock {
                val current = _uiState.value.stagedAttachments
                if (current.size >= 10) return@launch // Limit to 10 attachments

                val newAttachment = StagedAttachment(uri, bytes, type)
                val updated = current + newAttachment
                _uiState.update { it.copy(stagedAttachments = updated) }
            }
        }
    }

    fun removeStagedAttachment(uri: Uri) {
        viewModelScope.launch {
            stageMutex.withLock {
                val updated = _uiState.value.stagedAttachments.filter { it.uri != uri }
                _uiState.update { it.copy(stagedAttachments = updated) }
            }
        }
    }

    fun clearStagedAttachments() {
        viewModelScope.launch {
            stageMutex.withLock {
                _uiState.update { it.copy(stagedAttachments = emptyList()) }
            }
        }
    }

    fun sendImage(bytes: ByteArray, extension: String) {
        viewModelScope.launch {
            repository.sendImage(peerId, peerName, bytes, extension, _uiState.value.replyTo?.id)
            _uiState.update { it.copy(replyTo = null) }
        }
    }

    fun sendVideo(bytes: ByteArray, extension: String) {
        viewModelScope.launch {
            repository.sendVideo(peerId, peerName, bytes, extension, _uiState.value.replyTo?.id)
            _uiState.update { it.copy(replyTo = null) }
        }
    }

    fun deleteMessage(messageId: String, deleteType: DeleteType) {
        viewModelScope.launch {
            repository.deleteMessage(messageId, deleteType)
        }
    }

    fun addReaction(messageId: String, reaction: String?) {
        viewModelScope.launch {
            repository.addReaction(messageId, reaction)
        }
    }

    /**
     * Get attachments for a specific message.
     * This is a suspend function that should be called from a coroutine.
     */
    suspend fun getAttachmentsForMessage(groupId: String): List<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity> {
        return withContext(Dispatchers.IO) {
            repository.getMessageAttachments(groupId)
        }
    }
}
