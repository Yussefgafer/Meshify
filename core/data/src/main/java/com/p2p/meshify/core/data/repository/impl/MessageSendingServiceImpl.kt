package com.p2p.meshify.core.data.repository.impl

import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.data.repository.MessageAttachmentRepository
import com.p2p.meshify.core.data.repository.MessageRepository
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.data.repository.interfaces.IMessageSendingService
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID

/**
 * Implementation of message sending operations.
 * Handles plaintext message sending (no encryption after Phase 3).
 */
class MessageSendingServiceImpl(
    private val messageRepository: MessageRepository,
    private val messageAttachmentRepository: MessageAttachmentRepository,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val settingsRepository: ISettingsRepository,
    private val stringProvider: StringResourceProvider,
    private val scope: CoroutineScope
) : IMessageSendingService {

    override suspend fun sendMessage(peerId: String, peerName: String, text: String, replyToId: String?): Result<Unit> {
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        // Create plaintext message envelope
        val envelope = com.p2p.meshify.domain.security.model.MessageEnvelope(
            senderId = myId,
            recipientId = peerId,
            text = text,
            timestamp = timestamp,
            messageType = "text"
        )

        val envelopeBytes = serializeEnvelope(envelope)
        return sendPlaintextPayload(text, peerId, peerName, envelopeBytes, replyToId)
    }

    override suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
        return messageRepository.sendImageMessage(peerId, peerName, imageBytes, extension, replyToId)
    }

    override suspend fun sendVideo(peerId: String, peerName: String, videoBytes: ByteArray, extension: String, replyToId: String?): Result<Unit> {
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
        chatDao.insertChat(ChatEntity(peerId, cleanName, caption.ifBlank { stringProvider.getString(R.string.label_album) }, timestamp))
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

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        // Forwarding is handled by ChatRepositoryImpl facade
        return Result.failure(NotImplementedError("Forwarding should be handled by ChatRepositoryImpl"))
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
                PendingMessageEntity(
                    id = messageId,
                    recipientId = peerId,
                    recipientName = cleanName,
                    content = message.text ?: "[Message]",
                    type = MessageType.TEXT
                )
            )
            return Result.success(Unit)
        }

        val payload = com.p2p.meshify.domain.model.Payload(
            id = messageId,
            senderId = myId,
            timestamp = timestamp,
            type = com.p2p.meshify.domain.model.Payload.PayloadType.TEXT,
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
                        kotlinx.coroutines.withTimeout(30000L) {
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
                        PendingMessageEntity(
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
            Result.failure(e)
        }
    }

    private fun serializeEnvelope(envelope: com.p2p.meshify.domain.security.model.MessageEnvelope): ByteArray {
        val textBytes = envelope.text.toByteArray(Charsets.UTF_8)
        val senderIdBytes = envelope.senderId.toByteArray(Charsets.UTF_8)
        val recipientIdBytes = envelope.recipientId.toByteArray(Charsets.UTF_8)
        val messageTypeBytes = envelope.messageType.toByteArray(Charsets.UTF_8)

        val totalSize = 2 + senderIdBytes.size +
                2 + recipientIdBytes.size +
                4 + textBytes.size +
                8 + // timestamp
                2 + messageTypeBytes.size

        return java.nio.ByteBuffer.allocate(totalSize).apply {
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

    private fun parseName(peerName: String): String {
        return peerName.substringBefore(" (").trim()
    }
}
