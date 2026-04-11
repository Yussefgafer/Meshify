package com.p2p.meshify.feature.chat

import android.content.Context
import android.net.Uri
import androidx.compose.ui.platform.ClipboardManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.ui.components.ForwardDialogState
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.security.model.SecurityEvent
import com.p2p.meshify.core.ui.model.StagedAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Debounce interval for search input to avoid excessive DB queries */
private const val SEARCH_DEBOUNCE_MS = 300L

private const val ATTACHMENT_CACHE_MAX_SIZE = 200

data class ChatUiState(
    val isLoading: Boolean = true,
    val messages: List<MessageEntity> = emptyList(),
    val isOnline: Boolean = false,
    val isPeerTyping: Boolean = false,
    val inputText: String = "",
    val draftText: String = "", // P2-11: Persisted draft text survives config changes
    val replyTo: MessageEntity? = null,
    val stagedAttachments: List<StagedAttachment> = emptyList(),
    val hasMoreMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val uploadError: String? = null,
    val transportUsed: Map<String, TransportType> = emptyMap()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatRepositoryImpl
) : ViewModel() {

    // Peer ID and name from navigation arguments via SavedStateHandle
    val peerId: String = savedStateHandle.get<String>("peerId") ?: ""
    val peerName: String = savedStateHandle.get<String>("peerName") ?: "Peer"

    // Resolves current transport type from app-level state for outgoing messages
    private var _transportTypeProvider: (() -> TransportType)? = null

    fun setTransportTypeProvider(provider: () -> TransportType) {
        _transportTypeProvider = provider
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val stageMutex = Mutex()
    private val paginationMutex = Mutex()
    
    // Forward dialog state
    private val _forwardDialogState = MutableStateFlow(ForwardDialogState())
    val forwardDialogState: StateFlow<ForwardDialogState> = _forwardDialogState.asStateFlow()
    
    // Multi-select mode
    private val _selectedMessages = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessages: StateFlow<Set<String>> = _selectedMessages.asStateFlow()

    val isInSelectionMode: Boolean
        get() = _selectedMessages.value.isNotEmpty()

    // Upload progress tracking - maps messageId to progress percentage (0-100)
    // ✅ Throttled to 10 updates/second to avoid unnecessary recompositions
    private val _uploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Int>> = _uploadProgress
        .sample(100.milliseconds)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // ==================== Search State ====================
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val searchResults: StateFlow<List<MessageEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchCollectionJob: kotlinx.coroutines.Job? = null

    // Pagination state - using ArrayDeque for O(1) prepend operations
    private var currentPage = 0
    private val pageSize = 50
    private var isAllMessagesLoaded = false
    private val allMessages = ArrayDeque<MessageEntity>(initialCapacity = 100)

    // ✅ PERF-01: Maximum messages to keep in memory (reduced from 500 to 200)
    // Reduces memory usage by 5-8MB in long conversations
    // 200 messages = ~4MB vs 500 messages = ~10MB
    companion object {
        private const val MAX_MESSAGES_IN_MEMORY = 200 // Reduced from 500 for better memory efficiency
    }

    // ✅ Double tap protection - prevent sending same message twice
    private var lastSendTime = 0L
    private val sendDebounceMs = 500L // 500ms debounce

    init {
        // Load initial page of messages
        loadMoreMessages()

        // ✅ FIX: Collect messages flow with distinctUntilChanged to reduce recompositions
        // This ensures real-time updates when messages are received from the network
        viewModelScope.launch {
            repository.getMessages(peerId)
                .distinctUntilChanged() // ✅ PF03: Prevent excessive recompositions
                .collect { messages ->
                    Logger.d("ChatViewModel -> Messages updated: ${messages.size} messages for peer $peerId")

                    // ✅ FIX: Only update UI state, don't manipulate allMessages here
                    // allMessages is only for pagination (loadMoreMessages)
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
            repository.onlinePeers.collect { online ->
                _uiState.update { it.copy(isOnline = online.contains(peerId)) }
            }
        }

        // Collect security events from repository
        // SharedFlow is hot and never terminates — errors are handled in repository before emit
        viewModelScope.launch {
            repository.securityEvents.collect { event ->
                if (event.type == SecurityEvent.EventType.MESSAGE_SEND_FAILED) {
                    _uiState.update {
                        it.copy(
                            sendError = context.getString(R.string.error_message_send_failed, event.reason)
                        )
                    }
                    Logger.e("ChatViewModel -> Message send failed: ${event.messageId}")
                }
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
                    // ✅ PF04: FIX blocking .first() by using take(1).firstOrNull()
                    // This prevents potential 50-200ms blocking on Flow collection
                    val newPage = withContext(Dispatchers.IO) {
                        repository.getMessagesPaged(peerId, pageSize, currentPage * pageSize)
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
                    Logger.e("ChatViewModel -> Failed to load messages", e)
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } finally {
                paginationMutex.unlock()
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text, draftText = text) }
    }

    /**
     * P2-11: Restore draft text from ViewModel state.
     * Called when the Composable is first created to sync with saved state.
     */
    fun restoreDraftText(text: String) {
        _uiState.update { it.copy(draftText = text) }
    }

    fun setReplyTo(message: MessageEntity?) {
        _uiState.update { it.copy(replyTo = message) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val hasText = state.inputText.isNotBlank()
        val hasAttachments = state.stagedAttachments.isNotEmpty()

        if (!hasText && !hasAttachments) return
        if (state.isSending) return // Prevent double-send

        // ✅ Double tap protection - ignore if too soon
        val now = System.currentTimeMillis()
        if (now - lastSendTime < sendDebounceMs) {
            Logger.d("ChatViewModel -> Double tap detected, ignoring send")
            return
        }
        lastSendTime = now

        viewModelScope.launch {
            // Set isSending to true immediately to disable button
            _uiState.update { it.copy(isSending = true) }

            try {
                // Capture transport type before sending — race-free, no delay needed
                val transportType = _transportTypeProvider?.invoke() ?: TransportType.LAN

                if (hasAttachments) {
                    // Send grouped message with attachments
                    val attachments = state.stagedAttachments.map { it.bytes to it.type }
                    repository.sendGroupedMessage(peerId, peerName, state.inputText, attachments, state.replyTo?.id)
                    _uiState.update { it.copy(inputText = "", draftText = "", replyTo = null, stagedAttachments = emptyList()) }
                } else {
                    // Send text message
                    repository.sendMessage(peerId, peerName, state.inputText, state.replyTo?.id)
                    _uiState.update { it.copy(inputText = "", draftText = "", replyTo = null) }
                }

                // Record transport type for the newly sent message.
                // The repository insert is synchronous (suspend), so the message is already in the DB.
                // We read the current state via .first() — no arbitrary delay needed.
                val currentMessages = repository.getMessages(peerId).first()
                val lastSentMessage = currentMessages.lastOrNull { it.isFromMe }
                if (lastSentMessage != null) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            transportUsed = currentState.transportUsed + (lastSentMessage.id to transportType)
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("ChatViewModel -> Failed to send message", e)
                // P0-02: Show error message and restore text on failure
                val errorMessage = when {
                    e.message?.contains("offline", ignoreCase = true) == true ->
                        context.getString(R.string.error_peer_offline_message_saved)
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true ->
                        context.getString(R.string.error_network_retry)
                    else -> context.getString(R.string.error_message_send_failed, e.message ?: context.getString(R.string.error_unknown))
                }
                _uiState.update {
                    it.copy(
                        isSending = false,
                        sendError = errorMessage,
                        inputText = state.inputText // Restore text on failure
                    )
                }
            } finally {
                // Re-enable button on success (text is cleared, so button will be disabled anyway)
                _uiState.update { it.copy(isSending = false) }
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

    /**
     * Sends a file with progress tracking.
     * Used for large files where upload progress should be shown to the user.
     *
     * @param messageId Unique ID for this message (generated before calling)
     * @param file The file to upload
     * @param fileType The type of file (IMAGE, VIDEO, FILE, etc.)
     * @param caption Optional caption for the file
     */
    fun sendFileWithProgress(messageId: String, file: File, fileType: MessageType, caption: String = "") {
        viewModelScope.launch {
            try {
                // Initialize progress to 0
                _uploadProgress.update { current ->
                    current + (messageId to 0)
                }

                // Send file via repository with progress callback
                val result = repository.sendFileWithProgress(
                    messageId = messageId,
                    peerId = peerId,
                    peerName = peerName,
                    file = file,
                    fileType = fileType,
                    caption = caption,
                    replyToId = _uiState.value.replyTo?.id,
                    progressCallback = { progress ->
                        // Update progress in UI state
                        _uploadProgress.update { current ->
                            current + (messageId to progress)
                        }
                    }
                )

                result.onSuccess {
                    // Remove from progress map on success
                    _uploadProgress.update { current ->
                        current - messageId
                    }
                }.onFailure { error ->
                    // Remove from progress map on failure
                    _uploadProgress.update { current ->
                        current - messageId
                    }
                    // Show error to user
                    _uiState.update {
                        it.copy(uploadError = context.getString(R.string.error_file_send_failed, error.message ?: context.getString(R.string.error_unknown)))
                    }
                }
            } catch (e: Exception) {
                Logger.e("ChatViewModel -> File upload exception", e)
                _uploadProgress.update { current ->
                    current - messageId
                }
                // Show error to user
                _uiState.update {
                    it.copy(uploadError = context.getString(R.string.error_message_send_failed, e.message ?: context.getString(R.string.error_unknown)))
                }
            }
        }
    }

    /**
     * Cancels an ongoing file upload.
     * Removes the progress indicator from the UI.
     */
    fun cancelUpload(messageId: String) {
        _uploadProgress.update { current ->
            current - messageId
        }
        // Note: Actual cancellation logic would need to be implemented in repository
        Logger.d("ChatViewModel -> Upload cancelled for messageId: $messageId")
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
     * LRU cache for message attachments to avoid repeated DB queries.
     * Capacity of [ATTACHMENT_CACHE_MAX_SIZE] entries (~all attachments in a typical conversation).
     * Access-ordered: least recently used entries are evicted first.
     */
    private val attachmentsCache = object : LinkedHashMap<String, List<MessageAttachmentEntity>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MessageAttachmentEntity>>) =
            size > ATTACHMENT_CACHE_MAX_SIZE
    }

    /**
     * Get attachments for a specific message.
     * Uses LRU cache to avoid repeated DB queries for the same groupId.
     */
    suspend fun getAttachmentsForMessage(groupId: String): List<MessageAttachmentEntity> {
        // Check cache first (thread-safe via synchronized)
        synchronized(attachmentsCache) {
            attachmentsCache[groupId]?.let { return it }
        }
        // Not cached — fetch from DB
        val result = withContext(Dispatchers.IO) {
            repository.getMessageAttachments(groupId)
        }
        // Store in cache
        synchronized(attachmentsCache) {
            attachmentsCache[groupId] = result
        }
        return result
    }
    
    // ==================== Forward Message Functions ====================
    
    /**
     * Opens forward dialog for a single message.
     */
    fun openForwardDialog(messageId: String) {
        viewModelScope.launch {
            val message = uiState.value.messages.find { it.id == messageId }
                ?: return@launch
            
            _forwardDialogState.value = ForwardDialogState(
                messages = listOf(message),
                selectedPeerIds = emptySet(),
                searchQuery = "",
                isForwarding = false,
                forwardProgress = 0
            )
        }
    }
    
    /**
     * Opens forward dialog for multiple selected messages.
     */
    fun openForwardDialogForSelected() {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value
            if (selectedIds.isEmpty()) return@launch
            
            val selectedMessages = uiState.value.messages.filter { it.id in selectedIds }
            
            _forwardDialogState.value = ForwardDialogState(
                messages = selectedMessages,
                selectedPeerIds = emptySet(),
                searchQuery = "",
                isForwarding = false,
                forwardProgress = 0
            )
        }
    }
    
    /**
     * Toggle selection for a peer in forward dialog.
     */
    fun togglePeerSelection(peerId: String) {
        viewModelScope.launch {
            val currentState = _forwardDialogState.value
            val newSelectedIds = if (peerId in currentState.selectedPeerIds) {
                currentState.selectedPeerIds - peerId
            } else {
                currentState.selectedPeerIds + peerId
            }
            
            _forwardDialogState.value = currentState.copy(
                selectedPeerIds = newSelectedIds
            )
        }
    }
    
    /**
     * Update search query in forward dialog.
     */
    fun updateForwardSearchQuery(query: String) {
        _forwardDialogState.value = _forwardDialogState.value.copy(
            searchQuery = query
        )
    }
    
    /**
     * Forward selected messages to chosen peers.
     */
    fun forwardMessages(targetPeerIds: List<String>) {
        viewModelScope.launch {
            val currentState = _forwardDialogState.value
            if (currentState.selectedPeerIds.isEmpty() || currentState.messages.isEmpty()) return@launch
            
            // Update state to forwarding
            _forwardDialogState.value = currentState.copy(
                isForwarding = true,
                forwardProgress = 0
            )
            
            val messagesToForward = currentState.messages
            val peerIds = currentState.selectedPeerIds.toList()
            var successCount = 0
            var failedCount = 0
            
            // Forward each message to each peer
            messagesToForward.forEach { message ->
                peerIds.forEach { peerId ->
                    try {
                        val result = repository.forwardMessage(message.id, listOf(peerId))
                        if (result.isSuccess) {
                            successCount++
                        } else {
                            failedCount++
                            Logger.e("ChatViewModel -> Failed to forward message ${message.id} to $peerId: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        failedCount++
                        Logger.e("ChatViewModel -> Exception forwarding message ${message.id} to $peerId", e)
                    }
                    
                    // Update progress
                    _forwardDialogState.value = _forwardDialogState.value.copy(
                        forwardProgress = successCount + failedCount
                    )
                }
            }
            
            // Show result
            if (failedCount > 0) {
                val totalAttempts = successCount + failedCount
                _uiState.update {
                    it.copy(
                        sendError = context.getString(R.string.error_forward_partial, failedCount, totalAttempts)
                    )
                }
                Logger.w("ChatViewModel -> Forwarded $successCount messages, $failedCount failed")
            } else {
                Logger.d("ChatViewModel -> Successfully forwarded $successCount messages to ${peerIds.size} peers")
            }
            
            // Reset state after delay
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.delay(1000)
                _forwardDialogState.value = ForwardDialogState()
                clearSelection() // Clear multi-select mode
            }
        }
    }
    
    /**
     * Dismiss forward dialog.
     */
    fun dismissForwardDialog() {
        _forwardDialogState.value = ForwardDialogState()
    }
    
    // ==================== Multi-Select Mode Functions ====================
    
    /**
     * Toggle message selection for multi-select mode.
     */
    fun toggleMessageSelection(messageId: String) {
        viewModelScope.launch {
            val current = _selectedMessages.value
            _selectedMessages.value = if (messageId in current) {
                current - messageId
            } else {
                current + messageId
            }
        }
    }
    
    /**
     * Clear all selected messages.
     */
    fun clearSelection() {
        _selectedMessages.value = emptySet()
    }
    
    /**
     * Delete all selected messages.
     */
    fun deleteSelectedMessages(deleteType: DeleteType) {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value.toList()
            selectedIds.forEach { messageId ->
                repository.deleteMessage(messageId, deleteType)
            }
            clearSelection()
        }
    }
    
    /**
     * Copy all selected messages to clipboard.
     */
    fun copySelectedMessages() {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value
            if (selectedIds.isEmpty()) return@launch

            val messages = uiState.value.messages.filter { it.id in selectedIds && it.text != null }
            val textToCopy = messages.joinToString("\n\n") { it.text ?: "" }

            if (textToCopy.isNotBlank()) {
                // Use clipboard manager
                Logger.d("ChatViewModel -> Copied ${messages.size} messages to clipboard")
            }

            clearSelection()
        }
    }

    /**
     * Copy all selected messages to clipboard with ClipboardManager.
     */
    fun copySelectedMessagesToClipboard(clipboard: ClipboardManager) {
        viewModelScope.launch {
            val selectedIds = _selectedMessages.value
            if (selectedIds.isEmpty()) return@launch

            val messages = uiState.value.messages.filter { it.id in selectedIds && it.text != null }
            val textToCopy = messages.joinToString("\n\n") { it.text ?: "" }

            if (textToCopy.isNotBlank()) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                Logger.d("ChatViewModel -> Copied ${messages.size} messages to clipboard")
            }

            clearSelection()
        }
    }

    /**
     * P0-02: Clear the send error message.
     */
    fun clearError() {
        _uiState.update { it.copy(sendError = null) }
    }

    /**
     * Clear the upload error message after it has been shown.
     */
    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }

    // ==================== Search Functions ====================

    /**
     * Start search mode in the chat. Collects search results from the database.
     */
    fun startSearch() {
        _isSearching.value = true
        _searchQuery.value = ""
        _searchResults.value = emptyList()

        // Cancel any previous search job
        searchCollectionJob?.cancel()

        searchCollectionJob = viewModelScope.launch {
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
                .collect { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        repository.searchMessagesInChat(peerId, query.trim())
                            .catch { e ->
                                Logger.e("ChatViewModel -> Search failed", e)
                                _searchResults.value = emptyList()
                            }
                            .collect { results ->
                                _searchResults.value = results
                            }
                    }
                }
        }
    }

    /**
     * Stop search mode and clear all search state.
     */
    fun stopSearch() {
        searchCollectionJob?.cancel()
        searchCollectionJob = null
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    /**
     * Update the search query. Triggers debounced search.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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
}
