package com.p2p.meshify.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Recent Chats (Home) Screen with Online Status.
 * Depends on ChatRepositoryImpl for direct method access.
 */
class RecentChatsViewModel(
    private val chatRepository: ChatRepositoryImpl
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
