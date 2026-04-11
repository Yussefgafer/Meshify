package com.p2p.meshify.core.data.repository.util

import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.data.repository.interfaces.ISessionManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Helper class for message forwarding.
 * Extracted from MessageSendingServiceImpl to reduce file size and improve maintainability.
 */
class MessageForwardHelper(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val settingsRepository: ISettingsRepository,
    private val sessionManagementService: ISessionManagementService,
    private val messageCrypto: MessageEnvelopeCrypto,
    private val encryptedPayloadSender: EncryptedPayloadSender
) {

    /**
     * Forward a message to multiple target peers.
     */
    suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
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
                            val forwardContext = MessageSerializationUtil.buildForwardContext(encryptedPayloadSender.stringProvider, message)
                            var success = false

                            if (message.type == MessageType.TEXT && message.text != null) {
                                success = forwardTextMessage(message, peerId, forwardContext)
                            } else {
                                success = forwardMediaContext(message, peerId, forwardContext)
                            }

                            if (success) {
                                encryptedPayloadSender.updateChatLastMessage(peerId, chat, forwardContext)
                            }

                            Pair(peerId, if (success) Result.success(Unit) else Result.failure(Exception("Forward failed")))
                        } catch (e: Exception) {
                            Logger.e("MessageSendingService -> Failed to forward message to $peerId", e)
                            Pair(peerId, Result.failure<Unit>(e))
                        }
                    }
                }.awaitAll()
            }

            val failures = results.filter { it.second.isFailure }
            if (failures.isNotEmpty()) {
                Logger.e("MessageSendingService -> Forward completed with ${failures.size} failures out of ${results.size}")
                Result.failure(Exception("${failures.size} of ${results.size} forwards failed"))
            } else {
                Logger.d("MessageSendingService -> All ${results.size} forwards succeeded")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("MessageSendingService -> Failed to forward message: $messageId", e)
            Result.failure(e)
        }
    }

    private suspend fun forwardTextMessage(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        val sessionKeyInfo = sessionManagementService.getOrEstablishSessionKey(peerId)
            ?: run {
                Logger.e("MessageSendingService -> Cannot forward: no secure session with $peerId")
                return false
            }

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
            Logger.w("MessageSendingService -> No transport available for forwarding to $peerId")
            return false
        }

        val envelope = try {
            val myId = settingsRepository.getDeviceId()
            messageCrypto.encrypt(
                plaintext = forwardContext.toByteArray(Charsets.UTF_8),
                senderId = myId,
                recipientId = peerId,
                sessionKey = sessionKeyInfo.sessionKey
            )
        } catch (e: Exception) {
            Logger.e("MessageSendingService -> Failed to encrypt forwarded message for $peerId", e)
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            return false
        }

        val envelopeBytes = MessageSerializationUtil.serializeEnvelope(envelope)
        val payload = Payload(
            id = newMessage.id,
            senderId = settingsRepository.getDeviceId(),
            timestamp = newMessage.timestamp,
            type = Payload.PayloadType.ENCRYPTED_MESSAGE,
            data = envelopeBytes
        )

        return try {
            val sendResult = transport.sendPayload(peerId, payload)
            if (sendResult.isSuccess) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)
                Logger.d("MessageSendingService -> Forwarded message ${message.id} to $peerId")
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("MessageSendingService -> Failed to forward message to $peerId")
                false
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            Logger.e("MessageSendingService -> Failed to forward message to $peerId", e)
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
                Logger.e("MessageSendingService -> Cannot forward media: mediaPath is null")
                return false
            }

            val mediaFile = File(mediaPath)
            if (!mediaFile.exists()) {
                Logger.e("MessageSendingService -> Media file does not exist: ${mediaFile.name}")
                return false
            }

            val fileSize = mediaFile.length()
            val maxFileSize = AppConstants.MAX_FILE_SIZE_BYTES
            if (fileSize > maxFileSize) {
                val maxFileSizeMb = AppConstants.MAX_FILE_SIZE_BYTES / 1024 / 1024
                Logger.e("MessageSendingService -> File size exceeds ${maxFileSizeMb}MB limit: ${fileSize / 1024 / 1024}MB")
                return false
            }

            val mediaBytes = withContext(Dispatchers.IO) {
                mediaFile.readBytes()
            }

            val sessionKeyInfo = sessionManagementService.getOrEstablishSessionKey(peerId)
                ?: run {
                    Logger.e("MessageSendingService -> Cannot forward: no secure session with $peerId")
                    return false
                }

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
                Logger.w("MessageSendingService -> No transport available for forwarding to ${peerId.take(8)}")
                return false
            }

            val envelope = try {
                val myId = settingsRepository.getDeviceId()
                messageCrypto.encrypt(
                    plaintext = mediaBytes,
                    senderId = myId,
                    recipientId = peerId,
                    sessionKey = sessionKeyInfo.sessionKey
                )
            } catch (e: Exception) {
                Logger.e("MessageSendingService -> Failed to encrypt forwarded media for ${peerId.take(8)}", e)
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                return false
            }

            val envelopeBytes = MessageSerializationUtil.serializeEnvelope(envelope)
            val payload = Payload(
                id = newMessage.id,
                senderId = settingsRepository.getDeviceId(),
                timestamp = newMessage.timestamp,
                type = Payload.PayloadType.ENCRYPTED_MESSAGE,
                data = envelopeBytes
            )

            val sendResult = try {
                transport.sendPayload(peerId, payload)
            } catch (e: Exception) {
                Logger.e("MessageSendingService -> sendPayload threw exception for ${peerId.take(8)}", e)
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
                Logger.d("MessageSendingService -> Forwarded media message ${message.id.take(8)} to ${peerId.take(8)}")
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("MessageSendingService -> Failed to forward media message to ${peerId.take(8)}")
                false
            }
        } catch (e: java.io.IOException) {
            Logger.e("MessageSendingService -> IO error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: SecurityException) {
            Logger.e("MessageSendingService -> Security error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: Exception) {
            Logger.e("MessageSendingService -> Failed to forward media context to ${peerId.take(8)}", e)
            false
        }
    }
}
