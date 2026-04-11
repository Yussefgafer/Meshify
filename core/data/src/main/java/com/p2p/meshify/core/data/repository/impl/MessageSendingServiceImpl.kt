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
import com.p2p.meshify.core.data.repository.MessageRepository
import com.p2p.meshify.core.data.repository.MessageAttachmentRepository
import com.p2p.meshify.core.data.repository.util.MessageForwardHelper
import com.p2p.meshify.core.data.repository.util.EncryptedPayloadSender
import com.p2p.meshify.core.data.repository.util.MessageSerializationUtil
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.data.repository.interfaces.IMessageSendingService
import com.p2p.meshify.core.data.repository.interfaces.ISessionManagementService
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID

/**
 * Implementation of message sending operations.
 * Delegates to MessageRepository for standard sends, handles encrypted/grouped/forwarded messages directly.
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
    private val sessionManagementService: ISessionManagementService,
    private val messageCrypto: MessageEnvelopeCrypto,
    private val stringProvider: StringResourceProvider,
    private val scope: CoroutineScope
) : IMessageSendingService {

    private val encryptedPayloadSender = EncryptedPayloadSender(
        messageDao = messageDao,
        chatDao = chatDao,
        pendingMessageDao = pendingMessageDao,
        transportManager = transportManager,
        fileManager = fileManager,
        settingsRepository = settingsRepository,
        sessionManagementService = sessionManagementService,
        messageCrypto = messageCrypto,
        stringProvider = stringProvider,
        scope = scope
    )

    private val forwardHelper = MessageForwardHelper(
        messageDao = messageDao,
        chatDao = chatDao,
        transportManager = transportManager,
        fileManager = fileManager,
        settingsRepository = settingsRepository,
        sessionManagementService = sessionManagementService,
        messageCrypto = messageCrypto,
        encryptedPayloadSender = encryptedPayloadSender
    )

    override suspend fun sendMessage(peerId: String, peerName: String, text: String, replyToId: String?): Result<Unit> {
        val sessionKeyInfo = sessionManagementService.getOrEstablishSessionKey(peerId)
            ?: return Result.failure(SecurityException("Secure session required - cannot send plaintext to $peerId"))

        val myId = settingsRepository.getDeviceId()
        val envelope = messageCrypto.encrypt(
            plaintext = text.toByteArray(Charsets.UTF_8),
            senderId = myId,
            recipientId = peerId,
            sessionKey = sessionKeyInfo.sessionKey
        )

        val envelopeBytes = MessageSerializationUtil.serializeEnvelope(envelope)
        return sendEncryptedPayload(peerId, peerName, envelopeBytes, replyToId)
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

        val cleanName = MessageSerializationUtil.parseName(peerName)
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
        return forwardHelper.forwardMessage(messageId, targetPeerIds)
    }

    private suspend fun sendEncryptedPayload(
        peerId: String,
        peerName: String,
        encryptedData: ByteArray,
        replyToId: String?
    ): Result<Unit> {
        return encryptedPayloadSender.sendEncryptedPayload(peerId, peerName, encryptedData, replyToId)
    }
}
