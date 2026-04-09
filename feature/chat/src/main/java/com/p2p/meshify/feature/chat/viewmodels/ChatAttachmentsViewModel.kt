package com.p2p.meshify.feature.chat.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.ui.model.StagedAttachment
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.feature.chat.state.ChatAttachmentsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val ATTACHMENT_CACHE_MAX_SIZE = 200

/**
 * ViewModel responsible for attachment staging, upload progress tracking,
 * media sending, and attachment caching.
 *
 * Extracted from the monolithic ChatViewModel to reduce coupling and improve testability.
 * Handles:
 * - Attachment staging (add, remove, clear)
 * - Image sending (from staged attachments)
 * - Video sending (from staged attachments)
 * - Grouped/album message sending
 * - File upload with progress tracking
 * - Upload cancellation
 * - LRU cache for message attachments
 * - Getting attachments for a message (group)
 */
class ChatAttachmentsViewModel(
    private val context: Context,
    private val chatRepository: ChatRepositoryImpl,
    private val peerId: String,
    private val peerName: String
) : ViewModel() {

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow(ChatAttachmentsUiState())
    val uiState: StateFlow<ChatAttachmentsUiState> = _uiState.asStateFlow()

    // ==================== Attachment Staging ====================

    private val stageMutex = Mutex()

    /**
     * Stages an attachment for sending.
     * Limits to 10 attachments at a time.
     *
     * @param uri The URI of the attachment.
     * @param bytes The raw bytes of the attachment.
     * @param type The type of the attachment (IMAGE, VIDEO, FILE, etc.).
     */
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

    /**
     * Removes a staged attachment by URI.
     *
     * @param uri The URI of the attachment to remove.
     */
    fun removeStagedAttachment(uri: Uri) {
        viewModelScope.launch {
            stageMutex.withLock {
                val updated = _uiState.value.stagedAttachments.filter { it.uri != uri }
                _uiState.update { it.copy(stagedAttachments = updated) }
            }
        }
    }

    /**
     * Clears all staged attachments.
     */
    fun clearStagedAttachments() {
        viewModelScope.launch {
            stageMutex.withLock {
                _uiState.update { it.copy(stagedAttachments = emptyList()) }
            }
        }
    }

    // ==================== Image & Video Sending ====================

    /**
     * Sends an image message directly (without staging).
     *
     * @param bytes The raw image bytes.
     * @param extension The file extension (e.g., "jpg", "png").
     * @param replyToId Optional message ID to reply to.
     */
    fun sendImage(bytes: ByteArray, extension: String, replyToId: String? = null) {
        viewModelScope.launch {
            chatRepository.sendImage(peerId, peerName, bytes, extension, replyToId)
        }
    }

    /**
     * Sends a video message directly (without staging).
     *
     * @param bytes The raw video bytes.
     * @param extension The file extension (e.g., "mp4", "avi").
     * @param replyToId Optional message ID to reply to.
     */
    fun sendVideo(bytes: ByteArray, extension: String, replyToId: String? = null) {
        viewModelScope.launch {
            chatRepository.sendVideo(peerId, peerName, bytes, extension, replyToId)
        }
    }

    /**
     * Sends a grouped message with multiple attachments (album).
     *
     * @param caption Optional caption text for the grouped message.
     * @param attachments List of attachment byte arrays with their types.
     * @param replyToId Optional message ID to reply to.
     */
    fun sendGroupedMessage(
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            chatRepository.sendGroupedMessage(peerId, peerName, caption, attachments, replyToId)
        }
    }

    // ==================== File Upload with Progress ====================

    /**
     * Sends a file with progress tracking.
     * Used for large files where upload progress should be shown to the user.
     *
     * @param messageId Unique ID for this message (generated before calling).
     * @param file The file to upload.
     * @param fileType The type of file (IMAGE, VIDEO, FILE, etc.).
     * @param caption Optional caption for the file.
     * @param replyToId Optional message ID to reply to.
     */
    fun sendFileWithProgress(
        messageId: String,
        file: File,
        fileType: MessageType,
        caption: String = "",
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Initialize progress to 0
                _uploadProgress.update { current ->
                    current + (messageId to 0)
                }

                // Send file via repository with progress callback
                val result = chatRepository.sendFileWithProgress(
                    messageId = messageId,
                    peerId = peerId,
                    peerName = peerName,
                    file = file,
                    fileType = fileType,
                    caption = caption,
                    replyToId = replyToId,
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
                        it.copy(
                            uploadError = context.getString(
                                R.string.error_file_send_failed,
                                error.message ?: context.getString(R.string.error_unknown)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("ChatAttachmentsViewModel -> File upload exception", e)
                _uploadProgress.update { current ->
                    current - messageId
                }
                // Show error to user
                _uiState.update {
                    it.copy(
                        uploadError = context.getString(
                            R.string.error_message_send_failed,
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    /**
     * Cancels an ongoing file upload.
     * Removes the progress indicator from the UI.
     *
     * @param messageId The ID of the message whose upload to cancel.
     */
    fun cancelUpload(messageId: String) {
        _uploadProgress.update { current ->
            current - messageId
        }
        // Note: Actual cancellation logic would need to be implemented in repository
        Logger.d("ChatAttachmentsViewModel -> Upload cancelled for messageId: $messageId")
    }

    // ==================== Upload Progress ====================

    /**
     * Upload progress tracking - maps messageId to progress percentage (0-100).
     * Throttled to 10 updates/second to avoid unnecessary recompositions.
     */
    @OptIn(FlowPreview::class)
    private val _uploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    @OptIn(FlowPreview::class)
    val uploadProgress: StateFlow<Map<String, Int>> = _uploadProgress
        .sample(100.milliseconds)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // ==================== Attachment Cache ====================

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
     *
     * @param groupId The message group ID to fetch attachments for.
     * @return List of attachments for the message.
     */
    suspend fun getAttachmentsForMessage(groupId: String): List<MessageAttachmentEntity> {
        // Check cache first (thread-safe via synchronized)
        synchronized(attachmentsCache) {
            attachmentsCache[groupId]?.let { return it }
        }
        // Not cached — fetch from DB
        val result = withContext(Dispatchers.IO) {
            chatRepository.getMessageAttachments(groupId)
        }
        // Store in cache
        synchronized(attachmentsCache) {
            attachmentsCache[groupId] = result
        }
        return result
    }

    // ==================== State Clearing ====================

    /**
     * Clears the upload error message after it has been shown.
     */
    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }
}
