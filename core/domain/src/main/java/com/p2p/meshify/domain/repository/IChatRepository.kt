package com.p2p.meshify.domain.repository

import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level repository interface for chat operations.
 * 
 * Note: This interface is now a facade that delegates to specialized repositories:
 * - MessageRepository: Sending/receiving messages
 * - ChatManagementRepository: Chat CRUD operations
 * - PendingMessageRepository: Pending message queue
 * - MessageAttachmentRepository: Attachments (albums)
 * - ReactionRepository: Message reactions
 */
interface IChatRepository {
    val onlinePeers: Flow<Set<String>>
    val typingPeers: Flow<Set<String>>

    // Message sending
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
    
    // Chat management
    suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit>
    suspend fun deleteChat(peerId: String)
    suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit>
    
    // Reactions
    suspend fun addReaction(messageId: String, reaction: String?): Result<Unit>
    
    // System
    suspend fun sendSystemCommand(peerId: String, command: String)
    suspend fun handleIncomingPayload(peerId: String, payload: Payload)
    suspend fun retryPendingMessages(peerId: String): Result<Unit>
}
