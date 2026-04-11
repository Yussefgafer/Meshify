package com.p2p.meshify.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.p2p.meshify.core.data.local.dao.*
import com.p2p.meshify.core.data.local.entity.*

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
 * - v6: Added unreadCount column to chats table for unread badges
 * - v7: Dropped trusted_peers table (TOFU security model removed)
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        PendingMessageEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class MeshifyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS trusted_peers")
            }
        }
    }
}
