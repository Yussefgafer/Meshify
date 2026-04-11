package com.p2p.meshify.core.data.repository.impl

import android.content.Context
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.repository.PendingMessageRepository
import com.p2p.meshify.core.data.repository.interfaces.IPayloadProcessingService
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.DeleteRequest
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.ReactionUpdate
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.model.MessageEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Implementation of incoming payload processing.
 * After Phase 3, handles plaintext messages instead of encrypted ones.
 */
class PayloadProcessingServiceImpl(
    private val context: Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
    private val settingsRepository: ISettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val pendingMessageRepository: PendingMessageRepository,
    private val scope: CoroutineScope
) : IPayloadProcessingService {

    private val _securityEvents = MutableSharedFlow<com.p2p.meshify.domain.security.model.SecurityEvent>(replay = 0)
    override val securityEvents: SharedFlow<com.p2p.meshify.domain.security.model.SecurityEvent> = _securityEvents.asSharedFlow()

    override suspend fun processPayload(peerId: String, payload: Payload) {
        Logger.d("PayloadProcessingService -> Processing payload from $peerId, type=${payload.type}")

        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(peerId, payload)
            Payload.PayloadType.DELETE_REQUEST -> handleDeleteRequest(peerId, payload)
            Payload.PayloadType.REACTION -> handleReaction(peerId, payload)
            Payload.PayloadType.TEXT, Payload.PayloadType.ENCRYPTED_MESSAGE -> handlePlaintextMessage(peerId, payload)
            Payload.PayloadType.HANDSHAKE -> handleHandshake(peerId, payload)
            Payload.PayloadType.AVATAR_REQUEST -> Logger.d("PayloadProcessingService -> Avatar request handled by transport layer")
            Payload.PayloadType.AVATAR_RESPONSE -> Logger.d("PayloadProcessingService -> Avatar response handled by transport layer")
            Payload.PayloadType.FILE, Payload.PayloadType.VIDEO -> Logger.d("PayloadProcessingService -> File payload handled by transport layer")
            Payload.PayloadType.DELIVERY_ACK -> Logger.w("PayloadProcessingService -> Unimplemented payload type: DELIVERY_ACK from $peerId")
        }
    }

    override suspend fun handleSystemCommand(peerId: String, payload: Payload) {
        val command = String(payload.data)
        if (command.startsWith("ACK_")) {
            val messageId = command.removePrefix("ACK_")
            Logger.d("PayloadProcessingService -> Received ACK for message $messageId")
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
        }
    }

    override suspend fun handleDeleteRequest(peerId: String, payload: Payload) {
        val req = Json.decodeFromString<DeleteRequest>(String(payload.data))
        Logger.d("PayloadProcessingService -> Received delete request for message ${req.messageId}")
        messageDao.markAsDeletedForEveryone(req.messageId, req.deletedAt, req.deletedBy)
    }

    override suspend fun handleReaction(peerId: String, payload: Payload) {
        val update = Json.decodeFromString<ReactionUpdate>(String(payload.data))
        Logger.d("PayloadProcessingService -> Received reaction ${update.reaction} for message ${update.messageId}")
        messageDao.updateReaction(update.messageId, update.reaction)
    }

    override suspend fun handleEncryptedMessage(peerId: String, payload: Payload) {
        // Deprecated after Phase 3 - redirected to plaintext handler
        handlePlaintextMessage(peerId, payload)
    }

    private suspend fun handlePlaintextMessage(peerId: String, payload: Payload) {
        try {
            val envelope = deserializeEnvelope(payload.data)
            val text = envelope.text

            Logger.d("PayloadProcessingService", "Message received successfully from ${peerId.take(8)}...")

            val saveResult = saveIncomingMessage(peerId, text, null, MessageType.TEXT, payload.timestamp, payload.id)

            if (saveResult.isSuccess) {
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            } else {
                Logger.e(
                    message = "Failed to save incoming message from ${peerId.take(8)}: ${saveResult.exceptionOrNull()?.message}",
                    tag = "PayloadProcessingService"
                )
            }
        } catch (e: Exception) {
            Logger.e("Failed to process plaintext message from ${peerId.take(8)}...", e, "PayloadProcessingService")

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

    override suspend fun handleHandshake(peerId: String, payload: Payload) {
        try {
            val handshake = Json.decodeFromString<Handshake>(String(payload.data))
            val cleanName = parseName(handshake.name)
            chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))

            Logger.d("PayloadProcessingService", "Handshake V3 from ${peerId.take(8)}... (plaintext)")

            scope.launch {
                try {
                    retryPendingMessages(payload.senderId)
                } catch (e: Exception) {
                    Logger.e(
                        message = "Failed to retry pending messages for ${payload.senderId.take(8)}: ${e.message}",
                        throwable = e,
                        tag = "PayloadProcessingService"
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to process handshake from ${peerId.take(8)}...", e, "PayloadProcessingService")
        }
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
            Logger.e("PayloadProcessingService -> Failed to save incoming message", e)
            Result.failure(e)
        }
    }

    private fun parseName(raw: String): String {
        return if (raw.contains("name")) {
            try { Json.decodeFromString<Handshake>(raw).name } catch(e: Exception) { raw.take(20) }
        } else raw.removePrefix("HELO_")
    }

    private fun deserializeEnvelope(data: ByteArray): MessageEnvelope {
        return parsePacket(data)
    }

    private fun parsePacket(data: ByteArray): MessageEnvelope {
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
}
