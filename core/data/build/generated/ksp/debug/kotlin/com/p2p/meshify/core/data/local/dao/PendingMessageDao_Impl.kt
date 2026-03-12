package com.p2p.meshify.core.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.p2p.meshify.core.`data`.local.entity.MessageStatus
import com.p2p.meshify.core.`data`.local.entity.PendingMessageEntity
import com.p2p.meshify.domain.model.MessageType
import javax.`annotation`.processing.Generated
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

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PendingMessageDao_Impl(
  __db: RoomDatabase,
) : PendingMessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPendingMessageEntity: EntityInsertAdapter<PendingMessageEntity>

  private val __deleteAdapterOfPendingMessageEntity:
      EntityDeleteOrUpdateAdapter<PendingMessageEntity>

  private val __updateAdapterOfPendingMessageEntity:
      EntityDeleteOrUpdateAdapter<PendingMessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPendingMessageEntity = object : EntityInsertAdapter<PendingMessageEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `pending_messages` (`id`,`recipientId`,`recipientName`,`content`,`type`,`timestamp`,`status`,`retryCount`,`maxRetries`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PendingMessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.recipientId)
        statement.bindText(3, entity.recipientName)
        statement.bindText(4, entity.content)
        statement.bindText(5, __MessageType_enumToString(entity.type))
        statement.bindLong(6, entity.timestamp)
        statement.bindText(7, __MessageStatus_enumToString(entity.status))
        statement.bindLong(8, entity.retryCount.toLong())
        statement.bindLong(9, entity.maxRetries.toLong())
      }
    }
    this.__deleteAdapterOfPendingMessageEntity = object : EntityDeleteOrUpdateAdapter<PendingMessageEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `pending_messages` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PendingMessageEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfPendingMessageEntity = object : EntityDeleteOrUpdateAdapter<PendingMessageEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `pending_messages` SET `id` = ?,`recipientId` = ?,`recipientName` = ?,`content` = ?,`type` = ?,`timestamp` = ?,`status` = ?,`retryCount` = ?,`maxRetries` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PendingMessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.recipientId)
        statement.bindText(3, entity.recipientName)
        statement.bindText(4, entity.content)
        statement.bindText(5, __MessageType_enumToString(entity.type))
        statement.bindLong(6, entity.timestamp)
        statement.bindText(7, __MessageStatus_enumToString(entity.status))
        statement.bindLong(8, entity.retryCount.toLong())
        statement.bindLong(9, entity.maxRetries.toLong())
        statement.bindText(10, entity.id)
      }
    }
  }

  public override suspend fun insert(message: PendingMessageEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPendingMessageEntity.insert(_connection, message)
  }

  public override suspend fun delete(message: PendingMessageEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfPendingMessageEntity.handle(_connection, message)
  }

  public override suspend fun update(message: PendingMessageEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfPendingMessageEntity.handle(_connection, message)
  }

  public override suspend fun getByStatus(status: MessageStatus): List<PendingMessageEntity> {
    val _sql: String = "SELECT * FROM pending_messages WHERE status = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, __MessageStatus_enumToString(status))
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRecipientId: Int = getColumnIndexOrThrow(_stmt, "recipientId")
        val _columnIndexOfRecipientName: Int = getColumnIndexOrThrow(_stmt, "recipientName")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfRetryCount: Int = getColumnIndexOrThrow(_stmt, "retryCount")
        val _columnIndexOfMaxRetries: Int = getColumnIndexOrThrow(_stmt, "maxRetries")
        val _result: MutableList<PendingMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PendingMessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpRecipientId: String
          _tmpRecipientId = _stmt.getText(_columnIndexOfRecipientId)
          val _tmpRecipientName: String
          _tmpRecipientName = _stmt.getText(_columnIndexOfRecipientName)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpRetryCount: Int
          _tmpRetryCount = _stmt.getLong(_columnIndexOfRetryCount).toInt()
          val _tmpMaxRetries: Int
          _tmpMaxRetries = _stmt.getLong(_columnIndexOfMaxRetries).toInt()
          _item = PendingMessageEntity(_tmpId,_tmpRecipientId,_tmpRecipientName,_tmpContent,_tmpType,_tmpTimestamp,_tmpStatus,_tmpRetryCount,_tmpMaxRetries)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByRecipient(recipientId: String): List<PendingMessageEntity> {
    val _sql: String = "SELECT * FROM pending_messages WHERE recipientId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, recipientId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRecipientId: Int = getColumnIndexOrThrow(_stmt, "recipientId")
        val _columnIndexOfRecipientName: Int = getColumnIndexOrThrow(_stmt, "recipientName")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfRetryCount: Int = getColumnIndexOrThrow(_stmt, "retryCount")
        val _columnIndexOfMaxRetries: Int = getColumnIndexOrThrow(_stmt, "maxRetries")
        val _result: MutableList<PendingMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PendingMessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpRecipientId: String
          _tmpRecipientId = _stmt.getText(_columnIndexOfRecipientId)
          val _tmpRecipientName: String
          _tmpRecipientName = _stmt.getText(_columnIndexOfRecipientName)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpType: MessageType
          _tmpType = __MessageType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpStatus: MessageStatus
          _tmpStatus = __MessageStatus_stringToEnum(_stmt.getText(_columnIndexOfStatus))
          val _tmpRetryCount: Int
          _tmpRetryCount = _stmt.getLong(_columnIndexOfRetryCount).toInt()
          val _tmpMaxRetries: Int
          _tmpMaxRetries = _stmt.getLong(_columnIndexOfMaxRetries).toInt()
          _item = PendingMessageEntity(_tmpId,_tmpRecipientId,_tmpRecipientName,_tmpContent,_tmpType,_tmpTimestamp,_tmpStatus,_tmpRetryCount,_tmpMaxRetries)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByStatus(status: MessageStatus) {
    val _sql: String = "DELETE FROM pending_messages WHERE status = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, __MessageStatus_enumToString(status))
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM pending_messages WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
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
