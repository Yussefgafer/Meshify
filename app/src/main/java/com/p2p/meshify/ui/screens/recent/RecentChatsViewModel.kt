package com.p2p.meshify.ui.screens.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.data.local.entity.ChatEntity
import com.p2p.meshify.domain.repository.IChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Recent Chats (Home) Screen with Online Status.
 * Refactored to depend on IChatRepository interface.
 */
class RecentChatsViewModel(
    private val chatRepository: IChatRepository
) : ViewModel() {

    val recentChats: StateFlow<List<ChatEntity>> = chatRepository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteChat(peerId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(peerId)
        }
    }

    val onlinePeers: StateFlow<Set<String>> = chatRepository.onlinePeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
}
