package com.p2p.meshify.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.repository.IChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isOnline: Boolean = false,
    val isPeerTyping: Boolean = false,
    val inputText: String = "",
    val replyTo: MessageEntity? = null
)

class ChatViewModel(
    private val peerId: String,
    private val peerName: String,
    private val repository: IChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

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
        if (state.inputText.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(peerId, peerName, state.inputText, state.replyTo?.id)
            _uiState.update { it.copy(inputText = "", replyTo = null) }
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
