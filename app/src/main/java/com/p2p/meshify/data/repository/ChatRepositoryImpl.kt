package com.p2p.meshify.data.repository

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.data.local.dao.ChatDao
import com.p2p.meshify.data.local.dao.MessageDao
import com.p2p.meshify.data.local.entity.ChatEntity
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.data.local.entity.MessageStatus
import com.p2p.meshify.data.local.entity.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.network.base.IMeshTransport
import com.p2p.meshify.network.lan.LanTransportImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * Data layer implementation of Chat Repository.
 * Compliant with IChatRepository domain interface.
 * Dependency Injected via Interface.
 */
class ChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val meshTransport: IMeshTransport,
    private val settingsRepository: ISettingsRepository, // FIXED: Now depends on Interface
    private val fileManager: IFileManager
) : IChatRepository {

    override fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    override fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getAllMessagesForChat(chatId)

    override fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> = 
        messageDao.getMessagesPaged(chatId, limit, offset).map { it.reversed() }

    override val onlinePeers: Flow<Set<String>> = if (meshTransport is LanTransportImpl) {
        meshTransport.onlinePeers
    } else {
        flowOf(emptySet())
    }

    override val typingPeers: Flow<Set<String>> = if (meshTransport is LanTransportImpl) {
        meshTransport.typingPeers
    } else {
        flowOf(emptySet())
    }

    override suspend fun sendSystemCommand(peerId: String, command: String) {
        val myId = settingsRepository.getDeviceId()
        val payload = Payload(
            senderId = myId,
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = command.toByteArray()
        )
        meshTransport.sendPayload(peerId, payload)
    }

    override suspend fun sendMessage(peerId: String, peerName: String, text: String): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        
        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId,
            text = text, timestamp = System.currentTimeMillis(), isFromMe = true,
            type = MessageType.TEXT, status = MessageStatus.SENT
        )
        
        saveAndSend(peerId, peerName, message, Payload.PayloadType.TEXT, text.toByteArray())
        return Result.success(Unit)
    }

    override suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()

        val fileName = "sent_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, imageBytes)

        val message = MessageEntity(
            id = messageId, chatId = peerId, senderId = myId,
            text = null, mediaPath = savedPath,
            type = MessageType.IMAGE, timestamp = System.currentTimeMillis(),
            isFromMe = true, status = MessageStatus.SENT
        )

        saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, imageBytes)
        return Result.success(Unit)
    }

    override suspend fun deleteMessages(messageIds: List<String>) {
        messageDao.deleteMessages(messageIds)
    }

    private suspend fun saveAndSend(
        peerId: String, peerName: String, message: MessageEntity,
        payloadType: Payload.PayloadType, data: ByteArray
    ) {
        chatDao.insertChat(ChatEntity(peerId, peerName, message.text ?: "[Image]", message.timestamp))
        messageDao.insertMessage(message)
        
        val payload = Payload(
            id = message.id, senderId = message.senderId,
            timestamp = message.timestamp, type = payloadType, data = data
        )
        
        val result = meshTransport.sendPayload(peerId, payload)
        if (result.isFailure) {
            messageDao.updateMessageStatus(message.id, MessageStatus.FAILED)
        } else {
            // Updated: Mark as RECEIVED if send succeeds (Self-ACK for now until peer ACKs)
            // messageDao.updateMessageStatus(message.id, MessageStatus.RECEIVED) 
        }
    }

    override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> {
                val command = String(payload.data)
                if (command.startsWith("ACK_")) {
                    val messageId = command.removePrefix("ACK_")
                    messageDao.updateMessageStatus(messageId, MessageStatus.RECEIVED)
                }
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
            Payload.PayloadType.HANDSHAKE -> {
                val peerName = String(payload.data).replace("HELO_", "")
                chatDao.insertChat(ChatEntity(payload.senderId, peerName, "Connected", payload.timestamp))
            }
            else -> {}
        }
    }

    private suspend fun saveIncomingMessage(
        peerId: String, text: String?, mediaPath: String?,
        type: MessageType, timestamp: Long, messageId: String
    ) {
        val existingChat = chatDao.getChatById(peerId)
        val finalName = existingChat?.peerName ?: "Peer_${peerId.take(4)}"
        chatDao.insertChat(ChatEntity(peerId, finalName, text ?: "[Image]", timestamp))
        messageDao.insertMessage(MessageEntity(
            id = messageId, chatId = peerId, senderId = peerId,
            text = text, mediaPath = mediaPath, type = type,
            timestamp = timestamp, isFromMe = false, status = MessageStatus.RECEIVED
        ))
    }
}
