package com.p2p.meshify.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview

/** Debounce interval for search input to avoid excessive DB queries */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * ViewModel for the Recent Chats (Home) Screen with Online Status.
 * Depends on ChatRepositoryImpl for direct method access.
 *
 * ✅ P0-2: Collects Room Flow continuously instead of take(1).
 * Any database change (new chat, deleted chat, updated message)
 * will immediately update the UI.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecentChatsViewModel @Inject constructor(
    private val chatRepository: ChatRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentChatsUiState())
    val uiState: StateFlow<RecentChatsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadChatsWithSearch()

        // ✅ P0-2: Collect online peers independently — updates in real-time
        viewModelScope.launch {
            chatRepository.onlinePeers.collect { onlinePeers ->
                _uiState.update { it.copy(onlinePeers = onlinePeers) }
                Logger.d("RecentChatsViewModel -> ${onlinePeers.size} online peers")
            }
        }
    }

    /**
     * Loads chats with search support.
     * When search query is blank, returns all chats.
     * When search query has text, returns filtered results.
     * Debounced at 300ms to avoid excessive DB queries.
     */
    private fun loadChatsWithSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        chatRepository.getAllChats()
                    } else {
                        chatRepository.searchChats(query.trim())
                    }
                }
                .catch { e ->
                    Logger.e("RecentChatsViewModel -> Failed to load chats", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error occurred"
                        )
                    }
                }
                .collect { chats ->
                    _uiState.update {
                        it.copy(
                            chats = chats,
                            isLoading = false,
                            error = null
                        )
                    }
                    Logger.d("RecentChatsViewModel -> Loaded ${chats.size} chats")
                }
        }
    }

    /**
     * Update the search query. Triggers debounced search.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Delete a chat by peer ID.
     * Room Flow will automatically emit the updated list after deletion.
     */
    fun deleteChat(peerId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(peerId)
        }
    }

    /**
     * Clear error state. The underlying Flow will re-emit automatically.
     * This is called when the user taps "Retry" after an error.
     */
    fun retryLoad() {
        _uiState.update { it.copy(error = null, isLoading = false) }
    }

    /**
     * Mark a chat as read, resetting its unread count to 0.
     * Called when the user opens a chat.
     */
    fun markChatAsRead(peerId: String) {
        viewModelScope.launch {
            chatRepository.markChatAsRead(peerId)
        }
    }
}
