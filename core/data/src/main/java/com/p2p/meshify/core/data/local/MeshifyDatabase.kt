package com.p2p.meshify.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.p2p.meshify.core.data.local.dao.*
import com.p2p.meshify.core.data.local.entity.*

/**
 * Main database for Meshify.
 * Stores all chats and messages for offline-first capability.
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        PendingMessageEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MeshifyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao
}
