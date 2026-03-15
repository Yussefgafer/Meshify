package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * MessageSender - Responsible for sending messages to peers.
 * 
 * Handles:
 * - Text messages
 * - Image messages
 * - Video messages
 * - File messages
 * - Grouped messages (albums)
 * - Status updates (QUEUED -> SENDING -> SENT -> DELIVERED -> READ)
 */
class MessageSender(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: PendingMessageDao,
    private val meshTransport: IMeshTransport,
    private val settingsRepository: ISettingsRepository
) {
    
    /**
     * Sends a text message.
     */
    suspend fun sendTextMessage(
        peerId: String,
        peerName: String,
        text: String,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            type = MessageType.TEXT,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )
        
        return saveAndSendMessage(peerId, peerName, message, Payload.PayloadType.TEXT, text.toByteArray())
    }
    
    /**
     * Sends an image message.
     */
    suspend fun sendImageMessage(
        peerId: String,
        peerName: String,
        imageBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = null,
            mediaPath = null, // Will be set by FileManager
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )
        
        return saveAndSendMessage(peerId, peerName, message, Payload.PayloadType.FILE, imageBytes)
    }
    
    /**
     * Sends a video message.
     */
    suspend fun sendVideoMessage(
        peerId: String,
        peerName: String,
        videoBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = null,
            mediaPath = null,
            type = MessageType.VIDEO,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )
        
        return saveAndSendMessage(peerId, peerName, message, Payload.PayloadType.VIDEO, videoBytes)
    }
    
    /**
     * Sends a file message (generic file).
     */
    suspend fun sendFileMessage(
        peerId: String,
        peerName: String,
        fileBytes: ByteArray,
        fileName: String,
        fileType: MessageType,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = fileName,
            mediaPath = null,
            type = fileType,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )
        
        return saveAndSendMessage(peerId, peerName, message, Payload.PayloadType.FILE, fileBytes)
    }
    
    /**
     * Saves and sends a message.
     */
    private suspend fun saveAndSendMessage(
        peerId: String,
        peerName: String,
        message: MessageEntity,
        payloadType: Payload.PayloadType,
        data: ByteArray
    ): Result<Unit> {
        return try {
            val cleanName = parseName(peerName)
            
            // Save chat
            chatDao.insertChat(
                ChatEntity(
                    peerId = peerId,
                    peerName = cleanName,
                    lastMessage = message.text ?: "[${message.type.name}]",
                    lastTimestamp = message.timestamp
                )
            )
            
            // Save message
            messageDao.insertMessage(message)
            
            // Check if peer is online
            // ✅ MAJOR FIX M2: Use withContext(Dispatchers.IO) for blocking .first() call
        // This prevents potential coroutine blocking
        val isOnline = withContext(Dispatchers.IO) {
            meshTransport.onlinePeers.first().contains(peerId)
        }
            
            if (!isOnline) {
                // Queue for later delivery
                pendingMessageDao.insert(
                    PendingMessageEntity(
                        id = message.id,
                        recipientId = peerId,
                        recipientName = cleanName,
                        content = message.text ?: "[${message.type.name}]",
                        type = message.type
                    )
                )
                Logger.w("MessageSender -> Peer $peerId offline, message queued")
                return Result.success(Unit)
            }
            
            // Create payload
            val payload = Payload(
                id = message.id,
                senderId = message.senderId,
                timestamp = message.timestamp,
                type = payloadType,
                data = data
            )
            
            // Update status to SENDING
            messageDao.updateMessageStatus(message.id, MessageStatus.SENDING)
            
            // Send payload
            val result = withTimeout(30000L) {
                meshTransport.sendPayload(peerId, payload)
            }
            
            if (result.isFailure) {
                messageDao.updateMessageStatus(message.id, MessageStatus.FAILED)
                pendingMessageDao.insert(
                    PendingMessageEntity(
                        id = message.id,
                        recipientId = peerId,
                        recipientName = cleanName,
                        content = message.text ?: "[${message.type.name}]",
                        type = message.type
                    )
                )
                Result.failure(result.exceptionOrNull() ?: Exception("Send failed"))
            } else {
                messageDao.updateMessageStatus(message.id, MessageStatus.SENT)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("MessageSender -> Failed to send message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parses peer name from format "name (device_id)".
     */
    private fun parseName(peerName: String): String {
        return peerName.substringBefore(" (").trim()
    }
}
