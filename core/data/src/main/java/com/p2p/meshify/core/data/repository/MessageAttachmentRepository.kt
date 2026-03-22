package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * MessageAttachmentRepository - Responsible for managing message attachments (albums).
 *
 * Handles:
 * - Save attachment entities for grouped messages
 * - Get attachments for a specific message
 * - Get all attachments
 *
 * Single Responsibility: Message attachment management only
 */
class MessageAttachmentRepository(
    private val messageDao: MessageDao,
    private val fileManager: IFileManager
) {

    /**
     * Save attachments for a grouped message (album).
     */
    suspend fun saveAttachments(
        messageId: String,
        attachments: List<Pair<ByteArray, MessageType>>
    ): Result<List<MessageAttachmentEntity>> = withContext(Dispatchers.IO) {
        try {
            if (attachments.isEmpty()) {
                return@withContext Result.failure(Exception("No attachments provided"))
            }

            val attachmentEntities = ArrayList<MessageAttachmentEntity>(attachments.size)
            var index = 0

            for ((bytes, type) in attachments) {
                val ext = if (type == MessageType.IMAGE) "jpg" else "mp4"
                val fileName = "sent_album_${messageId}_$index.$ext"
                val savedPath = fileManager.saveMedia(fileName, bytes)

                if (savedPath == null) {
                    Logger.e("MessageAttachmentRepository -> Failed to save attachment $index")
                    return@withContext Result.failure(Exception("Failed to save attachment $index"))
                }

                attachmentEntities.add(
                    MessageAttachmentEntity(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        messageId = messageId,
                        filePath = savedPath
                    )
                )
                index++
            }

            // Insert attachments into database
            messageDao.insertMessageAttachments(attachmentEntities)

            Logger.d("MessageAttachmentRepository -> Saved ${attachmentEntities.size} attachments for message $messageId")
            Result.success(attachmentEntities)
        } catch (e: Exception) {
            Logger.e("MessageAttachmentRepository -> Failed to save attachments", e)
            Result.failure(e)
        }
    }

    /**
     * Get all attachments for a specific message.
     */
    suspend fun getAttachmentsForMessage(messageId: String): List<MessageAttachmentEntity> =
        withContext(Dispatchers.IO) {
            messageDao.getAttachmentsForMessage(messageId)
        }

    /**
     * Get all attachments in the database (for debugging).
     */
    suspend fun getAllAttachments(): List<MessageAttachmentEntity> =
        withContext(Dispatchers.IO) {
            // ✅ FIX: Now uses the new DAO query
            messageDao.getAllAttachments()
        }

    /**
     * Delete attachments for a message.
     */
    suspend fun deleteAttachmentsForMessage(messageId: String) {
        val attachments = getAttachmentsForMessage(messageId)
        attachments.forEach { attachment ->
            // Delete file from disk
            val file = java.io.File(attachment.filePath)
            if (file.exists()) {
                file.delete()
                Logger.d("MessageAttachmentRepository -> Deleted attachment file: ${attachment.filePath}")
            }
        }
        messageDao.deleteAttachmentsForMessages(listOf(messageId))
        Logger.d("MessageAttachmentRepository -> Deleted attachments for message: $messageId")
    }

    /**
     * Send grouped message (album) with multiple attachments.
     * This combines saving attachments with sending the first attachment as payload.
     */
    suspend fun sendGroupedMessage(
        messageId: String,
        peerId: String,
        peerName: String,
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        messageRepository: MessageRepository
    ): Result<Unit> {
        if (attachments.isEmpty()) {
            return Result.failure(Exception("No attachments provided"))
        }

        // Save attachments first
        val saveResult = saveAttachments(messageId, attachments)
        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save attachments"))
        }

        // Send the first attachment as representative (the album will be reconstructed on receiver side)
        val firstAttachmentBytes = attachments.first().first
        return messageRepository.sendFileMessage(
            peerId = peerId,
            peerName = peerName,
            fileBytes = firstAttachmentBytes,
            fileName = "Album: $caption",
            fileType = if (attachments.all { it.second == MessageType.VIDEO }) MessageType.VIDEO else MessageType.IMAGE,
            replyToId = null
        )
    }
}
