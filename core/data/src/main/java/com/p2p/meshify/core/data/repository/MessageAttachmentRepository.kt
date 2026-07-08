package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import kotlinx.coroutines.CoroutineDispatcher
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
    private val fileManager: IFileManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Save attachments for a grouped message (album).
     */
    suspend fun saveAttachments(
        messageId: String,
        attachments: List<Pair<ByteArray, MessageType>>
    ): Result<List<MessageAttachmentEntity>> = withContext(ioDispatcher) {
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
        withContext(ioDispatcher) {
            messageDao.getAttachmentsForMessage(messageId)
        }

    /**
     * Get all attachments in the database (for debugging).
     */
    suspend fun getAllAttachments(): List<MessageAttachmentEntity> =
        withContext(ioDispatcher) {
            // ✅ FIX: Now uses the new DAO query
            messageDao.getAllAttachments()
        }

}
