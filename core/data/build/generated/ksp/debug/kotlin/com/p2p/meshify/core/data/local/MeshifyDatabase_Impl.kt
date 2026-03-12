package com.p2p.meshify.core.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.p2p.meshify.core.`data`.local.dao.ChatDao
import com.p2p.meshify.core.`data`.local.dao.ChatDao_Impl
import com.p2p.meshify.core.`data`.local.dao.MessageDao
import com.p2p.meshify.core.`data`.local.dao.MessageDao_Impl
import com.p2p.meshify.core.`data`.local.dao.PendingMessageDao
import com.p2p.meshify.core.`data`.local.dao.PendingMessageDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MeshifyDatabase_Impl : MeshifyDatabase() {
  private val _chatDao: Lazy<ChatDao> = lazy {
    ChatDao_Impl(this)
  }

  private val _messageDao: Lazy<MessageDao> = lazy {
    MessageDao_Impl(this)
  }

  private val _pendingMessageDao: Lazy<PendingMessageDao> = lazy {
    PendingMessageDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "68204f0efe073df37348673aa65c8b8e", "5885ca024c78ad989f724c9e33e6b9e9") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chats` (`peerId` TEXT NOT NULL, `peerName` TEXT NOT NULL, `lastMessage` TEXT, `lastTimestamp` INTEGER NOT NULL, PRIMARY KEY(`peerId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `chatId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `text` TEXT, `mediaPath` TEXT, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isFromMe` INTEGER NOT NULL, `status` TEXT NOT NULL, `isDeletedForMe` INTEGER NOT NULL, `isDeletedForEveryone` INTEGER NOT NULL, `deletedAt` INTEGER, `deletedBy` TEXT, `reaction` TEXT, `replyToId` TEXT, `groupId` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `message_attachments` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `messageId` TEXT, `filePath` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_message_attachments_messageId` ON `message_attachments` (`messageId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `pending_messages` (`id` TEXT NOT NULL, `recipientId` TEXT NOT NULL, `recipientName` TEXT NOT NULL, `content` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `maxRetries` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '68204f0efe073df37348673aa65c8b8e')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `chats`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `message_attachments`")
        connection.execSQL("DROP TABLE IF EXISTS `pending_messages`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsChats: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChats.put("peerId", TableInfo.Column("peerId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChats.put("peerName", TableInfo.Column("peerName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChats.put("lastMessage", TableInfo.Column("lastMessage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChats.put("lastTimestamp", TableInfo.Column("lastTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChats: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChats: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChats: TableInfo = TableInfo("chats", _columnsChats, _foreignKeysChats, _indicesChats)
        val _existingChats: TableInfo = read(connection, "chats")
        if (!_infoChats.equals(_existingChats)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chats(com.p2p.meshify.core.data.local.entity.ChatEntity).
              | Expected:
              |""".trimMargin() + _infoChats + """
              |
              | Found:
              |""".trimMargin() + _existingChats)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("chatId", TableInfo.Column("chatId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("senderId", TableInfo.Column("senderId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("text", TableInfo.Column("text", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("mediaPath", TableInfo.Column("mediaPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("isFromMe", TableInfo.Column("isFromMe", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("isDeletedForMe", TableInfo.Column("isDeletedForMe", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("isDeletedForEveryone", TableInfo.Column("isDeletedForEveryone", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("deletedAt", TableInfo.Column("deletedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("deletedBy", TableInfo.Column("deletedBy", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("reaction", TableInfo.Column("reaction", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("replyToId", TableInfo.Column("replyToId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("groupId", TableInfo.Column("groupId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages, _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(com.p2p.meshify.core.data.local.entity.MessageEntity).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsMessageAttachments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessageAttachments.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessageAttachments.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessageAttachments.put("messageId", TableInfo.Column("messageId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessageAttachments.put("filePath", TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessageAttachments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysMessageAttachments.add(TableInfo.ForeignKey("messages", "CASCADE", "NO ACTION", listOf("messageId"), listOf("id")))
        val _indicesMessageAttachments: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMessageAttachments.add(TableInfo.Index("index_message_attachments_messageId", false, listOf("messageId"), listOf("ASC")))
        val _infoMessageAttachments: TableInfo = TableInfo("message_attachments", _columnsMessageAttachments, _foreignKeysMessageAttachments, _indicesMessageAttachments)
        val _existingMessageAttachments: TableInfo = read(connection, "message_attachments")
        if (!_infoMessageAttachments.equals(_existingMessageAttachments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |message_attachments(com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity).
              | Expected:
              |""".trimMargin() + _infoMessageAttachments + """
              |
              | Found:
              |""".trimMargin() + _existingMessageAttachments)
        }
        val _columnsPendingMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPendingMessages.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("recipientId", TableInfo.Column("recipientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("recipientName", TableInfo.Column("recipientName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("retryCount", TableInfo.Column("retryCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingMessages.put("maxRetries", TableInfo.Column("maxRetries", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPendingMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPendingMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPendingMessages: TableInfo = TableInfo("pending_messages", _columnsPendingMessages, _foreignKeysPendingMessages, _indicesPendingMessages)
        val _existingPendingMessages: TableInfo = read(connection, "pending_messages")
        if (!_infoPendingMessages.equals(_existingPendingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |pending_messages(com.p2p.meshify.core.data.local.entity.PendingMessageEntity).
              | Expected:
              |""".trimMargin() + _infoPendingMessages + """
              |
              | Found:
              |""".trimMargin() + _existingPendingMessages)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "chats", "messages", "message_attachments", "pending_messages")
  }

  public override fun clearAllTables() {
    super.performClear(true, "chats", "messages", "message_attachments", "pending_messages")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ChatDao::class, ChatDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessageDao::class, MessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PendingMessageDao::class, PendingMessageDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun chatDao(): ChatDao = _chatDao.value

  public override fun messageDao(): MessageDao = _messageDao.value

  public override fun pendingMessageDao(): PendingMessageDao = _pendingMessageDao.value
}
