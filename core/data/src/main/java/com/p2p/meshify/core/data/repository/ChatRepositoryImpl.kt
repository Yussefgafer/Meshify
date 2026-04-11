package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.MeshifyDatabase
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
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.domain.security.model.MessageEnvelope
import com.p2p.meshify.domain.security.model.SecurityEvent
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * ChatRepositoryImpl - Facade pattern for chat operations.
 *
 * After Phase 3 security simplification, all messages are sent as plaintext.
 * This facade delegates to specialized repositories for different operations.
 */
class ChatRepositoryImpl(
    private val context: android.content.Context,
    private val stringProvider: StringResourceProvider,
    private val database: MeshifyDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: com.p2p.meshify.core.data.local.dao.PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: ISettingsRepository
) : IChatRepository, Closeable {

    init {
        require(context.applicationContext != null) {
            "Context must be Application Context to prevent memory leaks"
        }
    }

    private val messageRepository: MessageRepository = MessageRepository(
        database = database,
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

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)

    private val _securityEvents = MutableSharedFlow<SecurityEvent>(replay = 0)
    override val securityEvents: SharedFlow<SecurityEvent> = _securityEvents.asSharedFlow()

    fun getAllChats(): Flow<List<ChatEntity>> = chatManagementRepository.getAllChats()
    fun searchChats(query: String): Flow<List<ChatEntity>> = chatManagementRepository.searchChats(query)
    fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageRepository.getMessages(chatId)
    fun searchMessagesInChat(chatId: String, query: String): Flow<List<MessageEntity>> =
        messageRepository.searchMessagesInChat(chatId, query)
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> =
        messageRepository.getMessagesPaged(chatId, limit, offset)

    suspend fun getMessageAttachments(groupId: String): List<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity> =
        messageAttachmentRepository.getAttachmentsForMessage(groupId)

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
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        val envelope = MessageEnvelope(
            senderId = myId,
            recipientId = peerId,
            text = text,
            timestamp = timestamp,
            messageType = "text"
        )

        val envelopeBytes = serializeEnvelope(envelope)
        return sendPlaintextPayload(text, peerId, peerName, envelopeBytes, replyToId)
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

        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, caption.ifBlank { "[Album]" }, timestamp))
        messageDao.insertMessage(message)

        val saveResult = messageAttachmentRepository.saveAttachments(messageId, attachments)
        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save attachments"))
        }

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

                val request = DeleteRequest(messageId, deleteType, message.senderId)
                val payload = Payload(
                    senderId = message.senderId,
                    type = Payload.PayloadType.DELETE_REQUEST,
                    data = Json.encodeToString(request).toByteArray()
                )
                val transport = transportManager.selectBestTransport(message.chatId).firstOrNull()
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

    override suspend fun markChatAsRead(peerId: String) {
        chatManagementRepository.markChatAsRead(peerId)
    }

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

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
                Result.failure(Exception("${failures.size} of ${results.size} forwards failed"))
            } else {
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
        val myId = settingsRepository.getDeviceId()
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

        val transport = transportManager.selectBestTransport(peerId).firstOrNull()
        if (transport == null) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            return false
        }

        val envelope = MessageEnvelope(
            senderId = myId,
            recipientId = peerId,
            text = forwardContext,
            timestamp = newMessage.timestamp,
            messageType = "text"
        )

        val envelopeBytes = serializeEnvelope(envelope)
        val payload = Payload(
            id = newMessage.id,
            senderId = settingsRepository.getDeviceId(),
            timestamp = newMessage.timestamp,
            type = Payload.PayloadType.TEXT,
            data = envelopeBytes
        )

        return try {
            val sendResult = transport.sendPayload(peerId, payload)
            if (sendResult.isSuccess) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                false
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            false
        }
    }

    private suspend fun forwardMediaContext(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        return try {
            val mediaPath = message.mediaPath
            if (mediaPath == null) {
                Logger.e("ChatRepository -> Cannot forward media: mediaPath is null")
                return false
            }

            val mediaFile = File(mediaPath)
            if (!mediaFile.exists()) {
                Logger.e("ChatRepository -> Media file does not exist: ${mediaFile.name}")
                return false
            }

            val fileSize = mediaFile.length()
            val maxFileSize = AppConstants.MAX_FILE_SIZE_BYTES
            if (fileSize > maxFileSize) {
                return false
            }

            val mediaBytes = withContext(Dispatchers.IO) {
                mediaFile.readBytes()
            }

            val myId = settingsRepository.getDeviceId()
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

            messageDao.insertMessage(newMessage)

            val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            if (transport == null) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                return false
            }

            val envelope = MessageEnvelope(
                senderId = myId,
                recipientId = peerId,
                text = forwardContext,
                timestamp = newMessage.timestamp,
                messageType = message.type.name.lowercase()
            )

            val envelopeBytes = serializeEnvelope(envelope)
            val payload = Payload(
                id = newMessage.id,
                senderId = settingsRepository.getDeviceId(),
                timestamp = newMessage.timestamp,
                type = Payload.PayloadType.TEXT,
                data = envelopeBytes
            )

            val sendResult = try {
                transport.sendPayload(peerId, payload)
            } catch (e: Exception) {
                Result.failure(e)
            }

            return if (sendResult.isSuccess) {
                val fileName = "forwarded_${newMessage.id}.${mediaFile.extension}"
                val savedPath = withContext(Dispatchers.IO) {
                    fileManager.saveMedia(fileName, mediaBytes)
                }

                if (savedPath != null) {
                    messageDao.insertMessage(newMessage.copy(mediaPath = savedPath))
                }

                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                false
            }
        } catch (e: java.io.IOException) {
            Logger.e("ChatRepository -> IO error forwarding media", e)
            false
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward media context", e)
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

    private fun buildForwardContext(original: MessageEntity): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(original.timestamp))
        val unknown = stringProvider.getString(R.string.forward_context_unknown)

        return when (original.type) {
            MessageType.TEXT -> {
                val preview = original.text?.take(100) ?: ""
                stringProvider.getString(R.string.forward_context_text, timestamp, preview)
            }
            MessageType.IMAGE -> stringProvider.getString(R.string.forward_context_image, timestamp)
            MessageType.VIDEO -> stringProvider.getString(R.string.forward_context_video, timestamp)
            MessageType.AUDIO -> stringProvider.getString(R.string.forward_context_audio, timestamp)
            MessageType.FILE -> stringProvider.getString(R.string.forward_context_file, original.text ?: unknown, timestamp)
            MessageType.DOCUMENT -> stringProvider.getString(R.string.forward_context_document, timestamp)
            MessageType.ARCHIVE -> stringProvider.getString(R.string.forward_context_archive, original.text ?: unknown, timestamp)
            MessageType.APK -> stringProvider.getString(R.string.forward_context_apk, original.text ?: unknown, timestamp)
        }
    }

    // ==================== Reactions ====================

    override suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> {
        return reactionRepository.addReaction(messageId, reaction)
    }

    // ==================== Incoming Payload Handling ====================

    override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(peerId, payload)
            Payload.PayloadType.DELETE_REQUEST -> handleDeleteRequest(peerId, payload)
            Payload.PayloadType.REACTION -> handleReaction(peerId, payload)
            Payload.PayloadType.TEXT, Payload.PayloadType.ENCRYPTED_MESSAGE -> handlePlaintextMessage(peerId, payload)
            Payload.PayloadType.HANDSHAKE -> handleHandshake(peerId, payload)
            Payload.PayloadType.AVATAR_REQUEST -> handleAvatarRequest(peerId, payload)
            Payload.PayloadType.AVATAR_RESPONSE -> handleAvatarResponse(peerId, payload)
            Payload.PayloadType.FILE, Payload.PayloadType.VIDEO -> handleFilePayload(peerId, payload)
            else -> Logger.w("ChatRepository -> Unhandled payload type: ${payload.type} from $peerId")
        }
    }

    private suspend fun handleSystemCommand(peerId: String, payload: Payload) {
        val command = String(payload.data)
        if (command.startsWith("ACK_")) {
            val messageId = command.removePrefix("ACK_")
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
        }
    }

    private suspend fun handleDeleteRequest(peerId: String, payload: Payload) {
        val req = Json.decodeFromString<DeleteRequest>(String(payload.data))
        messageDao.markAsDeletedForEveryone(req.messageId, req.deletedAt, req.deletedBy)
    }

    private suspend fun handleReaction(peerId: String, payload: Payload) {
        val update = Json.decodeFromString<ReactionUpdate>(String(payload.data))
        messageDao.updateReaction(update.messageId, update.reaction)
    }

    private suspend fun handlePlaintextMessage(peerId: String, payload: Payload) {
        try {
            val envelope = deserializeEnvelope(payload.data)
            val text = envelope.text

            val saveResult = saveIncomingMessage(peerId, text, null, MessageType.TEXT, payload.timestamp, payload.id)

            if (saveResult.isSuccess) {
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            } else {
                Logger.e("Failed to save incoming message from $peerId", tag = "ChatRepository")
            }
        } catch (e: Exception) {
            Logger.e("Failed to process plaintext message from $peerId", e, "ChatRepository")
            saveIncomingMessage(
                peerId = peerId,
                text = context.getString(R.string.error_message_processing_failed),
                mediaPath = null,
                type = MessageType.TEXT,
                timestamp = payload.timestamp,
                messageId = "processing_failed_${UUID.randomUUID()}"
            )
        }
    }

    private suspend fun handleHandshake(peerId: String, payload: Payload) {
        try {
            val handshake = Json.decodeFromString<Handshake>(String(payload.data))
            val cleanName = parseName(handshake.name)
            chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))

            scope.launch {
                try {
                    retryPendingMessages(payload.senderId)
                } catch (e: Exception) {
                    Logger.e("Failed to retry pending messages for ${payload.senderId}", e, "ChatRepository")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to process handshake from $peerId", e, "ChatRepository")
        }
    }

    private suspend fun handleAvatarRequest(peerId: String, payload: Payload) {
        // Handled by LanTransportImpl
    }

    private suspend fun handleAvatarResponse(peerId: String, payload: Payload) {
        // Handled by LanTransportImpl
    }

    private suspend fun handleFilePayload(peerId: String, payload: Payload) {
        // File handling would be implemented here
        Logger.d("ChatRepository -> Received file payload from $peerId")
    }

    override suspend fun sendSystemCommand(peerId: String, command: String) {
        val myId = settingsRepository.getDeviceId()
        val payload = Payload(
            senderId = myId,
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = command.toByteArray()
        )
        val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            ?: throw IllegalStateException("No available transport for peer: $peerId")
        transport.sendPayload(peerId, payload)
    }

    override suspend fun retryPendingMessages(peerId: String): Result<Unit> {
        return pendingMessageRepository.retryPendingMessages(peerId)
    }

    private suspend fun saveIncomingMessage(
        peerId: String,
        text: String?,
        mediaPath: String?,
        type: MessageType,
        timestamp: Long,
        messageId: String
    ): Result<Unit> {
        return try {
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
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to save incoming message", e)
            Result.failure(e)
        }
    }

    private suspend fun sendPlaintextPayload(
        displayText: String,
        peerId: String,
        peerName: String,
        envelopeData: ByteArray,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()
        val cleanName = parseName(peerName)

        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = displayText,
            mediaPath = null,
            type = MessageType.TEXT,
            timestamp = timestamp,
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )

        chatDao.insertChat(ChatEntity(peerId, cleanName, message.text, timestamp))
        messageDao.insertMessage(message)

        val isOnline = transportManager.getAllTransports().any { it.onlinePeers.value.contains(peerId) }

        if (!isOnline) {
            pendingMessageDao.insert(
                com.p2p.meshify.core.data.local.entity.PendingMessageEntity(
                    id = messageId,
                    recipientId = peerId,
                    recipientName = cleanName,
                    content = message.text ?: "[Message]",
                    type = MessageType.TEXT
                )
            )
            return Result.success(Unit)
        }

        val payload = Payload(
            id = messageId,
            senderId = myId,
            timestamp = timestamp,
            type = Payload.PayloadType.TEXT,
            data = envelopeData
        )

        return try {
            messageDao.updateMessageStatus(messageId, MessageStatus.SENDING)
            val transports = transportManager.selectBestTransport(peerId)
            if (transports.isEmpty()) {
                Result.failure(Exception("No available transport"))
            } else {
                val results = transports.map { transport ->
                    runCatching {
                        withTimeout(30000L) {
                            transport.sendPayload(peerId, payload)
                        }
                    }
                }

                val firstSuccess = results.find { it.isSuccess }
                if (firstSuccess != null) {
                    messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
                    Result.success(Unit)
                } else {
                    messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
                    pendingMessageDao.insert(
                        com.p2p.meshify.core.data.local.entity.PendingMessageEntity(
                            id = messageId,
                            recipientId = peerId,
                            recipientName = cleanName,
                            content = message.text ?: "[Message]",
                            type = MessageType.TEXT
                        )
                    )
                    Result.failure(Exception("All transports failed"))
                }
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
            Logger.e("ChatRepository -> Exception sending plaintext message", e)
            Result.failure(e)
        }
    }

    // ==================== Serialization ====================

    private fun serializeEnvelope(envelope: MessageEnvelope): ByteArray {
        val textBytes = envelope.text.toByteArray(Charsets.UTF_8)
        val senderIdBytes = envelope.senderId.toByteArray(Charsets.UTF_8)
        val recipientIdBytes = envelope.recipientId.toByteArray(Charsets.UTF_8)
        val messageTypeBytes = envelope.messageType.toByteArray(Charsets.UTF_8)

        val totalSize = 2 + senderIdBytes.size +
                2 + recipientIdBytes.size +
                4 + textBytes.size +
                8 +
                2 + messageTypeBytes.size

        return ByteBuffer.allocate(totalSize).apply {
            putShort(senderIdBytes.size.toShort())
            put(senderIdBytes)
            putShort(recipientIdBytes.size.toShort())
            put(recipientIdBytes)
            putInt(textBytes.size)
            put(textBytes)
            putLong(envelope.timestamp)
            putShort(messageTypeBytes.size.toShort())
            put(messageTypeBytes)
        }.array()
    }

    private fun deserializeEnvelope(data: ByteArray): MessageEnvelope {
        val buffer = ByteBuffer.wrap(data)

        val senderIdLen = buffer.short.toInt()
        val senderIdBytes = ByteArray(senderIdLen)
        buffer.get(senderIdBytes)
        val senderId = String(senderIdBytes, Charsets.UTF_8)

        val recipientIdLen = buffer.short.toInt()
        val recipientIdBytes = ByteArray(recipientIdLen)
        buffer.get(recipientIdBytes)
        val recipientId = String(recipientIdBytes, Charsets.UTF_8)

        val textLen = buffer.int
        val textBytes = ByteArray(textLen)
        buffer.get(textBytes)
        val text = String(textBytes, Charsets.UTF_8)

        val timestamp = buffer.long

        val messageTypeLen = buffer.short.toInt()
        val messageTypeBytes = ByteArray(messageTypeLen)
        buffer.get(messageTypeBytes)
        val messageType = String(messageTypeBytes, Charsets.UTF_8)

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            text = text,
            timestamp = timestamp,
            messageType = messageType
        )
    }

    private fun parseName(raw: String): String {
        return if (raw.contains("name")) {
            try { Json.decodeFromString<Handshake>(raw).name } catch(e: Exception) { raw.take(20) }
        } else raw.removePrefix("HELO_")
    }

    // ==================== Cleanup ====================

    override fun close() {
        repositoryJob.cancel()
    }
}
