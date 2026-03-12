package com.p2p.meshify.core.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.p2p.meshify.core.`data`.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.`data`.local.entity.MessageEntity
import com.p2p.meshify.core.`data`.local.entity.MessageStatus
import com.p2p.meshify.domain.model.MessageType
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MessageDao_Impl(
  __db: RoomDatabase,
) : MessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>

  private val __insertAdapterOfMessageAttachmentEntity: EntityInsertAdapter<MessageAttachmentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `messages` (`id`,`chatId`,`senderId`,`text`,`mediaPath`,`type`,`timestamp`,`isFromMe`,`status`,`isDeletedForMe`,`isDeletedForEveryone`,`deletedAt`,`deletedBy`,`reaction`,`replyToId`,`groupId`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.chatId)
        statement.bindText(3, entity.senderId)
        val _tmpText: String? = entity.text
        if (_tmpText == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpText)
        }
        val _tmpMediaPath: String? = entity.mediaPath
        if (_tmpMediaPath == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpMediaPath)
        }
        statement.bindText(6, __MessageType_enumToString(entity.type))
        statement.bindLong(7, entity.timestamp)
        val _tmp: Int = if (entity.isFromMe) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, __MessageStatus_enumToString(entity.status))
        val _tmp_1: Int = if (entity.isDeletedForMe) 1 else 0
        statement.bindLong(10, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.isDeletedForEveryone) 1 else 0
        statement.bindLong(11, _tmp_2.toLong())
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(12)
        } else {
          statement.bindLong(12, _tmpDeletedAt)
        }
        val _tmpDeletedBy: String? = entity.deletedBy
        if (_tmpDeletedBy == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpDeletedBy)
        }
        val _tmpReaction: String? = entity.reaction
        if (_tmpReaction == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpReaction)
        }
        val _tmpReplyToId: String? = entity.replyToId
        if (_tmpReplyToId == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpReplyToId)
        }
        val _tmpGroupId: String? = entity.groupId
        if (_tmpGroupId == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpGroupId)
        }
      }
    }
    this.__insertAdapterOfMessageAttachmentEntity = object : EntityInsertAdapter<MessageAttachmentEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `message_attachments` (`id`,`type`,`messageId`,`filePath`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageAttachmentEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, __MessageType_enumToString(entity.type))
        val _tmpMessageId: String? = entity.messageId
        if (_tmpMessageId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpMessageId)
        }
        statement.bindText(4, entity.filePath)
      }
    }
  }

  public override suspend fun insertMessage(message: MessageEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMessageEntity.insert(_connection, message)
  }

  public override suspend fun insertMessageAttachment(attachment: MessageAttachmentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMessageAttachmentEntity.insert(_connection, attachment)
  }

  public override suspend fun insertMessageAttachments(attachments: List<MessageAttachmentEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMessageAttachmentEntity.insert(_connection, attachments)
  }

  public override fun getMessagesPaged(
    chatId: String,
    limit: Int,
    offset: Int,
  ): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE chatId = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, chatId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfChatId: Int = getColumnIndexOrThrow(_stmt, "chatId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfMediaPath: Int = getColumnIndexOrThrow(_stmt, "mediaPath")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIsFromMe: Int = getColumnIndexOrThrow(_stmt, "isFromMe")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIsDeletedForMe: Int = getColumnIndexOrThrow(_stmt, "isDeletedForMe")
        val _columnIndexOfIsDeletedForEveryone: Int = getColumnIndexOrThrow(_stmt, "isDeletedForEveryone")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deletedAt")
        val _columnIndexOfDeletedBy: Int = getColumnIndexOrThrow(_stmt, "deletedBy")
        val _columnIndexOfReaction: Int = getColumnIndexOrThrow(_stmt, "reaction")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "replyToId")
        val _columnIndexOfGroupId: Int = getColumnIndexOrThrow(_stmt, "groupId")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpChatId: String
          _tmpChatId = _stmt.getText(_columnIndexOfChatId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpText: String?
          if (_stmt.isNull(_columnIndexOfText)) {
            _tmpText = null
          } else {
            _tmpText = _stmt.getText(_columnIndexOfText)
          }
          val _tmpMediaPath: String?
          if (_stmt.isNull(_columnIndexOfMediaPath)) {
            _tmpMediaPath = null
          } else {
            _tmpMediaPath = _stmt.getText(_columnIndexOfMediaPath)
          }
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIsFromMe: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFromMe).toInt()
          _tmpIsFromMe = _tmp != 0
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpIsDeletedForMe: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeletedForMe).toInt()
          _tmpIsDeletedForMe = _tmp_1 != 0
          val _tmpIsDeletedForEveryone: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsDeletedForEveryone).toInt()
          _tmpIsDeletedForEveryone = _tmp_2 != 0
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedBy: String?
          if (_stmt.isNull(_columnIndexOfDeletedBy)) {
            _tmpDeletedBy = null
          } else {
            _tmpDeletedBy = _stmt.getText(_columnIndexOfDeletedBy)
          }
          val _tmpReaction: String?
          if (_stmt.isNull(_columnIndexOfReaction)) {
            _tmpReaction = null
          } else {
            _tmpReaction = _stmt.getText(_columnIndexOfReaction)
          }
          val _tmpReplyToId: String?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReplyToId = null
          } else {
            _tmpReplyToId = _stmt.getText(_columnIndexOfReplyToId)
          }
          val _tmpGroupId: String?
          if (_stmt.isNull(_columnIndexOfGroupId)) {
            _tmpGroupId = null
          } else {
            _tmpGroupId = _stmt.getText(_columnIndexOfGroupId)
          }
          _item = MessageEntity(_tmpId,_tmpChatId,_tmpSenderId,_tmpText,_tmpMediaPath,_tmpType,_tmpTimestamp,_tmpIsFromMe,_tmpStatus,_tmpIsDeletedForMe,_tmpIsDeletedForEveryone,_tmpDeletedAt,_tmpDeletedBy,_tmpReaction,_tmpReplyToId,_tmpGroupId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllMessagesForChat(chatId: String): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE chatId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, chatId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfChatId: Int = getColumnIndexOrThrow(_stmt, "chatId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfMediaPath: Int = getColumnIndexOrThrow(_stmt, "mediaPath")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIsFromMe: Int = getColumnIndexOrThrow(_stmt, "isFromMe")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIsDeletedForMe: Int = getColumnIndexOrThrow(_stmt, "isDeletedForMe")
        val _columnIndexOfIsDeletedForEveryone: Int = getColumnIndexOrThrow(_stmt, "isDeletedForEveryone")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deletedAt")
        val _columnIndexOfDeletedBy: Int = getColumnIndexOrThrow(_stmt, "deletedBy")
        val _columnIndexOfReaction: Int = getColumnIndexOrThrow(_stmt, "reaction")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "replyToId")
        val _columnIndexOfGroupId: Int = getColumnIndexOrThrow(_stmt, "groupId")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpChatId: String
          _tmpChatId = _stmt.getText(_columnIndexOfChatId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpText: String?
          if (_stmt.isNull(_columnIndexOfText)) {
            _tmpText = null
          } else {
            _tmpText = _stmt.getText(_columnIndexOfText)
          }
          val _tmpMediaPath: String?
          if (_stmt.isNull(_columnIndexOfMediaPath)) {
            _tmpMediaPath = null
          } else {
            _tmpMediaPath = _stmt.getText(_columnIndexOfMediaPath)
          }
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIsFromMe: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFromMe).toInt()
          _tmpIsFromMe = _tmp != 0
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpIsDeletedForMe: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeletedForMe).toInt()
          _tmpIsDeletedForMe = _tmp_1 != 0
          val _tmpIsDeletedForEveryone: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsDeletedForEveryone).toInt()
          _tmpIsDeletedForEveryone = _tmp_2 != 0
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedBy: String?
          if (_stmt.isNull(_columnIndexOfDeletedBy)) {
            _tmpDeletedBy = null
          } else {
            _tmpDeletedBy = _stmt.getText(_columnIndexOfDeletedBy)
          }
          val _tmpReaction: String?
          if (_stmt.isNull(_columnIndexOfReaction)) {
            _tmpReaction = null
          } else {
            _tmpReaction = _stmt.getText(_columnIndexOfReaction)
          }
          val _tmpReplyToId: String?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReplyToId = null
          } else {
            _tmpReplyToId = _stmt.getText(_columnIndexOfReplyToId)
          }
          val _tmpGroupId: String?
          if (_stmt.isNull(_columnIndexOfGroupId)) {
            _tmpGroupId = null
          } else {
            _tmpGroupId = _stmt.getText(_columnIndexOfGroupId)
          }
          _item = MessageEntity(_tmpId,_tmpChatId,_tmpSenderId,_tmpText,_tmpMediaPath,_tmpType,_tmpTimestamp,_tmpIsFromMe,_tmpStatus,_tmpIsDeletedForMe,_tmpIsDeletedForEveryone,_tmpDeletedAt,_tmpDeletedBy,_tmpReaction,_tmpReplyToId,_tmpGroupId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAttachmentsForMessage(messageId: String): List<MessageAttachmentEntity> {
    val _sql: String = "SELECT * FROM message_attachments WHERE messageId = ? ORDER BY id"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, messageId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfMessageId: Int = getColumnIndexOrThrow(_stmt, "messageId")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _result: MutableList<MessageAttachmentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageAttachmentEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpMessageId: String?
          if (_stmt.isNull(_columnIndexOfMessageId)) {
            _tmpMessageId = null
          } else {
            _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          }
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          _item = MessageAttachmentEntity(_tmpId,_tmpType,_tmpMessageId,_tmpFilePath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllMessagesForChatWithAttachments(chatId: String): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE chatId = ? ORDER BY timestamp ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, chatId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfChatId: Int = getColumnIndexOrThrow(_stmt, "chatId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfMediaPath: Int = getColumnIndexOrThrow(_stmt, "mediaPath")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIsFromMe: Int = getColumnIndexOrThrow(_stmt, "isFromMe")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIsDeletedForMe: Int = getColumnIndexOrThrow(_stmt, "isDeletedForMe")
        val _columnIndexOfIsDeletedForEveryone: Int = getColumnIndexOrThrow(_stmt, "isDeletedForEveryone")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deletedAt")
        val _columnIndexOfDeletedBy: Int = getColumnIndexOrThrow(_stmt, "deletedBy")
        val _columnIndexOfReaction: Int = getColumnIndexOrThrow(_stmt, "reaction")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "replyToId")
        val _columnIndexOfGroupId: Int = getColumnIndexOrThrow(_stmt, "groupId")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpChatId: String
          _tmpChatId = _stmt.getText(_columnIndexOfChatId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpText: String?
          if (_stmt.isNull(_columnIndexOfText)) {
            _tmpText = null
          } else {
            _tmpText = _stmt.getText(_columnIndexOfText)
          }
          val _tmpMediaPath: String?
          if (_stmt.isNull(_columnIndexOfMediaPath)) {
            _tmpMediaPath = null
          } else {
            _tmpMediaPath = _stmt.getText(_columnIndexOfMediaPath)
          }
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIsFromMe: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFromMe).toInt()
          _tmpIsFromMe = _tmp != 0
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpIsDeletedForMe: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeletedForMe).toInt()
          _tmpIsDeletedForMe = _tmp_1 != 0
          val _tmpIsDeletedForEveryone: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsDeletedForEveryone).toInt()
          _tmpIsDeletedForEveryone = _tmp_2 != 0
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedBy: String?
          if (_stmt.isNull(_columnIndexOfDeletedBy)) {
            _tmpDeletedBy = null
          } else {
            _tmpDeletedBy = _stmt.getText(_columnIndexOfDeletedBy)
          }
          val _tmpReaction: String?
          if (_stmt.isNull(_columnIndexOfReaction)) {
            _tmpReaction = null
          } else {
            _tmpReaction = _stmt.getText(_columnIndexOfReaction)
          }
          val _tmpReplyToId: String?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReplyToId = null
          } else {
            _tmpReplyToId = _stmt.getText(_columnIndexOfReplyToId)
          }
          val _tmpGroupId: String?
          if (_stmt.isNull(_columnIndexOfGroupId)) {
            _tmpGroupId = null
          } else {
            _tmpGroupId = _stmt.getText(_columnIndexOfGroupId)
          }
          _item = MessageEntity(_tmpId,_tmpChatId,_tmpSenderId,_tmpText,_tmpMediaPath,_tmpType,_tmpTimestamp,_tmpIsFromMe,_tmpStatus,_tmpIsDeletedForMe,_tmpIsDeletedForEveryone,_tmpDeletedAt,_tmpDeletedBy,_tmpReaction,_tmpReplyToId,_tmpGroupId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMessageById(messageId: String): MessageEntity? {
    val _sql: String = "SELECT * FROM messages WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, messageId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfChatId: Int = getColumnIndexOrThrow(_stmt, "chatId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfMediaPath: Int = getColumnIndexOrThrow(_stmt, "mediaPath")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIsFromMe: Int = getColumnIndexOrThrow(_stmt, "isFromMe")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIsDeletedForMe: Int = getColumnIndexOrThrow(_stmt, "isDeletedForMe")
        val _columnIndexOfIsDeletedForEveryone: Int = getColumnIndexOrThrow(_stmt, "isDeletedForEveryone")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deletedAt")
        val _columnIndexOfDeletedBy: Int = getColumnIndexOrThrow(_stmt, "deletedBy")
        val _columnIndexOfReaction: Int = getColumnIndexOrThrow(_stmt, "reaction")
        val _columnIndexOfReplyToId: Int = getColumnIndexOrThrow(_stmt, "replyToId")
        val _columnIndexOfGroupId: Int = getColumnIndexOrThrow(_stmt, "groupId")
        val _result: MessageEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpChatId: String
          _tmpChatId = _stmt.getText(_columnIndexOfChatId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpText: String?
          if (_stmt.isNull(_columnIndexOfText)) {
            _tmpText = null
          } else {
            _tmpText = _stmt.getText(_columnIndexOfText)
          }
          val _tmpMediaPath: String?
          if (_stmt.isNull(_columnIndexOfMediaPath)) {
            _tmpMediaPath = null
          } else {
            _tmpMediaPath = _stmt.getText(_columnIndexOfMediaPath)
          }
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIsFromMe: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFromMe).toInt()
          _tmpIsFromMe = _tmp != 0
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpIsDeletedForMe: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeletedForMe).toInt()
          _tmpIsDeletedForMe = _tmp_1 != 0
          val _tmpIsDeletedForEveryone: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsDeletedForEveryone).toInt()
          _tmpIsDeletedForEveryone = _tmp_2 != 0
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedBy: String?
          if (_stmt.isNull(_columnIndexOfDeletedBy)) {
            _tmpDeletedBy = null
          } else {
            _tmpDeletedBy = _stmt.getText(_columnIndexOfDeletedBy)
          }
          val _tmpReaction: String?
          if (_stmt.isNull(_columnIndexOfReaction)) {
            _tmpReaction = null
          } else {
            _tmpReaction = _stmt.getText(_columnIndexOfReaction)
          }
          val _tmpReplyToId: String?
          if (_stmt.isNull(_columnIndexOfReplyToId)) {
            _tmpReplyToId = null
          } else {
            _tmpReplyToId = _stmt.getText(_columnIndexOfReplyToId)
          }
          val _tmpGroupId: String?
          if (_stmt.isNull(_columnIndexOfGroupId)) {
            _tmpGroupId = null
          } else {
            _tmpGroupId = _stmt.getText(_columnIndexOfGroupId)
          }
          _result = MessageEntity(_tmpId,_tmpChatId,_tmpSenderId,_tmpText,_tmpMediaPath,_tmpType,_tmpTimestamp,_tmpIsFromMe,_tmpStatus,_tmpIsDeletedForMe,_tmpIsDeletedForEveryone,_tmpDeletedAt,_tmpDeletedBy,_tmpReaction,_tmpReplyToId,_tmpGroupId)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
    val _sql: String = "UPDATE messages SET status = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, __MessageStatus_enumToString(status))
        _argIndex = 2
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessages(messageIds: List<String>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("DELETE FROM messages WHERE id IN (")
    val _inputSize: Int = messageIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: String in messageIds) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAttachmentsForMessages(messageIds: List<String>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("DELETE FROM message_attachments WHERE messageId IN (")
    val _inputSize: Int = messageIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: String in messageIds) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markAsDeletedForMe(messageId: String) {
    val _sql: String = "UPDATE messages SET isDeletedForMe = 1 WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markAsDeletedForEveryone(
    messageId: String,
    deletedAt: Long,
    deletedBy: String,
  ) {
    val _sql: String = "UPDATE messages SET isDeletedForEveryone = 1, deletedAt = ?, deletedBy = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, deletedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, deletedBy)
        _argIndex = 3
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateReaction(messageId: String, reaction: String?) {
    val _sql: String = "UPDATE messages SET reaction = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (reaction == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, reaction)
        }
        _argIndex = 2
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllMessagesForChat(chatId: String) {
    val _sql: String = "DELETE FROM messages WHERE chatId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, chatId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __MessageType_enumToString(_value: MessageType): String = when (_value) {
    MessageType.TEXT -> "TEXT"
    MessageType.IMAGE -> "IMAGE"
    MessageType.VIDEO -> "VIDEO"
    MessageType.FILE -> "FILE"
  }

  private fun __MessageStatus_enumToString(_value: MessageStatus): String = when (_value) {
    MessageStatus.QUEUED -> "QUEUED"
    MessageStatus.SENDING -> "SENDING"
    MessageStatus.SENT -> "SENT"
    MessageStatus.DELIVERED -> "DELIVERED"
    MessageStatus.READ -> "READ"
    MessageStatus.FAILED -> "FAILED"
    MessageStatus.RECEIVED -> "RECEIVED"
  }

  private fun __MessageType_stringToEnum(_value: String): MessageType = when (_value) {
    "TEXT" -> MessageType.TEXT
    "IMAGE" -> MessageType.IMAGE
    "VIDEO" -> MessageType.VIDEO
    "FILE" -> MessageType.FILE
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  private fun __MessageStatus_stringToEnum(_value: String): MessageStatus = when (_value) {
    "QUEUED" -> MessageStatus.QUEUED
    "SENDING" -> MessageStatus.SENDING
    "SENT" -> MessageStatus.SENT
    "DELIVERED" -> MessageStatus.DELIVERED
    "READ" -> MessageStatus.READ
    "FAILED" -> MessageStatus.FAILED
    "RECEIVED" -> MessageStatus.RECEIVED
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
