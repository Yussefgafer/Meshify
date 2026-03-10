package com.p2p.meshify.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.repository.IChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StagedAttachment(
    val uri: Uri,
    val bytes: ByteArray,
    val type: MessageType
)

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isOnline: Boolean = false,
    val isPeerTyping: Boolean = false,
    val inputText: String = "",
    val replyTo: MessageEntity? = null,
    val stagedAttachments: List<StagedAttachment> = emptyList()
)

class ChatViewModel(
    private val peerId: String,
    private val peerName: String,
    private val repository: IChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val stageMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.getMessages(peerId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch {
            repository.onlinePeers.collect { online ->
                _uiState.update { it.copy(isOnline = online.contains(peerId)) }
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
}
