package com.p2p.meshify.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageType {
    TEXT, IMAGE, FILE
}

enum class MessageStatus {
    SENT, RECEIVED, READ, FAILED
}
