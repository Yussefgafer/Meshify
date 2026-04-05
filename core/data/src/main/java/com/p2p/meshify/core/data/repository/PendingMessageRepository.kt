package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow
import kotlin.random.Random

/**
 * PendingMessageRepository - Responsible for managing pending messages.
 *
 * Handles:
 * - Queue pending messages when peer is offline
 * - Retry sending pending messages with exponential backoff
 * - Clean up failed messages
 *
 * Single Responsibility: Pending message queue management only
 */
class PendingMessageRepository(
    private val pendingMessageDao: PendingMessageDao,
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
    private val settingsRepository: ISettingsRepository
) {

    companion object {
        private const val RETRY_MAX_ATTEMPTS = 5
        private const val RETRY_BASE_DELAY_MS = 1000L // 1 second
        private const val RETRY_MAX_DELAY_MS = 30000L // 30 seconds
    }

    /**
     * Queue a message for later delivery.
     */
    suspend fun queueMessage(
        messageId: String,
        recipientId: String,
        recipientName: String,
        content: String,
        type: MessageType
    ) {
        val pendingMessage = PendingMessageEntity(
            id = messageId,
            recipientId = recipientId,
            recipientName = recipientName,
            content = content,
            type = type,
            status = MessageStatus.QUEUED,
            retryCount = 0,
            maxRetries = RETRY_MAX_ATTEMPTS
        )
        pendingMessageDao.insert(pendingMessage)
        Logger.w("PendingMessageRepository -> Message queued: $messageId for $recipientId")
    }

    /**
     * Get all pending messages for a recipient.
     */
    suspend fun getPendingMessages(recipientId: String): List<PendingMessageEntity> =
        withContext(Dispatchers.IO) {
            pendingMessageDao.getByRecipient(recipientId)
        }

    /**
     * Retry all pending messages for a peer with exponential backoff.
     */
    suspend fun retryPendingMessages(peerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val pending = pendingMessageDao.getByRecipient(peerId)

        if (pending.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        Logger.i("PendingMessageRepository -> Retrying ${pending.size} pending messages for $peerId")

        // Batch fetch with chunking to avoid SQLite 999 parameter limit
        val messageIds = pending.map { it.id }.distinct()
        val messages = messageIds
            .chunked(999)
            .flatMap { chunk -> messageDao.getMessagesByIds(chunk) }
            .associateBy { it.id }

        var successCount = 0
        var failureCount = 0

        pending.forEach { pm ->
            val msg = messages[pm.id]
            if (msg != null) {
                val result = sendMessageWithBackoff(pm, msg)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failureCount++
                }
            } else {
                Logger.w("PendingMessageRepository -> Pending message ${pm.id} not found in DB, removing")
                pendingMessageDao.deleteById(pm.id)
                failureCount++
            }
        }

        Logger.i("PendingMessageRepository -> Retry complete for $peerId: $successCount success, $failureCount failed")

        if (failureCount == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("$failureCount messages failed to send"))
        }
    }

    /**
     * Sends a single pending message with exponential backoff retry logic.
     */
    private suspend fun sendMessageWithBackoff(
        pm: PendingMessageEntity,
        msg: MessageEntity
    ): Result<Unit> {
        var lastException: Exception? = null

        for (attempt in 1..RETRY_MAX_ATTEMPTS) {
            try {
                val data: ByteArray = when (msg.type) {
                    MessageType.TEXT -> msg.text?.toByteArray() ?: byteArrayOf()
                    MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE,
                    MessageType.AUDIO, MessageType.DOCUMENT, MessageType.ARCHIVE,
                    MessageType.APK -> {
                        val path = msg.mediaPath
                        if (path != null) {
                            // Check file existence before reading
                            val file = File(path)
                            if (!file.exists()) {
                                Logger.e("PendingMessageRepository -> Media file not found for retry: $path")
                                return Result.failure(Exception("Media file not found: $path"))
                            }
                            file.readBytes()
                        } else {
                            Logger.w("PendingMessageRepository -> No media path for message ${msg.id}")
                            byteArrayOf()
                        }
                    }
                }

                val payloadType = when (msg.type) {
                    MessageType.TEXT -> Payload.PayloadType.TEXT
                    MessageType.VIDEO -> Payload.PayloadType.VIDEO
                    else -> Payload.PayloadType.FILE
                }

                val payload = Payload(
                    id = msg.id,
                    senderId = msg.senderId,
                    timestamp = msg.timestamp,
                    type = payloadType,
                    data = data
                )

                val transport = selectBestTransport(pm.recipientId)
                val result = transport.sendPayload(pm.recipientId, payload)

                if (result.isSuccess) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
                    pendingMessageDao.deleteById(pm.id)
                    Logger.i("PendingMessageRepository -> Message ${msg.id} sent successfully on attempt $attempt")
                    return Result.success(Unit)
                } else {
                    lastException = result.exceptionOrNull() as? Exception ?: Exception("Send failed")
                }

            } catch (e: Exception) {
                Logger.e("PendingMessageRepository -> Send attempt $attempt failed for message ${msg.id}", e)
                lastException = e
            }

            // Wait before retry (exponential backoff with jitter)
            if (attempt < RETRY_MAX_ATTEMPTS) {
                val delayTime = calculateBackoffDelay(attempt)
                Logger.d("PendingMessageRepository -> Waiting ${delayTime}ms before retry $attempt")
                delay(delayTime)
            }
        }

        // All retries exhausted
        messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED)
        Logger.e("PendingMessageRepository -> Message ${msg.id} failed after $RETRY_MAX_ATTEMPTS attempts", lastException)
        return Result.failure(lastException ?: Exception("Max retries exceeded"))
    }

    /**
     * Select best transport for sending.
     */
    private fun selectBestTransport(peerId: String): IMeshTransport {
        return transportManager.selectBestTransport(peerId).firstOrNull()
            ?: throw IllegalStateException("No available transport for peer: $peerId")
    }

    /**
     * Calculates exponential backoff delay with jitter.
     * Formula: min(baseDelay * 2^attempt, maxDelay) + random jitter
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = RETRY_BASE_DELAY_MS * 2.0.pow(attempt - 1).toInt()
        val cappedDelay = exponentialDelay.coerceAtMost(RETRY_MAX_DELAY_MS)

        // Add jitter (±25% randomness) to prevent thundering herd
        val jitter = (cappedDelay * 0.25 * (Math.random() * 2 - 1)).toLong()

        return (cappedDelay + jitter).coerceAtLeast(RETRY_BASE_DELAY_MS)
    }

    /**
     * Delete a pending message by ID.
     */
    suspend fun deletePendingMessage(messageId: String) {
        pendingMessageDao.deleteById(messageId)
    }

    /**
     * Get all pending messages (for debugging/inspection).
     */
    suspend fun getAllPendingMessages(): List<PendingMessageEntity> =
        withContext(Dispatchers.IO) {
            // ✅ FIX: Now uses the new getAll() DAO query
            pendingMessageDao.getAll()
        }
}
