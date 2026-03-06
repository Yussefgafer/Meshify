package com.p2p.meshify.data.local.dao

import androidx.room.*
import com.p2p.meshify.data.local.entity.ChatEntity
import com.p2p.meshify.data.local.entity.MessageEntity
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
    /**
     * REAL PAGINATION: Retrieves messages with limit and offset.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getAllMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: com.p2p.meshify.data.local.entity.MessageStatus)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)
}
