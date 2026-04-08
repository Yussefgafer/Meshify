package com.p2p.meshify.core.data.repository.util

import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.data.repository.interfaces.ISessionManagementService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Handles sending encrypted payloads to peers, including offline queuing
 * and chat metadata updates.
 */
class EncryptedPayloadSender(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val pendingMessageDao: PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val settingsRepository: ISettingsRepository,
    private val sessionManagementService: ISessionManagementService,
    private val messageCrypto: MessageEnvelopeCrypto,
    val stringProvider: StringResourceProvider,
    private val scope: CoroutineScope
) {

    /**
     * Send an encrypted payload to a peer, handling offline queuing.
     */
    suspend fun sendEncryptedPayload(
        peerId: String,
        peerName: String,
        encryptedData: ByteArray,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()
        val encryptedLabel = stringProvider.getString(R.string.label_encrypted)

        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = encryptedLabel,
            mediaPath = null,
            type = MessageType.TEXT,
            timestamp = timestamp,
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )

        val cleanName = MessageSerializationUtil.parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, encryptedLabel, timestamp))
        messageDao.insertMessage(message)

        val isOnline = transportManager.getAllTransports().any { it.onlinePeers.value.contains(peerId) }

        if (!isOnline) {
            Logger.w("MessageSendingService -> Peer $peerId offline, queuing encrypted message")
            pendingMessageDao.insert(
                PendingMessageEntity(
                    id = messageId,
                    recipientId = peerId,
                    recipientName = cleanName,
                    content = encryptedLabel,
                    type = MessageType.TEXT
                )
            )
            return Result.success(Unit)
        }

        val payload = Payload(
            id = messageId,
            senderId = myId,
            timestamp = timestamp,
            type = Payload.PayloadType.ENCRYPTED_MESSAGE,
            data = encryptedData
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
                    val transportNames = transports.joinToString("+") { it.transportName }
                    Logger.d("MessageSendingService -> Encrypted message sent to $peerId via [$transportNames]")
                    Result.success(Unit)
                } else {
                    messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
                    val lastError = results.lastOrNull()?.exceptionOrNull()
                    Logger.e("MessageSendingService -> Failed to send encrypted message to $peerId on all transports: ${lastError?.message}")
                    pendingMessageDao.insert(
                        PendingMessageEntity(
                            id = messageId,
                            recipientId = peerId,
                            recipientName = cleanName,
                            content = encryptedLabel,
                            type = MessageType.TEXT
                        )
                    )
                    scope.launch {
                        try {
                            // Note: securityEvents flow is exposed via PayloadProcessingService
                        } catch (emitError: Exception) {
                            Logger.e("MessageSendingService -> Failed to emit security event: ${emitError.message}", emitError)
                        }
                    }
                    Result.failure(lastError ?: Exception("All transports failed"))
                }
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
            Logger.e("MessageSendingService -> Exception sending encrypted message", e)
            Result.failure(e)
        }
    }

    /**
     * Update the chat entity's last message and timestamp.
     * Creates a new chat if one does not exist.
     */
    suspend fun updateChatLastMessage(
        peerId: String,
        existingChat: ChatEntity?,
        lastMessage: String
    ) {
        if (existingChat != null) {
            chatDao.insertChat(existingChat.copy(
                lastMessage = lastMessage,
                lastTimestamp = System.currentTimeMillis()
            ))
        } else {
            chatDao.insertChat(ChatEntity(
                peerId = peerId,
                peerName = "${AppConstants.DEFAULT_PEER_NAME_PREFIX}${peerId.take(4)}",
                lastMessage = lastMessage,
                lastTimestamp = System.currentTimeMillis()
            ))
        }
    }
}
