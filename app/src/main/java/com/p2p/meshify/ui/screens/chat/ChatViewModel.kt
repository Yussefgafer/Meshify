package com.p2p.meshify.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.usecase.DeleteMessagesUseCase
import com.p2p.meshify.domain.usecase.GetMessagesUseCase
import com.p2p.meshify.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 50
private const val GROUPING_TIMEOUT_MS = 5 * 60 * 1000

/**
 * Grouped Message data class for efficient UI rendering.
 * Contains pre-calculated grouping metadata to move logic out of UI.
 */
data class GroupedMessage(
    val message: MessageEntity,
    val isGroupedWithPrevious: Boolean,
    val isGroupedWithNext: Boolean,
    val showAvatar: Boolean,
    val isFirstInGroup: Boolean,
    val isLastInGroup: Boolean
)

/**
 * Robust ViewModel with Pagination and Reactive Peer Name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val context: Context,
    private val peerId: String,
    private val chatRepository: IChatRepository,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessagesUseCase: DeleteMessagesUseCase
) : ViewModel() {

    // REACTIVE PEER NAME: Observes changes in ChatEntity
    val peerName: StateFlow<String> = chatRepository.getAllChats()
        .map { chats -> chats.find { it.peerId == peerId }?.peerName ?: "Peer_${peerId.take(4)}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Loading...")

    private val _pageSize = MutableStateFlow(PAGE_SIZE)
    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    // Grouped Messages - Pre-calculated grouping logic for UI efficiency
    private val _groupedMessages = MutableStateFlow<List<GroupedMessage>>(emptyList())
    val groupedMessages: StateFlow<List<GroupedMessage>> = _groupedMessages.asStateFlow()

    init {
        viewModelScope.launch {
            _pageSize.flatMapLatest { size ->
                getMessagesUseCase(peerId, size, 0)
            }.collect { entities ->
                val reversed = entities.reversed()
                _messages.value = reversed
                // Calculate grouping in ViewModel
                _groupedMessages.value = calculateGrouping(reversed)
            }
        }
    }

    /**
     * Calculates grouping metadata for all messages.
     * Moves expensive grouping logic out of UI layer.
     */
    private fun calculateGrouping(messages: List<MessageEntity>): List<GroupedMessage> {
        return messages.mapIndexed { index, message ->
            val prevMessage = if (index > 0) messages[index - 1] else null
            val nextMessage = if (index < messages.size - 1) messages[index + 1] else null

            val isGroupedWithPrevious = prevMessage?.senderId == message.senderId &&
                    (message.timestamp - prevMessage.timestamp) < GROUPING_TIMEOUT_MS

            val isGroupedWithNext = nextMessage?.senderId == message.senderId &&
                    (nextMessage.timestamp - message.timestamp) < GROUPING_TIMEOUT_MS

            val isFirstInGroup = !isGroupedWithPrevious
            val isLastInGroup = !isGroupedWithNext

            // Show avatar only for the last message in each group
            val showAvatar = isLastInGroup

            GroupedMessage(
                message = message,
                isGroupedWithPrevious = isGroupedWithPrevious,
                isGroupedWithNext = isGroupedWithNext,
                showAvatar = showAvatar,
                isFirstInGroup = isFirstInGroup,
                isLastInGroup = isLastInGroup
            )
        }
    }

    fun loadMoreMessages() {
        _pageSize.update { it + PAGE_SIZE }
    }

    val isOnline: StateFlow<Boolean> = chatRepository.onlinePeers
        .map { it.contains(peerId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPeerTyping: StateFlow<Boolean> = chatRepository.typingPeers
        .map { it.contains(peerId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val inputText = MutableStateFlow("")
    
    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

    // Error handling for send failures
    private val _sendError = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)
    val sendError = _sendError.asSharedFlow()

    private var typingJob: Job? = null
    private var isTypingSignalSent = false

    fun onInputChanged(text: String) {
        inputText.value = text
        handleTypingSignal(text.isNotEmpty())
    }

    private fun handleTypingSignal(isTyping: Boolean) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            if (isTyping) {
                if (!isTypingSignalSent) {
                    chatRepository.sendSystemCommand(peerId, "TYPING_ON")
                    isTypingSignalSent = true
                }
                delay(3000)
                chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
                isTypingSignalSent = false
            } else if (isTypingSignalSent) {
                chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
                isTypingSignalSent = false
            }
        }
    }

    fun setPendingImage(uri: Uri?) {
        _pendingImageUri.value = uri
    }

    fun sendMessage() {
        val text = inputText.value.trim()
        val imageUri = _pendingImageUri.value
        val currentPeerName = peerName.value

        if (text.isEmpty() && imageUri == null) return

        viewModelScope.launch {
            typingJob?.cancel()
            if (isTypingSignalSent) {
                chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
                isTypingSignalSent = false
            }

            try {
                if (imageUri != null) {
                    // ✅ FIX: Move blocking I/O to IO dispatcher
                    val bytes: ByteArray? = withContext(Dispatchers.IO) {
                        FileUtils.getBytesFromUri(context, imageUri)
                    }
                    if (bytes != null) {
                        val result = chatRepository.sendImage(peerId, currentPeerName, bytes, "jpg")
                        if (result.isFailure) {
                            _sendError.emit(result.exceptionOrNull() ?: Exception("Failed to send image"))
                        }
                    }
                    if (text.isNotEmpty()) {
                        val result = sendMessageUseCase(peerId, currentPeerName, text)
                        if (result.isFailure) {
                            _sendError.emit(result.exceptionOrNull() ?: Exception("Failed to send message"))
                        }
                    }
                } else {
                    val result = sendMessageUseCase(peerId, currentPeerName, text)
                    if (result.isFailure) {
                        _sendError.emit(result.exceptionOrNull() ?: Exception("Failed to send message"))
                    }
                }

                inputText.value = ""
                _pendingImageUri.value = null
            } catch (e: Exception) {
                // Catch any unexpected exceptions
                _sendError.emit(e)
            }
        }
    }

    fun toggleMessageSelection(messageId: String) {
        _selectedMessageIds.update { current ->
            if (current.contains(messageId)) current - messageId else current + messageId
        }
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun deleteSelectedMessages() {
        val ids = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            deleteMessagesUseCase(ids)
            clearSelection()
        }
    }

    fun removePendingImage() {
        _pendingImageUri.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup: cancel typing job and send TYPING_OFF if needed
        typingJob?.cancel()
        viewModelScope.launch {
            if (isTypingSignalSent) {
                chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
            }
        }
        // Clear pending image URI to prevent memory leaks
        _pendingImageUri.value = null
        // Clear selection
        _selectedMessageIds.value = emptySet()
    }
}
