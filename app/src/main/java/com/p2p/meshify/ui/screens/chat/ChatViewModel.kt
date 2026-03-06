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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

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

    init {
        viewModelScope.launch {
            _pageSize.flatMapLatest { size ->
                getMessagesUseCase(peerId, size, 0)
            }.collect {
                _messages.value = it.reversed()
            }
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

            if (imageUri != null) {
                val bytes = FileUtils.getBytesFromUri(context, imageUri)
                if (bytes != null) {
                    chatRepository.sendImage(peerId, currentPeerName, bytes, "jpg")
                }
                if (text.isNotEmpty()) sendMessageUseCase(peerId, currentPeerName, text)
            } else {
                sendMessageUseCase(peerId, currentPeerName, text)
            }
            
            inputText.value = ""
            _pendingImageUri.value = null
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
}
