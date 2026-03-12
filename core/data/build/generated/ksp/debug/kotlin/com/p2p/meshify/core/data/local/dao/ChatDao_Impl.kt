package com.p2p.meshify.core.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.p2p.meshify.core.`data`.local.entity.ChatEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ChatDao_Impl(
  __db: RoomDatabase,
) : ChatDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatEntity: EntityInsertAdapter<ChatEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatEntity = object : EntityInsertAdapter<ChatEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `chats` (`peerId`,`peerName`,`lastMessage`,`lastTimestamp`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatEntity) {
        statement.bindText(1, entity.peerId)
        statement.bindText(2, entity.peerName)
        val _tmpLastMessage: String? = entity.lastMessage
        if (_tmpLastMessage == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpLastMessage)
        }
        statement.bindLong(4, entity.lastTimestamp)
      }
    }
  }

  public override suspend fun insertChat(chat: ChatEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChatEntity.insert(_connection, chat)
  }

  public override fun getAllChats(): Flow<List<ChatEntity>> {
    val _sql: String = "SELECT * FROM chats ORDER BY lastTimestamp DESC"
    return createFlow(__db, false, arrayOf("chats")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfPeerId: Int = getColumnIndexOrThrow(_stmt, "peerId")
        val _columnIndexOfPeerName: Int = getColumnIndexOrThrow(_stmt, "peerName")
        val _columnIndexOfLastMessage: Int = getColumnIndexOrThrow(_stmt, "lastMessage")
        val _columnIndexOfLastTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastTimestamp")
        val _result: MutableList<ChatEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatEntity
          val _tmpPeerId: String
          _tmpPeerId = _stmt.getText(_columnIndexOfPeerId)
          val _tmpPeerName: String
          _tmpPeerName = _stmt.getText(_columnIndexOfPeerName)
          val _tmpLastMessage: String?
          if (_stmt.isNull(_columnIndexOfLastMessage)) {
            _tmpLastMessage = null
          } else {
            _tmpLastMessage = _stmt.getText(_columnIndexOfLastMessage)
          }
          val _tmpLastTimestamp: Long
          _tmpLastTimestamp = _stmt.getLong(_columnIndexOfLastTimestamp)
          _item = ChatEntity(_tmpPeerId,_tmpPeerName,_tmpLastMessage,_tmpLastTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChatById(peerId: String): ChatEntity? {
    val _sql: String = "SELECT * FROM chats WHERE peerId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, peerId)
        val _columnIndexOfPeerId: Int = getColumnIndexOrThrow(_stmt, "peerId")
        val _columnIndexOfPeerName: Int = getColumnIndexOrThrow(_stmt, "peerName")
        val _columnIndexOfLastMessage: Int = getColumnIndexOrThrow(_stmt, "lastMessage")
        val _columnIndexOfLastTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastTimestamp")
        val _result: ChatEntity?
        if (_stmt.step()) {
          val _tmpPeerId: String
          _tmpPeerId = _stmt.getText(_columnIndexOfPeerId)
          val _tmpPeerName: String
          _tmpPeerName = _stmt.getText(_columnIndexOfPeerName)
          val _tmpLastMessage: String?
          if (_stmt.isNull(_columnIndexOfLastMessage)) {
            _tmpLastMessage = null
          } else {
            _tmpLastMessage = _stmt.getText(_columnIndexOfLastMessage)
          }
          val _tmpLastTimestamp: Long
          _tmpLastTimestamp = _stmt.getLong(_columnIndexOfLastTimestamp)
          _result = ChatEntity(_tmpPeerId,_tmpPeerName,_tmpLastMessage,_tmpLastTimestamp)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteChatById(peerId: String) {
    val _sql: String = "DELETE FROM chats WHERE peerId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, peerId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
