package com.p2p.meshify.domain.repository

import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.*
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getAllChats(): Flow<List<ChatEntity>>
    fun getMessages(chatId: String): Flow<List<MessageEntity>>
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>

    val onlinePeers: Flow<Set<String>>
    val typingPeers: Flow<Set<String>>

    suspend fun sendMessage(peerId: String, peerName: String, text: String, replyToId: String? = null): Result<Unit>
    suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String, replyToId: String? = null): Result<Unit>
    suspend fun sendVideo(peerId: String, peerName: String, videoBytes: ByteArray, extension: String, replyToId: String? = null): Result<Unit>
    suspend fun sendGroupedMessage(
        peerId: String,
        peerName: String,
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        replyToId: String? = null
    ): Result<Unit>
    suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit>
    suspend fun deleteChat(peerId: String)
    suspend fun addReaction(messageId: String, reaction: String?): Result<Unit>
    suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit>
    suspend fun sendSystemCommand(peerId: String, command: String)
    suspend fun handleIncomingPayload(peerId: String, payload: Payload)
    suspend fun retryPendingMessages(peerId: String)
}
