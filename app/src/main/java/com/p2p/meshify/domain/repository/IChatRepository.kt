package com.p2p.meshify.domain.repository

import com.p2p.meshify.data.local.entity.ChatEntity
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.Flow

/**
 * Clean domain interface for Chat operations.
 * Free from Android Context or Framework dependencies.
 */
interface IChatRepository {
    fun getAllChats(): Flow<List<ChatEntity>>
    fun getMessages(chatId: String): Flow<List<MessageEntity>>
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>
    
    val onlinePeers: Flow<Set<String>>
    val typingPeers: Flow<Set<String>>

    suspend fun sendMessage(peerId: String, peerName: String, text: String): Result<Unit>
    suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String): Result<Unit>
    suspend fun deleteMessages(messageIds: List<String>)
    suspend fun sendSystemCommand(peerId: String, command: String)
    suspend fun handleIncomingPayload(peerId: String, payload: Payload)
}
