package com.p2p.meshify.data.local.dao

import androidx.room.*
import com.p2p.meshify.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE peerId = :peerId")
    suspend fun getChatById(peerId: String): ChatEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getAllMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)

    @Query("UPDATE messages SET isDeletedForMe = 1 WHERE id = :messageId")
    suspend fun markAsDeletedForMe(messageId: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1, deletedAt = :deletedAt, deletedBy = :deletedBy WHERE id = :messageId")
    suspend fun markAsDeletedForEveryone(messageId: String, deletedAt: Long, deletedBy: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateReaction(messageId: String, reaction: String?)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
}

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages WHERE status = :status")
    suspend fun getByStatus(status: MessageStatus): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE recipientId = :recipientId")
    suspend fun getByRecipient(recipientId: String): List<PendingMessageEntity>

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
}
