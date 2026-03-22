package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * ChatManagementRepository - Responsible for chat CRUD operations.
 *
 * Handles:
 * - Get all chats
 * - Get messages for a chat
 * - Delete chat
 * - Delete message (for me / for everyone)
 * - Forward message
 *
 * Single Responsibility: Chat and message management only
 */
class ChatManagementRepository(
    private val context: Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {

    /**
     * Get all chats as a Flow.
     */
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    /**
     * Get messages for a specific chat.
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

    /**
     * Delete a chat and all its messages.
     */
    suspend fun deleteChat(peerId: String) {
        try {
            chatDao.deleteChatById(peerId)
            messageDao.deleteAllMessagesForChat(peerId)
            Logger.d("ChatManagementRepository -> Chat deleted: $peerId")
        } catch (e: Exception) {
            Logger.e("ChatManagementRepository -> Failed to delete chat: $peerId", e)
            throw e
        }
    }

    /**
     * Delete a message (for me or for everyone).
     */
    suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit> {
        return try {
            val message = messageDao.getMessageById(messageId)
                ?: return Result.failure(Exception("Message not found"))

            when (deleteType) {
                DeleteType.DELETE_FOR_ME -> {
                    messageDao.markAsDeletedForMe(messageId)
                    Logger.d("ChatManagementRepository -> Message marked as deleted for me: $messageId")
                }
                DeleteType.DELETE_FOR_EVERYONE -> {
                    messageDao.markAsDeletedForEveryone(
                        messageId,
                        System.currentTimeMillis(),
                        message.senderId
                    )
                    Logger.d("ChatManagementRepository -> Message marked as deleted for everyone: $messageId")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatManagementRepository -> Failed to delete message: $messageId", e)
            Result.failure(e)
        }
    }

    /**
     * Forward a message to multiple peers.
     * 
     * This method creates a copy of the original message in each target chat.
     * For text messages: copies the text content
     * For media messages: creates a reference to the same media file (no copying)
     * 
     * Note: Forwarded messages are marked as "from me" with a new timestamp and ID.
     * The original message metadata (sender, timestamp) is preserved in the message text for context.
     * 
     * @param messageId The ID of the message to forward
     * @param targetPeerIds List of peer IDs to forward the message to
     * @return Result indicating success or failure
     */
    suspend fun forwardMessage(
        messageId: String,
        targetPeerIds: List<String>
    ): Result<Unit> {
        val originalMessage = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

        return try {
            targetPeerIds.forEach { peerId ->
                // Get or create chat record
                val targetChat = chatDao.getChatById(peerId)
                val chatName = targetChat?.peerName ?: "Peer_${peerId.take(4)}"
                
                // Ensure chat exists
                if (targetChat == null) {
                    chatDao.insertChat(
                        ChatEntity(
                            peerId = peerId,
                            peerName = chatName,
                            lastMessage = "[Forwarded Message]",
                            lastTimestamp = System.currentTimeMillis()
                        )
                    )
                }

                // Create forwarded message with context
                val forwardContext = buildForwardContext(originalMessage)
                
                val newMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = peerId,
                    senderId = originalMessage.senderId, // Preserve original sender
                    text = forwardContext,
                    mediaPath = originalMessage.mediaPath, // Reference to same file (no copy)
                    type = originalMessage.type,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true, // Mark as sent by me
                    status = MessageStatus.SENT, // Already "sent" since it's a copy
                    replyToId = null, // Clear reply context for forwarded message
                    groupId = null // Clear group context
                )
                
                messageDao.insertMessage(newMessage)
                
                // Update chat's last message
                chatDao.insertChat(
                    ChatEntity(
                        peerId = peerId,
                        peerName = chatName,
                        lastMessage = newMessage.text ?: "[${newMessage.type.name}]",
                        lastTimestamp = newMessage.timestamp
                    )
                )
                
                Logger.d("ChatManagementRepository -> Message forwarded to $peerId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatManagementRepository -> Failed to forward message: $messageId", e)
            Result.failure(e)
        }
    }

    /**
     * Builds context string for forwarded message.
     * Includes original sender, timestamp, and content preview.
     */
    private fun buildForwardContext(original: MessageEntity): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(original.timestamp))
        val unknown = context.getString(R.string.forward_context_unknown)

        return when (original.type) {
            MessageType.TEXT -> {
                val preview = original.text?.take(100) ?: ""
                context.getString(R.string.forward_context_text, timestamp, preview)
            }
            MessageType.IMAGE -> context.getString(R.string.forward_context_image, timestamp)
            MessageType.VIDEO -> context.getString(R.string.forward_context_video, timestamp)
            MessageType.AUDIO -> context.getString(R.string.forward_context_audio, timestamp)
            MessageType.FILE -> context.getString(R.string.forward_context_file, original.text ?: unknown, timestamp)
            MessageType.DOCUMENT -> context.getString(R.string.forward_context_document, timestamp)
            MessageType.ARCHIVE -> context.getString(R.string.forward_context_archive, original.text ?: unknown, timestamp)
            MessageType.APK -> context.getString(R.string.forward_context_apk, original.text ?: unknown, timestamp)
        }
    }

    /**
     * Copy a message to another chat (for forwarding).
     * @deprecated Use forwardMessage() instead
     */
    @Deprecated("Use forwardMessage() instead", ReplaceWith("forwardMessage(messageId, listOf(newChatId))"))
    private suspend fun copyMessageToChat(messageId: String, newChatId: String) {
        forwardMessage(messageId, listOf(newChatId))
    }
}
