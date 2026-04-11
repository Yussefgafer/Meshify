package com.p2p.meshify.core.data.repository.impl

import android.content.Context
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.common.util.HexUtil.hexToByteArray
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.repository.PendingMessageRepository
import com.p2p.meshify.core.data.repository.interfaces.IPayloadProcessingService
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
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
import com.p2p.meshify.domain.security.model.SecurityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Implementation of incoming payload processing.
 * Handles decryption, command processing, reactions, handshakes, and security event emission.
 */
class PayloadProcessingServiceImpl(
    private val context: Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
    private val settingsRepository: ISettingsRepository,
    private val messageCrypto: MessageEnvelopeCrypto,
    private val sessionKeyStore: EncryptedSessionKeyStore,
    private val notificationHelper: NotificationHelper,
    private val sessionManagementService: com.p2p.meshify.core.data.repository.interfaces.ISessionManagementService,
    private val pendingMessageRepository: PendingMessageRepository,
    private val scope: CoroutineScope
) : IPayloadProcessingService {

    private val _securityEvents = MutableSharedFlow<SecurityEvent>(replay = 0)
    override val securityEvents: SharedFlow<SecurityEvent> = _securityEvents.asSharedFlow()

    private val payloadMutex = Mutex()

    companion object {
        private const val PAYLOAD_HANDLING_TIMEOUT_MS = 30000L
    }

    override suspend fun processPayload(peerId: String, payload: Payload) {
        Logger.d("PayloadProcessingService -> Processing payload from $peerId, type=${payload.type}")

        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(peerId, payload)
            Payload.PayloadType.DELETE_REQUEST -> handleDeleteRequest(peerId, payload)
            Payload.PayloadType.REACTION -> handleReaction(peerId, payload)
            Payload.PayloadType.ENCRYPTED_MESSAGE -> handleEncryptedMessage(peerId, payload)
            Payload.PayloadType.HANDSHAKE -> handleHandshake(peerId, payload)
            Payload.PayloadType.TEXT -> rejectPayload("TEXT", peerId)
            Payload.PayloadType.FILE -> rejectPayload("FILE", peerId)
            Payload.PayloadType.VIDEO -> rejectPayload("VIDEO", peerId)
            Payload.PayloadType.DELIVERY_ACK -> Logger.w("PayloadProcessingService -> Unimplemented payload type: DELIVERY_ACK from $peerId")
            Payload.PayloadType.AVATAR_REQUEST -> Logger.w("PayloadProcessingService -> Unimplemented payload type: AVATAR_REQUEST from $peerId")
            Payload.PayloadType.AVATAR_RESPONSE -> Logger.w("PayloadProcessingService -> Unimplemented payload type: AVATAR_RESPONSE from $peerId")
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

    private fun rejectPayload(type: String, peerId: String) {
        Logger.w("PayloadProcessingService -> REJECTED: Plaintext $type from $peerId (downgrade attack prevented)")
    }

    override suspend fun handleEncryptedMessage(peerId: String, payload: Payload) {
        try {
            val envelope = deserializeEnvelope(payload.data)

            val sessionKeyInfo = sessionKeyStore.getSessionKey(peerId)
                ?: throw SecurityException("No session key for peer $peerId - cannot decrypt message")

            val plaintext = messageCrypto.decrypt(
                envelope = envelope,
                sessionKey = sessionKeyInfo.sessionKey
            )

            val text = String(plaintext, Charsets.UTF_8)
            Logger.d("PayloadProcessingService", "Message decrypted successfully from ${peerId.take(8)}...")

            val saveResult = saveIncomingMessage(peerId, text, null, MessageType.TEXT, payload.timestamp, payload.id)

            if (saveResult.isSuccess) {
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            } else {
                Logger.e(
                    message = "Failed to save incoming message from ${peerId.take(8)}: ${saveResult.exceptionOrNull()?.message}",
                    tag = "PayloadProcessingService"
                )

                scope.launch {
                    try {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Database save failed: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } catch (emitError: Exception) {
                        Logger.e(
                            message = "Failed to emit security event for DB save failure: ${emitError.message}",
                            throwable = emitError,
                            tag = "PayloadProcessingService"
                        )
                    }
                }
            }

        } catch (e: SecurityException) {
            Logger.e(
                "SECURITY: Decryption failed for message from ${peerId.take(8)}...: ${e.message}",
                e,
                "PayloadProcessingService"
            )

            val saveResult = saveIncomingMessage(
                peerId = peerId,
                text = context.getString(R.string.error_decryption_failed),
                mediaPath = null,
                type = MessageType.TEXT,
                timestamp = payload.timestamp,
                messageId = "decryption_failed_${UUID.randomUUID()}"
            )

            scope.launch {
                try {
                    if (saveResult.isFailure) {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Failed to save decryption error placeholder: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } else {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = e.message ?: "Unknown decryption error"
                            )
                        )
                    }
                } catch (emitError: Exception) {
                    Logger.e(
                        message = "Failed to emit security event for decryption failure: ${emitError.message}",
                        throwable = emitError,
                        tag = "PayloadProcessingService"
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to process encrypted message from ${peerId.take(8)}...", e, "PayloadProcessingService")

            val saveResult = saveIncomingMessage(
                peerId = peerId,
                text = context.getString(R.string.error_message_processing_failed),
                mediaPath = null,
                type = MessageType.TEXT,
                timestamp = payload.timestamp,
                messageId = "processing_failed_${UUID.randomUUID()}"
            )

            scope.launch {
                try {
                    if (saveResult.isFailure) {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Failed to save processing error placeholder: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } else {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Message processing error: ${e.message ?: "Unknown error"}"
                            )
                        )
                    }
                } catch (emitError: Exception) {
                    Logger.e(
                        message = "Failed to emit security event for processing error: ${emitError.message}",
                        throwable = emitError,
                        tag = "PayloadProcessingService"
                    )
                }
            }
        }
    }

    override suspend fun handleHandshake(peerId: String, payload: Payload) {
        try {
            val handshake = Json.decodeFromString<Handshake>(String(payload.data))
            val cleanName = parseName(handshake.name)
            chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))

            val ephemeralPubKeyHex = handshake.ephemeralPubKeyHex
            val nonceHex = handshake.nonceHex

            if (!ephemeralPubKeyHex.isNullOrBlank() && !nonceHex.isNullOrBlank()) {
                Logger.d("PayloadProcessingService", "Handshake V2 from ${peerId.take(8)}... with ephemeral key exchange")

                val sessionEstablished = sessionManagementService.establishSessionFromHandshake(
                    peerId = peerId,
                    peerEphemeralPubKeyHex = ephemeralPubKeyHex,
                    peerNonceHex = nonceHex
                )

                if (sessionEstablished) {
                    Logger.d("PayloadProcessingService", "Encrypted V2 session established with ${peerId.take(8)}...")
                } else {
                    Logger.e("Session establishment failed for ${peerId.take(8)}...", tag = "PayloadProcessingService")
                    return
                }
            } else {
                Logger.e("Handshake from ${peerId.take(8)}... missing public keys - REJECTING", tag = "PayloadProcessingService")
                return
            }

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

    private suspend fun getPeerPublicKey(peerId: String): String? {
        return sessionKeyStore.getSessionKey(peerId)?.peerPublicKeyHex
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

        val nonceLen = buffer.short.toInt()
        val nonce = ByteArray(nonceLen)
        buffer.get(nonce)

        val timestamp = buffer.long

        val ivLen = buffer.short.toInt()
        val iv = ByteArray(ivLen)
        buffer.get(iv)

        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        val sigLen = buffer.short.toInt()
        val signature = ByteArray(sigLen)
        buffer.get(signature)

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = iv,
            ciphertext = ciphertext,
            signature = signature
        )
    }
}
