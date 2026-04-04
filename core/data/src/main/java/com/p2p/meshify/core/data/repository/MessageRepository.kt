package com.p2p.meshify.core.data.repository

import androidx.room.withTransaction
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.core.util.ImageCompressor
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.ProgressFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/**
 * MessageRepository - Responsible for sending and receiving messages.
 *
 * Handles:
 * - Text messages
 * - Image messages (with smart compression)
 * - Video messages
 * - File messages
 * - Status updates (QUEUED -> SENDING -> SENT -> DELIVERED -> READ)
 * - Offline message queuing
 *
 * Single Responsibility: Message transmission only
 *
 * Merged from:
 * - Original MessageRepository (image/video compression)
 * - MessageSender (saveAndSend logic with offline queuing)
 */
class MessageRepository(
    private val database: MeshifyDatabase,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val pendingMessageDao: PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val settingsRepository: ISettingsRepository
) {

    companion object {
        private const val SEND_TIMEOUT_MS = 30000L // 30 seconds
    }

    /**
     * Select best transport for sending to a peer.
     * Strategy:
     * 1. Use transport that already has this peer online
     * 2. Fall back to LAN as default
     */
    private fun selectBestTransport(peerId: String): IMeshTransport {
        return transportManager.selectBestTransport(peerId)
            ?: throw IllegalStateException("No available transport for peer: $peerId")
    }

    // ==================== Public API: Send Messages ====================

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

        return saveAndSend(peerId, peerName, message, Payload.PayloadType.TEXT, text.toByteArray())
    }

    /**
     * Sends an image message with smart compression.
     */
    suspend fun sendImageMessage(
        peerId: String,
        peerName: String,
        imageBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        // Compress image before sending (smart compression)
        val compressionResult = ImageCompressor.compress(imageBytes, maxSize = 1920, targetSizeKB = 500)
        Logger.d("MessageRepository -> Image compressed: ${compressionResult.originalSize / 1024}KB → ${compressionResult.compressedSize / 1024}KB (${compressionResult.compressionRatio.toInt()}% reduction)")

        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val fileName = "sent_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, compressionResult.bytes)

        // Verify file was saved successfully before proceeding
        if (savedPath == null) {
            Logger.e("MessageRepository -> Failed to save image to disk")
            return Result.failure(Exception("Failed to save image"))
        }

        // Verify file exists before sending
        val file = File(savedPath)
        if (!file.exists()) {
            Logger.e("MessageRepository -> Saved file does not exist: $savedPath")
            return Result.failure(Exception("Saved file not found"))
        }

        Logger.d("MessageRepository -> Image saved successfully: $savedPath (${file.length()} bytes)")

        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = null,
            mediaPath = savedPath,
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )

        return saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, compressionResult.bytes)
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
        val fileName = "sent_vid_$messageId.$extension"
        val savedPath = fileManager.saveMedia(fileName, videoBytes)

        // Verify file was saved successfully before proceeding
        if (savedPath == null) {
            Logger.e("MessageRepository -> Failed to save video to disk")
            return Result.failure(Exception("Failed to save video"))
        }

        // Verify file exists before sending
        val file = File(savedPath)
        if (!file.exists()) {
            Logger.e("MessageRepository -> Saved video file does not exist: $savedPath")
            return Result.failure(Exception("Saved video file not found"))
        }

        Logger.d("MessageRepository -> Video saved successfully: $savedPath (${file.length()} bytes)")

        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = null,
            mediaPath = savedPath,
            type = MessageType.VIDEO,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )

        return saveAndSend(peerId, peerName, message, Payload.PayloadType.VIDEO, videoBytes)
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

        return saveAndSend(peerId, peerName, message, Payload.PayloadType.FILE, fileBytes)
    }

    /**
     * Sends a file with progress tracking using ProgressFileReader.
     * This function reads the file from disk and sends it while emitting progress updates.
     * 
     * @param messageId Unique ID for this message (provided by caller)
     * @param peerId Destination peer ID
     * @param peerName Destination peer name
     * @param file The file to send
     * @param fileType Type of file (IMAGE, VIDEO, FILE, etc.)
     * @param caption Optional caption
     * @param replyToId Optional reply-to message ID
     */
    suspend fun sendFileWithProgress(
        messageId: String,
        peerId: String,
        peerName: String,
        file: File,
        fileType: MessageType,
        caption: String,
        replyToId: String?,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.d("MessageRepository -> sendFileWithProgress START: messageId=$messageId, file=${file.name}, size=${file.length()} bytes")

            // Validate file exists
            if (!file.exists()) {
                Logger.e("MessageRepository -> File does not exist: ${file.absolutePath}")
                return@withContext Result.failure(Exception("File does not exist"))
            }

            // Validate file size (100MB limit to prevent OOM)
            val fileSize = file.length()
            val maxFileSize = com.p2p.meshify.domain.model.AppConstants.MAX_FILE_SIZE_BYTES
            if (fileSize > maxFileSize) {
                val maxFileSizeMb = maxFileSize / 1024 / 1024
                Logger.e("MessageRepository -> File size exceeds ${maxFileSizeMb}MB limit: ${fileSize / 1024 / 1024}MB")
                return@withContext Result.failure(Exception("File too large (max ${maxFileSizeMb}MB)"))
            }

            val myId = settingsRepository.getDeviceId()
            val cleanName = parseName(peerName)

            // Create message entity
            val message = MessageEntity(
                id = messageId,
                chatId = peerId,
                senderId = myId,
                text = caption.ifBlank { file.name },
                mediaPath = null, // Will be saved after successful send
                type = fileType,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                status = MessageStatus.QUEUED,
                replyToId = replyToId
            )

            // Save chat + message atomically in a single transaction
            database.withTransaction {
                chatDao.insertChat(
                    ChatEntity(
                        peerId = peerId,
                        peerName = cleanName,
                        lastMessage = caption.ifBlank { "[File: ${file.name}]" },
                        lastTimestamp = message.timestamp
                    )
                )
                messageDao.insertMessage(message)
            }

            // Check if peer is online
            val isOnline = transportManager.getAllTransports().any { it.onlinePeers.value.contains(peerId) }

            if (!isOnline) {
                Logger.w("MessageRepository -> Peer $peerId offline, queuing file message")
                pendingMessageDao.insert(
                    PendingMessageEntity(
                        id = message.id,
                        recipientId = peerId,
                        recipientName = cleanName,
                        content = "[File: ${file.name}]",
                        type = message.type
                    )
                )
                Logger.d("MessageRepository -> File message queued successfully")
                return@withContext Result.success(Unit)
            }

            // Read file bytes with progress tracking
            val progressReader = ProgressFileReader(file, progressCallback)
            
            // Read file bytes (progress callback will be invoked during reading)
            val fileBytes = progressReader.readBytesWithProgress()
            
            Logger.d("MessageRepository -> File read: ${fileBytes.size} bytes")

            // Update status to SENDING
            messageDao.updateMessageStatus(message.id, MessageStatus.SENDING)

            // Create payload
            val payload = Payload(
                id = message.id,
                senderId = message.senderId,
                timestamp = message.timestamp,
                type = Payload.PayloadType.FILE,
                data = fileBytes
            )

            // Send payload with timeout
            val transport = transportManager.selectBestTransport(peerId)
                ?: return@withContext Result.failure(Exception("No available transport"))

            val result = withTimeout(SEND_TIMEOUT_MS) {
                transport.sendPayload(peerId, payload)
            }

            // Update status based on result
            if (result.isFailure) {
                Logger.e("MessageRepository -> sendPayload failed: ${result.exceptionOrNull()?.message}")
                messageDao.updateMessageStatus(message.id, MessageStatus.FAILED)

                // Queue for retry
                pendingMessageDao.insert(
                    PendingMessageEntity(
                        id = message.id,
                        recipientId = peerId,
                        recipientName = cleanName,
                        content = "[File: ${file.name}]",
                        type = message.type
                    )
                )

                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Send failed"))
            } else {
                Logger.d("MessageRepository -> sendPayload succeeded, updating status to SENT")
                messageDao.updateMessageStatus(message.id, MessageStatus.SENT)
                Logger.d("MessageRepository -> sendFileWithProgress COMPLETE: messageId=$messageId")
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("MessageRepository -> Failed to send file with progress", e)
            return@withContext Result.failure(e)
        }
    }

    // ==================== Internal: Save and Send Logic ====================

    /**
     * Saves message and sends to peer with proper error handling.
     *
     * This method:
     * 1. Saves chat record
     * 2. Saves message to database
     * 3. Checks if peer is online
     * 4. If offline: queues message for later delivery
     * 5. If online: sends payload with timeout
     * 6. Updates message status (SENDING -> SENT/FAILED)
     */
    private suspend fun saveAndSend(
        peerId: String,
        peerName: String,
        message: MessageEntity,
        payloadType: Payload.PayloadType,
        data: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = parseName(peerName)
        try {
            Logger.d("MessageRepository -> saveAndSend START: messageId=${message.id}, peerId=$peerId")

            // Step 1 & 2: Atomic save of chat + message in a single transaction
            Logger.d("MessageRepository -> Inserting chat and message in transaction: peerId=$peerId, name=$cleanName")
            database.withTransaction {
                chatDao.insertChat(
                    ChatEntity(
                        peerId = peerId,
                        peerName = cleanName,
                        lastMessage = message.text ?: "[${message.type.name}]",
                        lastTimestamp = message.timestamp
                    )
                )
                messageDao.insertMessage(message)
            }

            // Step 3: Check if peer is online
            Logger.d("MessageRepository -> Checking online status for peer: $peerId")
            val isOnline = transportManager.getAllTransports().any { it.onlinePeers.value.contains(peerId) }
            Logger.d("MessageRepository -> Peer $peerId is online: $isOnline")

            // Step 4: If offline, queue for later delivery
            if (!isOnline) {
                Logger.w("MessageRepository -> Peer $peerId offline, queuing message")
                pendingMessageDao.insert(
                    PendingMessageEntity(
                        id = message.id,
                        recipientId = peerId,
                        recipientName = cleanName,
                        content = message.text ?: "[${message.type.name}]",
                        type = message.type
                    )
                )
                Logger.d("MessageRepository -> Message queued successfully")
                return@withContext Result.success(Unit)
            }

            // Step 5: Send payload with timeout
            val payload = Payload(
                id = message.id,
                senderId = message.senderId,
                timestamp = message.timestamp,
                type = payloadType,
                data = data
            )

            Logger.d("MessageRepository -> Updating message status to SENDING")
            messageDao.updateMessageStatus(message.id, MessageStatus.SENDING)

            Logger.d("MessageRepository -> Sending payload via selected transport")
            val transport = selectBestTransport(peerId)
            val result = withTimeout(SEND_TIMEOUT_MS) {
                transport.sendPayload(peerId, payload)
            }

            // Step 6: Update status based on result (atomic transaction)
            if (result.isFailure) {
                Logger.e("MessageRepository -> sendPayload failed: ${result.exceptionOrNull()?.message}")
                database.withTransaction {
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
                }

                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Send failed"))
            } else {
                Logger.d("MessageRepository -> sendPayload succeeded, updating status to SENT")
                database.withTransaction {
                    messageDao.updateMessageStatus(message.id, MessageStatus.SENT)
                }
                Logger.d("MessageRepository -> saveAndSend COMPLETE: messageId=${message.id}")
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("MessageRepository -> Failed to save and send message", e)
            // Ensure message is not left in limbo — mark as FAILED and queue for retry
            try {
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
            } catch (dbError: Exception) {
                Logger.e("MessageRepository -> Failed to update message status after exception", dbError)
            }
            return@withContext Result.failure(e)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Parses peer name from format "name (device_id)".
     */
    private fun parseName(peerName: String): String {
        return peerName.substringBefore(" (").trim()
    }

    // ==================== Query Methods ====================

    /**
     * Get messages flow for a chat.
     */
    fun getMessages(chatId: String): Flow<List<MessageEntity>> =
        messageDao.getAllMessagesForChat(chatId)

    /**
     * Get paginated messages for a chat.
     */
    fun getMessagesPaged(
        chatId: String,
        limit: Int,
        offset: Int
    ): Flow<List<MessageEntity>> =
        messageDao.getMessagesPaged(chatId, limit, offset)
}
