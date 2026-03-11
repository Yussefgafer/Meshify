package com.p2p.meshify.data.repository

import com.p2p.meshify.data.local.dao.*
import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.*
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.network.base.*
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

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

    override fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    override fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getAllMessagesForChat(chatId)

    override fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> =
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
        saveAndSend(peerId, peerName, message, Payload.PayloadType.TEXT, text.toByteArray())
        return Result.success(Unit)
    }

    override suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val fileName = "sent_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, imageBytes)
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId, text = null, mediaPath = savedPath,
            type = MessageType.IMAGE, timestamp = System.currentTimeMillis(),
            isFromMe = true, status = MessageStatus.QUEUED, replyToId = replyToId
        )
        saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, imageBytes)
        return Result.success(Unit)
    }

    override suspend fun sendVideo(peerId: String, peerName: String, videoBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val fileName = "sent_vid_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, videoBytes)
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId, text = null, mediaPath = savedPath,
            type = MessageType.VIDEO, timestamp = System.currentTimeMillis(),
            isFromMe = true, status = MessageStatus.QUEUED, replyToId = replyToId
        )
        saveAndSend(peerId, peerName, message, Payload.PayloadType.VIDEO, videoBytes)
        return Result.success(Unit)
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
        val attachmentEntities = ArrayList<MessageAttachmentEntity>(attachments.size)
        var index = 0
        for ((bytes, type) in attachments) {
            val ext = if (type == MessageType.IMAGE) "jpg" else "mp4"
            val fileName = "sent_album_${messageId}_$index.$ext"
            val savedPath = fileManager.saveMedia(fileName, bytes)
            attachmentEntities.add(
                MessageAttachmentEntity(
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
        saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, firstAttachment)
        
        return Result.success(Unit)
    }

    override suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit> {
        val myId = settingsRepository.getDeviceId()
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Not found"))
        if (deleteType == DeleteType.DELETE_FOR_ME) {
            messageDao.markAsDeletedForMe(messageId)
        } else {
            messageDao.markAsDeletedForEveryone(messageId, System.currentTimeMillis(), myId)
            val request = DeleteRequest(messageId, deleteType, myId)
            val payload = Payload(senderId = myId, type = Payload.PayloadType.DELETE_REQUEST, data = Json.encodeToString(request).toByteArray())
            meshTransport.sendPayload(message.chatId, payload)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteChat(peerId: String) {
        chatDao.deleteChatById(peerId)
        messageDao.deleteAllMessagesForChat(peerId)
    }

    override suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> {
        val myId = settingsRepository.getDeviceId()
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Not found"))
        messageDao.updateReaction(messageId, reaction)
        val update = ReactionUpdate(messageId, reaction, myId)
        val payload = Payload(senderId = myId, type = Payload.PayloadType.REACTION, data = Json.encodeToString(update).toByteArray())
        meshTransport.sendPayload(message.chatId, payload)
        return Result.success(Unit)
    }

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        val message = messageDao.getMessageById(messageId) ?: return Result.failure(Exception("Not found"))
        targetPeerIds.forEach { pid ->
            val chat = chatDao.getChatById(pid)
            if (message.type == MessageType.TEXT) sendMessage(pid, chat?.peerName ?: pid, message.text ?: "")
        }
        return Result.success(Unit)
    }

    private suspend fun saveAndSend(peerId: String, peerName: String, message: MessageEntity, payloadType: Payload.PayloadType, data: ByteArray) {
        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, message.text ?: "[Media]", message.timestamp))
        messageDao.insertMessage(message)
        
        val isOnline = onlinePeers.first().contains(peerId)
        if (!isOnline) {
            pendingMessageDao.insert(PendingMessageEntity(id = message.id, recipientId = peerId, recipientName = cleanName, content = message.text ?: "[Media]", type = message.type))
            return
        }

        val payload = Payload(id = message.id, senderId = message.senderId, timestamp = message.timestamp, type = payloadType, data = data)
        messageDao.updateMessageStatus(message.id, MessageStatus.SENDING)
        val result = meshTransport.sendPayload(peerId, payload)
        if (result.isFailure) {
            messageDao.updateMessageStatus(message.id, MessageStatus.FAILED)
            pendingMessageDao.insert(PendingMessageEntity(id = message.id, recipientId = peerId, recipientName = cleanName, content = message.text ?: "[Media]", type = message.type))
        } else {
            messageDao.updateMessageStatus(message.id, MessageStatus.SENT)
        }
    }

    override suspend fun retryPendingMessages(peerId: String) {
        val pending = pendingMessageDao.getByRecipient(peerId)
        pending.forEach { pm ->
            val msg = messageDao.getMessageById(pm.id)
            if (msg != null) {
                val data: ByteArray = when (msg.type) {
                    MessageType.TEXT -> msg.text?.toByteArray() ?: byteArrayOf()
                    MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE -> {
                        val path = msg.mediaPath
                        if (path != null) {
                            try { File(path).readBytes() }
                            catch (e: Exception) {
                                Logger.e("ChatRepo -> Failed to read media for retry: $path"); byteArrayOf()
                            }
                        } else byteArrayOf()
                    }
                }
                val payloadType = when (msg.type) {
                    MessageType.TEXT -> Payload.PayloadType.TEXT
                    MessageType.VIDEO -> Payload.PayloadType.VIDEO
                    else -> Payload.PayloadType.FILE
                }
                val payload = Payload(id = msg.id, senderId = msg.senderId, timestamp = msg.timestamp, type = payloadType, data = data)
                if (meshTransport.sendPayload(peerId, payload).isSuccess) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
                    pendingMessageDao.deleteById(pm.id)
                }
            }
        }
    }

    override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
        payloadMutex.withLock {
            when (payload.type) {
                Payload.PayloadType.SYSTEM_CONTROL -> {
                    val command = String(payload.data)
                    if (command.startsWith("ACK_")) {
                        val messageId = command.removePrefix("ACK_")
                        messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
                    }
                }
                Payload.PayloadType.DELETE_REQUEST -> {
                    val req = Json.decodeFromString<DeleteRequest>(String(payload.data))
                    messageDao.markAsDeletedForEveryone(req.messageId, req.deletedAt, req.deletedBy)
                }
                Payload.PayloadType.REACTION -> {
                    val update = Json.decodeFromString<ReactionUpdate>(String(payload.data))
                    messageDao.updateReaction(update.messageId, update.reaction)
                }
                Payload.PayloadType.TEXT -> {
                    val text = String(payload.data)
                    saveIncomingMessage(payload.senderId, text, null, MessageType.TEXT, payload.timestamp, payload.id)
                    sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                }
                Payload.PayloadType.FILE -> {
                    val fileName = "img_${payload.id}.jpg"
                    val savedPath = fileManager.saveMedia(fileName, payload.data)
                    if (savedPath != null) {
                        saveIncomingMessage(payload.senderId, null, savedPath, MessageType.IMAGE, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                    }
                }
                Payload.PayloadType.VIDEO -> {
                    val fileName = "vid_${payload.id}.mp4"
                    val savedPath = fileManager.saveMedia(fileName, payload.data)
                    if (savedPath != null) {
                        saveIncomingMessage(payload.senderId, null, savedPath, MessageType.VIDEO, payload.timestamp, payload.id)
                        sendSystemCommand(payload.senderId, "ACK_${payload.id}")
                    }
                }
                Payload.PayloadType.HANDSHAKE -> {
                    val rawData = String(payload.data)
                    val cleanName = parseName(rawData)
                    chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))
                    retryPendingMessages(payload.senderId)
                }
                else -> Logger.w("ChatRepository -> Unknown type: ${payload.type}")
            }
        }
    }

    private suspend fun saveIncomingMessage(peerId: String, text: String?, mediaPath: String?, type: MessageType, timestamp: Long, messageId: String) {
        val existingChat = chatDao.getChatById(peerId)
        val finalName = if (existingChat != null) parseName(existingChat.peerName) else "Peer_${peerId.take(4)}"
        chatDao.insertChat(ChatEntity(peerId, finalName, text ?: "[Media]", timestamp))
        val message = MessageEntity(id = messageId, chatId = peerId, senderId = peerId, text = text, mediaPath = mediaPath, type = type, timestamp = timestamp, isFromMe = false, status = MessageStatus.SENT)
        messageDao.insertMessage(message)
        notificationHelper.showMessageNotification(finalName, message)
    }

    private fun parseName(raw: String): String {
        return if (raw.contains("name")) {
             try { Json.decodeFromString<Handshake>(raw).name } catch(e:Exception) { raw.take(20) }
        } else raw.removePrefix("HELO_")
    }
}
