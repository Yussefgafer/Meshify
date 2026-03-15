package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.util.ImageCompressor
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.model.DeleteRequest
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.PayloadTypeFromString
import com.p2p.meshify.domain.model.ReactionUpdate
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random

/**
 * Chat Repository Implementation with robust error handling.
 * 
 * Improvements:
 * - Timeout for Mutex operations to prevent deadlocks
 * - Improved file reading error handling with existence checks
 * - Exponential backoff retry logic for pending messages
 * - Result<T> returns for better error propagation
 */
class ChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: PendingMessageDao,
    private val meshTransport: IMeshTransport,
    private val fileManager: IFileManager,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: ISettingsRepository
) : IChatRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val payloadMutex = Mutex()

    companion object {
        private const val PAYLOAD_HANDLING_TIMEOUT_MS = 30000L // 30 seconds
        private const val RETRY_MAX_ATTEMPTS = 5
        private const val RETRY_BASE_DELAY_MS = 1000L // 1 second base
        private const val RETRY_MAX_DELAY_MS = 30000L // 30 seconds max
    }

    // Public DAO access methods for ViewModels
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()
    fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getAllMessagesForChat(chatId)
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> =
        messageDao.getMessagesPaged(chatId, limit, offset)

    override val onlinePeers: Flow<Set<String>> = meshTransport.onlinePeers
    override val typingPeers: Flow<Set<String>> = meshTransport.typingPeers

    override suspend fun sendSystemCommand(peerId: String, command: String) {
        val myId = settingsRepository.getDeviceId()
        val payload = Payload(senderId = myId, type = Payload.PayloadType.SYSTEM_CONTROL, data = command.toByteArray())
        meshTransport.sendPayload(peerId, payload)
    }

    override suspend fun sendMessage(peerId: String, peerName: String, text: String, replyToId: String?): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId, text = text,
            timestamp = System.currentTimeMillis(), isFromMe = true,
            type = MessageType.TEXT, status = MessageStatus.QUEUED, replyToId = replyToId
        )
        return saveAndSend(peerId, peerName, message, Payload.PayloadType.TEXT, text.toByteArray())
    }

    override suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
        // Compress image before sending (smart compression)
        val compressionResult = ImageCompressor.compress(imageBytes, maxSize = 1920, targetSizeKB = 500)
        Logger.d("ChatRepository -> Image compressed: ${compressionResult.originalSize / 1024}KB → ${compressionResult.compressedSize / 1024}KB (${compressionResult.compressionRatio.toInt()}% reduction)")

        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val fileName = "sent_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, compressionResult.bytes)
        
        // ✅ FIX: Verify file was saved successfully before proceeding
        if (savedPath == null) {
            Logger.e("ChatRepository -> Failed to save image to disk")
            return Result.failure(Exception("Failed to save image"))
        }
        
        // ✅ FIX: Verify file exists before sending
        val file = File(savedPath)
        if (!file.exists()) {
            Logger.e("ChatRepository -> Saved file does not exist: $savedPath")
            return Result.failure(Exception("Saved file not found"))
        }
        
        Logger.d("ChatRepository -> Image saved successfully: $savedPath (${file.length()} bytes)")
        
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId, text = null, mediaPath = savedPath,
            type = MessageType.IMAGE, timestamp = System.currentTimeMillis(),
            isFromMe = true, status = MessageStatus.QUEUED, replyToId = replyToId
        )
        return saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, compressionResult.bytes)
    }

    override suspend fun sendVideo(peerId: String, peerName: String, videoBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val fileName = "sent_vid_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, videoBytes)
        
        // ✅ FIX: Verify file was saved successfully before proceeding
        if (savedPath == null) {
            Logger.e("ChatRepository -> Failed to save video to disk")
            return Result.failure(Exception("Failed to save video"))
        }
        
        // ✅ FIX: Verify file exists before sending
        val file = File(savedPath)
        if (!file.exists()) {
            Logger.e("ChatRepository -> Saved video file does not exist: $savedPath")
            return Result.failure(Exception("Saved video file not found"))
        }
        
        Logger.d("ChatRepository -> Video saved successfully: $savedPath (${file.length()} bytes)")
        
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId, text = null, mediaPath = savedPath,
            type = MessageType.VIDEO, timestamp = System.currentTimeMillis(),
            isFromMe = true, status = MessageStatus.QUEUED, replyToId = replyToId
        )
        return saveAndSend(peerId, peerName, message, Payload.PayloadType.VIDEO, videoBytes)
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

        // Save message
        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, caption.ifBlank { "[Album]" }, timestamp))
        messageDao.insertMessage(message)

        // Save attachments - avoid lambda to work around KSP type inference issue
        val attachmentEntities = ArrayList<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity>(attachments.size)
        var index = 0
        for ((bytes, type) in attachments) {
            val ext = if (type == MessageType.IMAGE) "jpg" else "mp4"
            val fileName = "sent_album_${messageId}_$index.$ext"
            val savedPath = fileManager.saveMedia(fileName, bytes)
            attachmentEntities.add(
                com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    messageId = messageId,
                    filePath = savedPath ?: ""
                )
            )
            index++
        }
        messageDao.insertMessageAttachments(attachmentEntities)

        // Send as FILE payload (first attachment bytes as representative)
        val firstAttachment = attachments.first().first
        return saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, firstAttachment)
    }

    override suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit> {
        val myId = settingsRepository.getDeviceId()
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Message not found"))
        
        return try {
            if (deleteType == DeleteType.DELETE_FOR_ME) {
                messageDao.markAsDeletedForMe(messageId)
            } else {
                messageDao.markAsDeletedForEveryone(messageId, System.currentTimeMillis(), myId)
                val request = DeleteRequest(messageId, deleteType, myId)
                val payload = Payload(senderId = myId, type = Payload.PayloadType.DELETE_REQUEST, data = Json.encodeToString(request).toByteArray())
                meshTransport.sendPayload(message.chatId, payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to delete message: $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(peerId: String) {
        try {
            chatDao.deleteChatById(peerId)
            messageDao.deleteAllMessagesForChat(peerId)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to delete chat: $peerId", e)
        }
    }

    override suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> {
        val myId = settingsRepository.getDeviceId()
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Message not found"))
        
        return try {
            messageDao.updateReaction(messageId, reaction)
            val update = ReactionUpdate(messageId, reaction, myId)
            val payload = Payload(senderId = myId, type = Payload.PayloadType.REACTION, data = Json.encodeToString(update).toByteArray())
            meshTransport.sendPayload(message.chatId, payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to add reaction to message: $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Message not found"))
        
        return try {
            targetPeerIds.forEach { pid ->
                scope.launch {
                    val chat = chatDao.getChatById(pid)
                    if (message.type == MessageType.TEXT && message.text != null) {
                        sendMessage(pid, chat?.peerName ?: pid, message.text)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward message: $messageId", e)
            Result.failure(e)
        }
    }

    /**
     * Saves message and sends to peer with proper error handling.
     * Returns Result<Unit> for better error propagation.
     */
    private suspend fun saveAndSend(peerId: String, peerName: String, message: MessageEntity, payloadType: Payload.PayloadType, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.d("ChatRepository -> saveAndSend START: messageId=${message.id}, peerId=$peerId, text=${message.text?.take(20)}")
            
            val cleanName = parseName(peerName)
            Logger.d("ChatRepository -> Inserting chat: peerId=$peerId, name=$cleanName")
            chatDao.insertChat(ChatEntity(peerId, cleanName, message.text ?: "[Media]", message.timestamp))
            
            Logger.d("ChatRepository -> Inserting message: id=${message.id}, type=${message.type}")
            messageDao.insertMessage(message)

            // Check if peer is online
            Logger.d("ChatRepository -> Checking online status for peer: $peerId")
            val isOnline = onlinePeers.first().contains(peerId)
            Logger.d("ChatRepository -> Peer $peerId is online: $isOnline")
            
            if (!isOnline) {
                Logger.w("ChatRepository -> Peer $peerId offline, queuing message")
                pendingMessageDao.insert(PendingMessageEntity(id = message.id, recipientId = peerId, recipientName = cleanName, content = message.text ?: "[Media]", type = message.type))
                Logger.d("ChatRepository -> Message queued successfully")
                return@withContext Result.success(Unit)
            }

            val payload = Payload(id = message.id, senderId = message.senderId, timestamp = message.timestamp, type = payloadType, data = data)
            Logger.d("ChatRepository -> Updating message status to SENDING")
            messageDao.updateMessageStatus(message.id, MessageStatus.SENDING)

            Logger.d("ChatRepository -> Sending payload via meshTransport")
            val result = meshTransport.sendPayload(peerId, payload)
            
            if (result.isFailure) {
                Logger.e("ChatRepository -> sendPayload failed: ${result.exceptionOrNull()?.message}")
                messageDao.updateMessageStatus(message.id, MessageStatus.FAILED)
                pendingMessageDao.insert(PendingMessageEntity(id = message.id, recipientId = peerId, recipientName = cleanName, content = message.text ?: "[Media]", type = message.type))
                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Send failed"))
            } else {
                Logger.d("ChatRepository -> sendPayload succeeded, updating status to SENT")
                messageDao.updateMessageStatus(message.id, MessageStatus.SENT)
                Logger.d("ChatRepository -> saveAndSend COMPLETE: messageId=${message.id}")
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to save and send message: ${e.message}", e)
            Logger.e("ChatRepository -> Exception stack trace: ${e.stackTraceToString()}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Retries pending messages with exponential backoff.
     * Includes proper file existence checks and error handling.
     */
    override suspend fun retryPendingMessages(peerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val pending = pendingMessageDao.getByRecipient(peerId)
        
        if (pending.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        Logger.i("ChatRepository -> Retrying ${pending.size} pending messages for $peerId")

        var successCount = 0
        var failureCount = 0

        pending.forEach { pm ->
            val msg = messageDao.getMessageById(pm.id)
            if (msg != null) {
                val result = sendMessageWithBackoff(pm, msg)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failureCount++
                }
            } else {
                // Message not found in database, clean up pending entry
                Logger.w("ChatRepository -> Pending message ${pm.id} not found in DB, removing")
                pendingMessageDao.deleteById(pm.id)
                failureCount++
            }
        }

        Logger.i("ChatRepository -> Retry complete: $successCount succeeded, $failureCount failed")
        
        if (failureCount == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("$failureCount messages failed to send"))
        }
    }

    /**
     * Sends a single message with exponential backoff retry logic.
     */
    private suspend fun sendMessageWithBackoff(
        pm: PendingMessageEntity,
        msg: MessageEntity
    ): Result<Unit> {
        var lastException: Exception? = null

        for (attempt in 1..RETRY_MAX_ATTEMPTS) {
            try {
                val data: ByteArray = when (msg.type) {
                    MessageType.TEXT -> msg.text?.toByteArray() ?: byteArrayOf()
                    MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE, 
                    MessageType.AUDIO, MessageType.DOCUMENT, MessageType.ARCHIVE, 
                    MessageType.APK -> {
                        val path = msg.mediaPath
                        if (path != null) {
                            // Check file existence before reading
                            val file = File(path)
                            if (!file.exists()) {
                                Logger.e("ChatRepository -> Media file not found for retry: $path")
                                return Result.failure(Exception("Media file not found: $path"))
                            }
                            file.readBytes()
                        } else {
                            Logger.w("ChatRepository -> No media path for message ${msg.id}")
                            byteArrayOf()
                        }
                    }
                }

                val payloadType = when (msg.type) {
                    MessageType.TEXT -> Payload.PayloadType.TEXT
                    MessageType.VIDEO -> Payload.PayloadType.VIDEO
                    else -> Payload.PayloadType.FILE
                }

                val payload = Payload(
                    id = msg.id,
                    senderId = msg.senderId,
                    timestamp = msg.timestamp,
                    type = payloadType,
                    data = data
                )

                val result = meshTransport.sendPayload(pm.recipientId, payload)
                
                if (result.isSuccess) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
                    pendingMessageDao.deleteById(pm.id)
                    Logger.i("ChatRepository -> Message ${msg.id} sent successfully on attempt $attempt")
                    return Result.success(Unit)
                } else {
                    lastException = result.exceptionOrNull() as? Exception ?: Exception("Send failed")
                }

            } catch (e: Exception) {
                Logger.e("ChatRepository -> Send attempt $attempt failed for message ${msg.id}", e)
                lastException = e
            }

            // Wait before retry (exponential backoff with jitter)
            if (attempt < RETRY_MAX_ATTEMPTS) {
                val delay = calculateBackoffDelay(attempt)
                Logger.d("ChatRepository -> Waiting ${delay}ms before retry $attempt")
                delay(delay)
            }
        }

        // All retries exhausted
        messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED)
        Logger.e("ChatRepository -> Message ${msg.id} failed after $RETRY_MAX_ATTEMPTS attempts", lastException)
        return Result.failure(lastException ?: Exception("Max retries exceeded"))
    }

    /**
     * Calculates exponential backoff delay with jitter.
     * Formula: min(baseDelay * 2^attempt, maxDelay) + random jitter
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = RETRY_BASE_DELAY_MS * 2.0.pow(attempt - 1).toInt()
        val cappedDelay = exponentialDelay.coerceAtMost(RETRY_MAX_DELAY_MS)
        
        // Add jitter (±25% randomness) to prevent thundering herd
        val jitter = (cappedDelay * 0.25 * (Math.random() * 2 - 1)).toLong()
        
        return (cappedDelay + jitter).coerceAtLeast(RETRY_BASE_DELAY_MS)
    }

    /**
     * Handles incoming payload with timeout to prevent deadlocks.
     */
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

    /**
     * Processes payload logic (extracted for clarity).
     */
    private suspend fun processPayload(peerId: String, payload: Payload) {
        Logger.d("ChatRepository -> Processing payload from $peerId, type=${payload.type}, size=${payload.data.size} bytes")
        
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
                Logger.i("ChatRepository -> Received text message from $peerId: '$text'")
                saveIncomingMessage(payload.senderId, text, null, MessageType.TEXT, payload.timestamp, payload.id)
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            }
            Payload.PayloadType.FILE -> {
                val fileName = "img_${payload.id}.jpg"
                Logger.i("ChatRepository -> Receiving image from $peerId (${payload.data.size} bytes)")
                val savedPath = fileManager.saveMedia(fileName, payload.data)
                // ✅ FIX: Verify file was saved before creating message
                if (savedPath != null) {
                    // ✅ FIX: Verify file exists
                    val file = File(savedPath)
                    if (file.exists()) {
                        Logger.d("ChatRepository -> Incoming image saved: $savedPath (${file.length()} bytes)")
                        saveIncomingMessage(payload.senderId, null, savedPath, MessageType.IMAGE, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                        Logger.i("ChatRepository -> Image message saved to database")
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
                // ✅ FIX: Verify file was saved before creating message
                if (savedPath != null) {
                    // ✅ FIX: Verify file exists
                    val file = File(savedPath)
                    if (file.exists()) {
                        Logger.d("ChatRepository -> Incoming video saved: $savedPath (${file.length()} bytes)")
                        saveIncomingMessage(payload.senderId, null, savedPath, MessageType.VIDEO, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                        Logger.i("ChatRepository -> Video message saved to database")
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

    private suspend fun saveIncomingMessage(peerId: String, text: String?, mediaPath: String?, type: MessageType, timestamp: Long, messageId: String) {
        try {
            val existingChat = chatDao.getChatById(peerId)
            val finalName = if (existingChat != null) parseName(existingChat.peerName) else "Peer_${peerId.take(4)}"
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

    /**
     * Get attachments for a specific message.
     * This is a public method for ViewModels to fetch message attachments.
     */
    suspend fun getMessageAttachments(messageId: String): List<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity> {
        return messageDao.getAttachmentsForMessage(messageId)
    }
}
