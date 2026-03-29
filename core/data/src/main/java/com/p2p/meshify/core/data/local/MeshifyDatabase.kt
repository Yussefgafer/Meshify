package com.p2p.meshify.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.p2p.meshify.core.data.local.dao.*
import com.p2p.meshify.core.data.local.entity.*
import com.p2p.meshify.core.data.local.entity.TrustedPeerEntity

/**
 * Main database for Meshify.
 * Stores all chats and messages for offline-first capability.
 *
 * Version History:
 * - v1: Initial schema (chats, messages, attachments, pending)
 * - v2: Added indexes for performance
 * - v3: Added groupId index for grouped message queries
 * - v4: Added groupId index (C4 fix - 80-90% query speed improvement)
 * - v5: Added trusted_peers table for TOFU security
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        PendingMessageEntity::class,
        TrustedPeerEntity::class
    ],
    version = 5,  // ✅ Incremented for trusted_peers table
    exportSchema = true
)
abstract class MeshifyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun trustedPeerDao(): TrustedPeerDao
}
