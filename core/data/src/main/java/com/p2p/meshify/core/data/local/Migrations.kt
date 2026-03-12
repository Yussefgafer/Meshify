package com.p2p.meshify.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database Migrations for schema changes.
 * Each migration handles upgrading from one version to the next without data loss.
 */

/**
 * Migration from version 1 to 2:
 * - Adds foreign key constraint between messages and chats
 * - Adds index on messages.chatId for better query performance
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new messages table with foreign key and index
        // Note: Room expects onUpdate='NO ACTION' (default), not CASCADE
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `messages_new` (
                `id` TEXT NOT NULL,
                `chatId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `text` TEXT,
                `mediaPath` TEXT,
                `type` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isFromMe` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`chatId`) REFERENCES `chats`(`peerId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        // Copy data from old table to new table
        db.execSQL("""
            INSERT INTO messages_new (id, chatId, senderId, text, mediaPath, type, timestamp, isFromMe, status)
            SELECT id, chatId, senderId, text, mediaPath, type, timestamp, isFromMe, status
            FROM messages
        """.trimIndent())

        // Drop old table
        db.execSQL("DROP TABLE messages")

        // Rename new table to messages
        db.execSQL("ALTER TABLE messages_new RENAME TO messages")

        // Create index on chatId
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
    }
}

/**
 * Array of all migrations for easy registration.
 */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
