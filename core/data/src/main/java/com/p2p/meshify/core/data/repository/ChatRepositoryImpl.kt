package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.model.DeleteRequest
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.ReactionUpdate
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * ChatRepositoryImpl - Facade pattern for chat operations.
 *
 * This class now delegates to specialized repositories following Single Responsibility Principle:
 * - [messageRepository]: Sending/receiving messages
 * - [chatManagementRepository]: Chat CRUD operations
 * - [pendingMessageRepository]: Pending message queue management
 * - [messageAttachmentRepository]: Message attachments (albums)
 * - [reactionRepository]: Message reactions
 *
 * This facade maintains backward compatibility with existing code while improving maintainability.
 */
class ChatRepositoryImpl(
    private val context: android.content.Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: com.p2p.meshify.core.data.local.dao.PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: ISettingsRepository
) : IChatRepository {

    // Specialized repositories
    private val messageRepository: MessageRepository = MessageRepository(
        messageDao = messageDao,
        chatDao = chatDao,
        pendingMessageDao = pendingMessageDao,
        transportManager = transportManager,
        fileManager = fileManager,
        settingsRepository = settingsRepository
    )

    private val chatManagementRepository: ChatManagementRepository = ChatManagementRepository(
        context = context,
        chatDao = chatDao,
        messageDao = messageDao
    )

    private val pendingMessageRepository: PendingMessageRepository = PendingMessageRepository(
        pendingMessageDao = pendingMessageDao,
        messageDao = messageDao,
        transportManager = transportManager,
        settingsRepository = settingsRepository
    )

    private val messageAttachmentRepository: MessageAttachmentRepository = MessageAttachmentRepository(
        messageDao = messageDao,
        fileManager = fileManager
    )

    private val reactionRepository: ReactionRepository = ReactionRepository(
        messageDao = messageDao,
        transportManager = transportManager,
        settingsRepository = settingsRepository
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val payloadMutex = Mutex()

    companion object {
        private const val PAYLOAD_HANDLING_TIMEOUT_MS = 30000L // 30 seconds
    }

    // Public DAO access methods for ViewModels (backward compatibility)
    fun getAllChats(): Flow<List<ChatEntity>> = chatManagementRepository.getAllChats()
    fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageRepository.getMessages(chatId)
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> =
        messageRepository.getMessagesPaged(chatId, limit, offset)

    // Online peers flow (backward compatibility)
    override val onlinePeers: Flow<Set<String>> = transportManager.getAllEventsFlow()
        .map { event ->
            transportManager.getAllTransports().flatMap { it.onlinePeers.value }.toSet()
        }
    override val typingPeers: Flow<Set<String>> = transportManager.getAllEventsFlow()
        .map { event ->
            transportManager.getAllTransports().flatMap { it.typingPeers.value }.toSet()
        }

    // ==================== Message Sending ====================

    override suspend fun sendMessage(
        peerId: String,
        peerName: String,
        text: String,
        replyToId: String?
    ): Result<Unit> {
        return messageRepository.sendTextMessage(peerId, peerName, text, replyToId)
    }

    override suspend fun sendImage(
        peerId: String,
        peerName: String,
        imageBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        return messageRepository.sendImageMessage(peerId, peerName, imageBytes, extension, replyToId)
    }

    override suspend fun sendVideo(
        peerId: String,
        peerName: String,
        videoBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        return messageRepository.sendVideoMessage(peerId, peerName, videoBytes, extension, replyToId)
    }

    override suspend fun sendFileWithProgress(
        messageId: String,
        peerId: String,
        peerName: String,
        file: File,
        fileType: MessageType,
        caption: String,
        replyToId: String?,
        progressCallback: ((Int) -> Unit)?
    ): Result<Unit> {
        return messageRepository.sendFileWithProgress(
            messageId = messageId,
            peerId = peerId,
            peerName = peerName,
            file = file,
            fileType = fileType,
            caption = caption,
            replyToId = replyToId,
            progressCallback = progressCallback
        )
    }

    override suspend fun sendGroupedMessage(
        peerId: String,
        peerName: String,
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        replyToId: String?
    ): Result<Unit> {
        if (attachments.isEmpty()) return Result.failure(Exception("No attachments"))

        val messageId: String = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()

        // Create parent message with caption
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = caption.ifBlank { null },
            mediaPath = null,
            type = if (attachments.all { it.second == MessageType.VIDEO }) MessageType.VIDEO else MessageType.IMAGE,
            timestamp = timestamp,
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId,
            groupId = messageId
        )

        // Save chat
        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, caption.ifBlank { "[Album]" }, timestamp))

        // Save message
        messageDao.insertMessage(message)

        // Save attachments
        val saveResult = messageAttachmentRepository.saveAttachments(messageId, attachments)
        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save attachments"))
        }

        // Send first attachment as representative
        val firstAttachmentBytes = attachments.firstOrNull()?.first
            ?: return Result.failure(Exception("No attachments to send"))
        
        return messageRepository.sendFileMessage(
            peerId = peerId,
            peerName = peerName,
            fileBytes = firstAttachmentBytes,
            fileName = "Album: $caption",
            fileType = message.type,
            replyToId = replyToId
        )
    }

    // ==================== Chat Management ====================

    override suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

        return try {
            if (deleteType == DeleteType.DELETE_FOR_ME) {
                messageDao.markAsDeletedForMe(messageId)
            } else {
                messageDao.markAsDeletedForEveryone(messageId, System.currentTimeMillis(), message.senderId)
                
                // Send delete request to peer
                val request = DeleteRequest(messageId, deleteType, message.senderId)
                val payload = Payload(
                    senderId = message.senderId,
                    type = Payload.PayloadType.DELETE_REQUEST,
                    data = Json.encodeToString(request).toByteArray()
                )
                val transport = transportManager.selectBestTransport(message.chatId)
                    ?: return Result.failure(Exception("No available transport"))
                transport.sendPayload(message.chatId, payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to delete message: $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(peerId: String) {
        chatManagementRepository.deleteChat(peerId)
    }

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

        // Validate target list is not empty
        if (targetPeerIds.isEmpty()) {
            return Result.failure(Exception("No target peers specified"))
        }

        return try {
            val results = coroutineScope {
                targetPeerIds.map { peerId ->
                    async {
                        try {
                            val chat = chatDao.getChatById(peerId)
                            val forwardContext = buildForwardContext(message)
                            var success = false

                            if (message.type == MessageType.TEXT && message.text != null) {
                                success = forwardTextMessage(message, peerId, forwardContext)
                            } else {
                                success = forwardMediaContext(message, peerId, forwardContext)
                            }

                            // Update chat last message ONLY if successful
                            if (success) {
                                updateChatLastMessage(peerId, chat, forwardContext)
                            }

                            Pair(peerId, if (success) Result.success(Unit) else Result.failure(Exception("Forward failed")))
                        } catch (e: Exception) {
                            Logger.e("ChatRepository -> Failed to forward message to $peerId", e)
                            Pair(peerId, Result.failure<Unit>(e))
                        }
                    }
                }.awaitAll()
            }

            val failures = results.filter { it.second.isFailure }
            if (failures.isNotEmpty()) {
                Logger.e("ChatRepository -> Forward completed with ${failures.size} failures out of ${results.size}")
                Result.failure(Exception("${failures.size} of ${results.size} forwards failed"))
            } else {
                Logger.d("ChatRepository -> All ${results.size} forwards succeeded")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward message: $messageId", e)
            Result.failure(e)
        }
    }

    private suspend fun forwardTextMessage(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        val newMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = peerId,
            senderId = message.senderId,
            text = forwardContext,
            mediaPath = null,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.SENDING,
            replyToId = null,
            groupId = null
        )

        messageDao.insertMessage(newMessage)

        val transport = transportManager.selectBestTransport(peerId)
        if (transport == null) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            Logger.w("ChatRepository -> No transport available for forwarding to $peerId")
            return false
        }

        val payload = Payload(
            id = newMessage.id,
            senderId = settingsRepository.getDeviceId(),
            timestamp = newMessage.timestamp,
            type = Payload.PayloadType.TEXT,
            data = (newMessage.text ?: "").toByteArray()
        )

        return try {
            val sendResult = transport.sendPayload(peerId, payload)
            if (sendResult.isSuccess) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)
                Logger.d("ChatRepository -> Forwarded message ${message.id} to $peerId")
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("ChatRepository -> Failed to forward message to $peerId")
                false
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            Logger.e("ChatRepository -> Failed to forward message to $peerId", e)
            false
        }
    }

    private suspend fun forwardMediaContext(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        return try {
            // Step 1: Validate media path
            val mediaPath = message.mediaPath
            if (mediaPath == null) {
                Logger.e("ChatRepository -> Cannot forward media: mediaPath is null")
                return false
            }

            // Step 2: Read media file from disk
            val mediaFile = File(mediaPath)
            if (!mediaFile.exists()) {
                Logger.e("ChatRepository -> Media file does not exist: ${mediaFile.name}")
                return false
            }

            // CRITICAL: Validate file size (100MB limit to prevent OOM)
            val fileSize = mediaFile.length()
            val maxFileSize = AppConstants.MAX_FILE_SIZE_BYTES
            if (fileSize > maxFileSize) {
                val maxFileSizeMb = AppConstants.MAX_FILE_SIZE_BYTES / 1024 / 1024
                Logger.e("ChatRepository -> File size exceeds ${maxFileSizeMb}MB limit: ${fileSize / 1024 / 1024}MB")
                return false
            }

            // Step 3: Read media bytes on IO dispatcher
            val mediaBytes = withContext(Dispatchers.IO) {
                mediaFile.readBytes()
            }

            // Step 4: Determine PayloadType
            val payloadType = when (message.type) {
                MessageType.IMAGE -> Payload.PayloadType.FILE
                MessageType.VIDEO -> Payload.PayloadType.VIDEO
                MessageType.FILE, MessageType.APK -> Payload.PayloadType.FILE
                else -> {
                    Logger.e("ChatRepository -> Unsupported media type: ${message.type}")
                    return false
                }
            }

            // Step 5: Create new message entity
            val newMessage = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = message.senderId,
                text = forwardContext,
                mediaPath = null,
                type = message.type,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                status = MessageStatus.SENDING,
                replyToId = null,
                groupId = null
            )

            // Step 6: Save to database
            messageDao.insertMessage(newMessage)

            // Step 7: Get transport
            val transport = transportManager.selectBestTransport(peerId)
            if (transport == null) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.w("ChatRepository -> No transport available for forwarding to ${peerId.take(8)}")
                return false
            }

            // Step 8: Create payload
            val payload = Payload(
                id = newMessage.id,
                senderId = settingsRepository.getDeviceId(),
                timestamp = newMessage.timestamp,
                type = payloadType,
                data = mediaBytes
            )

            // Step 9: Send payload with try-catch
            val sendResult = try {
                transport.sendPayload(peerId, payload)
            } catch (e: Exception) {
                Logger.e("ChatRepository -> sendPayload threw exception for ${peerId.take(8)}", e)
                Result.failure(e)
            }

            return if (sendResult.isSuccess) {
                // Step 10: Save media file for the new message BEFORE updating status
                val fileName = "forwarded_${newMessage.id}.${mediaFile.extension}"
                val savedPath = withContext(Dispatchers.IO) {
                    fileManager.saveMedia(fileName, mediaBytes)
                }

                // Step 11: Update database with saved path
                if (savedPath != null) {
                    messageDao.insertMessage(newMessage.copy(mediaPath = savedPath))
                }

                // Step 12: Update status to SENT (AFTER file is saved)
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)

                Logger.d("ChatRepository -> Forwarded media message ${message.id.take(8)} to ${peerId.take(8)}")
                true
            } else {
                // Step 13: Update status to QUEUED
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("ChatRepository -> Failed to forward media message to ${peerId.take(8)}")
                false
            }
        } catch (e: java.io.IOException) {
            Logger.e("ChatRepository -> IO error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: SecurityException) {
            Logger.e("ChatRepository -> Security error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward media context to ${peerId.take(8)}", e)
            false
        }
    }

    private suspend fun updateChatLastMessage(
        peerId: String,
        existingChat: ChatEntity?,
        forwardContext: String
    ) {
        if (existingChat != null) {
            chatDao.insertChat(existingChat.copy(
                lastMessage = forwardContext,
                lastTimestamp = System.currentTimeMillis()
            ))
        } else {
            chatDao.insertChat(ChatEntity(
                peerId = peerId,
                peerName = "${AppConstants.DEFAULT_PEER_NAME_PREFIX}${peerId.take(4)}",
                lastMessage = forwardContext,
                lastTimestamp = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Builds context string for forwarded message.
     */
    private fun buildForwardContext(original: MessageEntity): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(original.timestamp))

        return when (original.type) {
            MessageType.TEXT -> {
                val preview = original.text?.take(100) ?: ""
                "Forwarded from $timestamp:\n$preview"
            }
            MessageType.IMAGE -> "Forwarded image from $timestamp"
            MessageType.VIDEO -> "Forwarded video from $timestamp"
            MessageType.AUDIO -> "Forwarded audio from $timestamp"
            MessageType.FILE -> "Forwarded file from $timestamp"
            MessageType.DOCUMENT -> "Forwarded document from $timestamp"
            MessageType.ARCHIVE -> "Forwarded archive from $timestamp"
            MessageType.APK -> "Forwarded APK from $timestamp"
        }
    }

    // ==================== Reactions ====================

    override suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> {
        return reactionRepository.addReaction(messageId, reaction)
    }

    // ==================== System Commands ====================

    override suspend fun sendSystemCommand(peerId: String, command: String) {
        val myId = settingsRepository.getDeviceId()
        val payload = Payload(
            senderId = myId,
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = command.toByteArray()
        )
        val transport = transportManager.selectBestTransport(peerId)
            ?: throw IllegalStateException("No available transport for peer: $peerId")
        transport.sendPayload(peerId, payload)
    }

    // ==================== Incoming Payload Handling ====================

    override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
        try {
            withTimeout(PAYLOAD_HANDLING_TIMEOUT_MS) {
                payloadMutex.withLock {
                    processPayload(peerId, payload)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.e("ChatRepository -> handleIncomingPayload timeout after ${PAYLOAD_HANDLING_TIMEOUT_MS}ms", e)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> handleIncomingPayload failed", e)
        }
    }

    private suspend fun processPayload(peerId: String, payload: Payload) {
        Logger.d("ChatRepository -> Processing payload from $peerId, type=${payload.type}")

        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> {
                val command = String(payload.data)
                if (command.startsWith("ACK_")) {
                    val messageId = command.removePrefix("ACK_")
                    Logger.d("ChatRepository -> Received ACK for message $messageId")
                    messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
                }
            }
            Payload.PayloadType.DELETE_REQUEST -> {
                val req = Json.decodeFromString<DeleteRequest>(String(payload.data))
                Logger.d("ChatRepository -> Received delete request for message ${req.messageId}")
                messageDao.markAsDeletedForEveryone(req.messageId, req.deletedAt, req.deletedBy)
            }
            Payload.PayloadType.REACTION -> {
                val update = Json.decodeFromString<ReactionUpdate>(String(payload.data))
                Logger.d("ChatRepository -> Received reaction ${update.reaction} for message ${update.messageId}")
                messageDao.updateReaction(update.messageId, update.reaction)
            }
            Payload.PayloadType.TEXT -> {
                val text = String(payload.data)
                Logger.i("ChatRepository -> Received text message from $peerId")
                saveIncomingMessage(peerId, text, null, MessageType.TEXT, payload.timestamp, payload.id)
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            }
            Payload.PayloadType.FILE -> {
                val fileName = "img_${payload.id}.jpg"
                Logger.i("ChatRepository -> Receiving image from $peerId (${payload.data.size} bytes)")
                val savedPath = fileManager.saveMedia(fileName, payload.data)
                if (savedPath != null) {
                    val file = File(savedPath)
                    if (file.exists()) {
                        Logger.d("ChatRepository -> Incoming image saved: $savedPath")
                        saveIncomingMessage(peerId, null, savedPath, MessageType.IMAGE, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                    } else {
                        Logger.e("ChatRepository -> Saved incoming image file does not exist: $savedPath")
                    }
                } else {
                    Logger.e("ChatRepository -> Failed to save incoming image: $fileName")
                }
            }
            Payload.PayloadType.VIDEO -> {
                val fileName = "vid_${payload.id}.mp4"
                Logger.i("ChatRepository -> Receiving video from $peerId (${payload.data.size} bytes)")
                val savedPath = fileManager.saveMedia(fileName, payload.data)
                if (savedPath != null) {
                    val file = File(savedPath)
                    if (file.exists()) {
                        Logger.d("ChatRepository -> Incoming video saved: $savedPath")
                        saveIncomingMessage(peerId, null, savedPath, MessageType.VIDEO, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                    } else {
                        Logger.e("ChatRepository -> Saved incoming video file does not exist: $savedPath")
                    }
                } else {
                    Logger.e("ChatRepository -> Failed to save incoming video: $fileName")
                }
            }
            Payload.PayloadType.HANDSHAKE -> {
                val rawData = String(payload.data)
                val cleanName = parseName(rawData)
                chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))

                // Retry pending messages in background
                scope.launch {
                    retryPendingMessages(payload.senderId)
                }
            }
            else -> Logger.w("ChatRepository -> Unknown payload type: ${payload.type}")
        }
    }

    private suspend fun saveIncomingMessage(
        peerId: String,
        text: String?,
        mediaPath: String?,
        type: MessageType,
        timestamp: Long,
        messageId: String
    ) {
        try {
            val existingChat = chatDao.getChatById(peerId)
            val finalName = if (existingChat != null) parseName(existingChat.peerName) else "${AppConstants.DEFAULT_PEER_NAME_PREFIX}${peerId.take(4)}"
            chatDao.insertChat(ChatEntity(peerId, finalName, text ?: "[Media]", timestamp))
            val message = MessageEntity(
                id = messageId,
                chatId = peerId,
                senderId = peerId,
                text = text,
                mediaPath = mediaPath,
                type = type,
                timestamp = timestamp,
                isFromMe = false,
                status = MessageStatus.SENT
            )
            messageDao.insertMessage(message)
            notificationHelper.showMessageNotification(finalName, message)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to save incoming message", e)
        }
    }

    private fun parseName(raw: String): String {
        return if (raw.contains("name")) {
             try { Json.decodeFromString<Handshake>(raw).name } catch(e:Exception) { raw.take(20) }
        } else raw.removePrefix("HELO_")
    }

    // ==================== Pending Messages ====================

    override suspend fun retryPendingMessages(peerId: String): Result<Unit> {
        return pendingMessageRepository.retryPendingMessages(peerId)
    }

    // ==================== Attachments (Backward Compatibility) ====================

    suspend fun getMessageAttachments(messageId: String): List<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity> {
        return messageAttachmentRepository.getAttachmentsForMessage(messageId)
    }
}
