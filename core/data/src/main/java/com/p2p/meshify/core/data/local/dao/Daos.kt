package com.p2p.meshify.core.data.local.dao

import androidx.room.*
import com.p2p.meshify.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE peerId = :peerId")
    suspend fun getChatById(peerId: String): ChatEntity?

    @Query("DELETE FROM chats WHERE peerId = :peerId")
    suspend fun deleteChatById(peerId: String)

    @Query("""
        SELECT * FROM chats
        WHERE peerName LIKE '%' || :query || '%' OR lastMessage LIKE '%' || :query || '%'
        ORDER BY lastTimestamp DESC
    """)
    fun searchChats(query: String): Flow<List<ChatEntity>>

    @Query("UPDATE chats SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun resetUnreadCount(peerId: String)

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE peerId = :peerId")
    suspend fun incrementUnreadCount(peerId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getAllMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    /**
     * Observe the most recent [limit] messages of a chat, newest first.
     * Caller reverses to display ascending.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatestMessages(chatId: String, limit: Int): Flow<List<MessageEntity>>

    /**
     * Fetch up to [limit] messages strictly older than [beforeTimestamp], newest first.
     * Caller reverses to display ascending.
     */
    @Query("""
        SELECT * FROM messages
        WHERE chatId = :chatId AND timestamp < :beforeTimestamp
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getMessagesBefore(chatId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>

    /**
     * Batched attachment fetch for album messages: attachments rows carry
     * messageId == album groupId, so one IN query serves every visible album.
     */
    @Query("SELECT * FROM message_attachments WHERE messageId IN (:groupIds)")
    suspend fun getAttachmentsForGroups(groupIds: List<String>): List<MessageAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageAttachment(attachment: MessageAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageAttachments(attachments: List<MessageAttachmentEntity>)

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY id")
    suspend fun getAttachmentsForMessage(messageId: String): List<MessageAttachmentEntity>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)

    @Query("DELETE FROM message_attachments WHERE messageId IN (:messageIds)")
    suspend fun deleteAttachmentsForMessages(messageIds: List<String>)

    @Query("UPDATE messages SET isDeletedForMe = 1 WHERE id = :messageId")
    suspend fun markAsDeletedForMe(messageId: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1, deletedAt = :deletedAt, deletedBy = :deletedBy WHERE id = :messageId")
    suspend fun markAsDeletedForEveryone(messageId: String, deletedAt: Long, deletedBy: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateReaction(messageId: String, reaction: String?)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<String>): List<MessageEntity>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)

    // FIX: Get all attachments in database (for debugging/utility)
    @Query("SELECT * FROM message_attachments ORDER BY id")
    suspend fun getAllAttachments(): List<MessageAttachmentEntity>

    @Query("""
        SELECT * FROM messages
        WHERE chatId = :chatId
          AND text LIKE '%' || :query || '%'
          AND isDeletedForMe = 0
        ORDER BY timestamp DESC
    """)
    fun searchMessagesInChat(chatId: String, query: String): Flow<List<MessageEntity>>

}

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages WHERE recipientId = :recipientId")
    suspend fun getByRecipient(recipientId: String): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE id = :id")
    suspend fun getById(id: String): PendingMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessageEntity)

    @Update
    suspend fun update(message: PendingMessageEntity)

    @Delete
    suspend fun delete(message: PendingMessageEntity)

    @Query("DELETE FROM pending_messages WHERE status = :status")
    suspend fun deleteByStatus(status: MessageStatus)

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    // FIX: Get all pending messages (for debugging/utility)
    @Query("SELECT * FROM pending_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingMessageEntity>
}
