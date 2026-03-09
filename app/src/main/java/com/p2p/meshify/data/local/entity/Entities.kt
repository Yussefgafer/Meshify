package com.p2p.meshify.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val peerId: String,
    val peerName: String,
    val lastMessage: String?,
    val lastTimestamp: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val mediaPath: String? = null,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val reaction: String? = null,
    val replyToId: String? = null
)

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recipientId: String,
    val recipientName: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.QUEUED,
    var retryCount: Int = 0,
    val maxRetries: Int = 3
)

enum class MessageType {
    TEXT, IMAGE, FILE
}

enum class MessageStatus {
    QUEUED, SENDING, SENT, DELIVERED, READ, FAILED, RECEIVED
}
