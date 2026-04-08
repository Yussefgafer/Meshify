package com.p2p.meshify.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.p2p.meshify.domain.model.MessageType as DomainMessageType
import java.util.UUID

@Entity(
    tableName = "chats",
    indices = [Index(value = ["lastTimestamp"])]
)
data class ChatEntity(
    @PrimaryKey val peerId: String,
    val peerName: String,
    val lastMessage: String?,
    val lastTimestamp: Long,
    val unreadCount: Int = 0
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["chatId", "timestamp"]),
        Index(value = ["senderId"]),
        Index(value = ["status"]),
        // ✅ CRITICAL FIX: Add index on groupId for faster grouped message queries
        // This reduces O(n) scan to O(1) lookup when fetching attachments by groupId
        Index(value = ["groupId"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val mediaPath: String? = null,
    val type: DomainMessageType = DomainMessageType.TEXT,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val reaction: String? = null,
    val replyToId: String? = null,
    val groupId: String? = null
)

@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MessageAttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: DomainMessageType,
    val messageId: String? = null,  // Nullable to work around KSP type inference bug
    val filePath: String
)

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recipientId: String,
    val recipientName: String,
    val content: String,
    val type: DomainMessageType = DomainMessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.QUEUED,
    var retryCount: Int = 0,
    val maxRetries: Int = 3
)

enum class MessageStatus {
    QUEUED, SENDING, SENT, DELIVERED, READ, FAILED, RECEIVED
}
