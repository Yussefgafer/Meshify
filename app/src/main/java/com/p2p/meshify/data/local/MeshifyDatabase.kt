package com.p2p.meshify.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.p2p.meshify.data.local.dao.ChatDao
import com.p2p.meshify.data.local.dao.MessageDao
import com.p2p.meshify.data.local.entity.ChatEntity
import com.p2p.meshify.data.local.entity.MessageEntity

/**
 * Main database for Meshify. 
 * Stores all chats and messages for offline-first capability.
 * Schema export enabled for future migrations.
 */
@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MeshifyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
