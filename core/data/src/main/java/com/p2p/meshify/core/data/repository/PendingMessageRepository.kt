package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.security.model.MessageEnvelope
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

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
    private val fileManager: IFileManager
) {

    companion object {
        private const val RETRY_MAX_ATTEMPTS = 5
        private const val RETRY_BASE_DELAY_MS = 1000L // 1 second
        private const val RETRY_MAX_DELAY_MS = 30000L // 30 seconds
    }

    // Observable pending count — allows UI to show badge/notification
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // Pending message cache for notification/visibility
    private val _pendingMessages = MutableStateFlow<List<PendingMessageEntity>>(emptyList())
    val pendingMessages: StateFlow<List<PendingMessageEntity>> = _pendingMessages.asStateFlow()

    /**
     * Refresh pending count and list from DB.
     */
    private suspend fun refreshPendingState() {
        val all = withContext(Dispatchers.IO) { pendingMessageDao.getAll() }
        _pendingMessages.value = all
        _pendingCount.value = all.size
        if (all.isNotEmpty()) {
            Logger.w("PendingMessageRepository -> ${all.size} pending message(s) waiting for delivery")
        }
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

        // Refresh pending state after retry batch completes
        refreshPendingState()

        Logger.i("PendingMessageRepository -> Retry complete for $peerId: $successCount success, $failureCount failed")

        if (failureCount == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("$failureCount messages failed to send"))
        }
    }

    /**
     * Retries a single message by id with ONE send attempt (no backoff loop,
     * no give-up cleanup). Used for user-triggered Retry on a failed message:
     * a failed attempt must keep the FAILED row, the pending queue entry and
     * any staged media intact so the user can tap Retry again later.
     */
    suspend fun retrySingleMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val pm = pendingMessageDao.getById(messageId)
            ?: return@withContext Result.failure(Exception("No queued entry for message $messageId"))
        val msg = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(Exception("Message $messageId not found"))
        val result = sendMessageWithBackoff(pm, msg, maxAttempts = 1, cleanupOnGiveUp = false)
        refreshPendingState()
        result
    }

    /**
     * Sends a single pending message with exponential backoff retry logic.
     */
    private suspend fun sendMessageWithBackoff(
        pm: PendingMessageEntity,
        msg: MessageEntity,
        maxAttempts: Int = RETRY_MAX_ATTEMPTS,
        cleanupOnGiveUp: Boolean = true
    ): Result<Unit> {
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                val data: ByteArray = when (msg.type) {
                    MessageType.TEXT -> {
                        // Must match the live-send wire format: the receiver
                        // deserializes TEXT payloads as MessageEnvelope.
                        val text = msg.text
                            ?: return Result.failure(Exception("Null text for queued message ${msg.id}"))
                        serializeMessageEnvelope(
                            MessageEnvelope(
                                senderId = msg.senderId,
                                recipientId = pm.recipientId,
                                text = text,
                                timestamp = msg.timestamp,
                                messageType = "text"
                            )
                        )
                    }
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
                            // Use streaming read with 8KB buffer instead of file.readBytes()
                            // to avoid loading the entire file into memory at once.
                            val outputStream = java.io.ByteArrayOutputStream(file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            java.io.BufferedInputStream(java.io.FileInputStream(file)).use { inputStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                            outputStream.toByteArray()
                        } else {
                            // Without a recoverable file reference we must never
                            // send an empty FILE payload (it would be marked SENT).
                            Logger.e("PendingMessageRepository -> No media path for queued message ${msg.id}, cannot retry")
                            messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED)
                            pendingMessageDao.deleteById(pm.id)
                            return Result.failure(Exception("No media path for message ${msg.id}"))
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

                // Never mark SENT with an empty payload — an empty byte array
                // means the content was lost and the send would be a silent no-op
                if (data.isEmpty()) {
                    Logger.e("PendingMessageRepository -> Refusing to send empty payload for message ${msg.id}")
                    messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED)
                    pendingMessageDao.deleteById(pm.id)
                    deleteStagedFile(msg.mediaPath)
                    return Result.failure(Exception("Empty payload for message ${msg.id}"))
                }

                val transport = selectBestTransport(pm.recipientId)
                val result = transport.sendPayload(pm.recipientId, payload)

                if (result.isSuccess) {
                    finalizeSent(msg, data)
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
            if (attempt < maxAttempts) {
                val delayTime = calculateBackoffDelay(attempt)
                Logger.d("PendingMessageRepository -> Waiting ${delayTime}ms before retry $attempt")
                delay(delayTime)
            }
        }

        if (cleanupOnGiveUp) {
            // Keep the pending row AND any staged file: the staged copy may be
            // the only local copy of the content, and the next handshake's
            // auto-retry (or manual Retry on this FAILED row) can still
            // deliver it once the peer returns.
            messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED)
        }
        Logger.e("PendingMessageRepository -> Message ${msg.id} failed after $maxAttempts attempt(s)", lastException)
        return Result.failure(lastException ?: Exception("Max retries exceeded"))
    }

    /**
     * Marks the message SENT after a successful retry. If the message pointed
     * at a staging copy, promote it to permanent local media (so the sender
     * keeps a viewable file) and delete the temporary staged file.
     */
    private suspend fun finalizeSent(msg: MessageEntity, data: ByteArray) {
        val path = msg.mediaPath
        if (path != null && isStagedFile(path)) {
            val extension = path.substringAfterLast('.', "bin")
            val savedPath = fileManager.saveMedia("sent_${msg.id}.$extension", data)
            if (savedPath != null) {
                messageDao.insertMessage(msg.copy(mediaPath = savedPath, status = MessageStatus.SENT))
                deleteStagedFile(path)
            } else {
                // Keep the staged file as the message's viewable reference
                Logger.w("PendingMessageRepository -> Failed to save sent media locally for ${msg.id}, keeping staged copy")
                messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
            }
        } else {
            messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
        }
        pendingMessageDao.deleteById(msg.id)
    }

    private fun isStagedFile(path: String): Boolean =
        File(path).parentFile?.name == IFileManager.STAGING_DIR_NAME

    private fun deleteStagedFile(path: String?) {
        if (path == null || !isStagedFile(path)) return
        if (!File(path).delete()) {
            Logger.w("PendingMessageRepository -> Failed to delete staged file: $path")
        }
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

}
