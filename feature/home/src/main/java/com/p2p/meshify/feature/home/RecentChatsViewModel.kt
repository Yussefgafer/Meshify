package com.p2p.meshify.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * ViewModel for the Recent Chats (Home) Screen with Online Status.
 * Depends on ChatRepositoryImpl for direct method access.
 */
class RecentChatsViewModel(
    private val chatRepository: ChatRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentChatsUiState())
    val uiState: StateFlow<RecentChatsUiState> = _uiState.asStateFlow()

    init {
        loadChats()
    }

    /**
     * Load chats from repository with proper error handling.
     * Sets loading state initially and handles errors gracefully.
     */
    private fun loadChats() {
        viewModelScope.launch {
            // Set loading state
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                chatRepository.getAllChats()
                    .take(1)
                    .collect { chats ->
                        // Get online peers
                        val onlinePeers = chatRepository.onlinePeers
                            .take(1)
                            .firstOrNull() ?: emptySet()
                        
                        _uiState.value = _uiState.value.copy(
                            chats = chats,
                            onlinePeers = onlinePeers,
                            isLoading = false
                        )
                        
                        Logger.d("RecentChatsViewModel -> Loaded ${chats.size} chats, ${onlinePeers.size} online peers")
                    }
            } catch (e: Exception) {
                Logger.e("RecentChatsViewModel -> Failed to load chats", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Retry loading chats after an error.
     */
    fun retryLoad() {
        loadChats()
    }

    fun deleteChat(peerId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(peerId)
        }
    }
}
