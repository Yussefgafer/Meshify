package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.ReactionUpdate
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.TransportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ReactionRepository - Responsible for managing message reactions.
 *
 * Handles:
 * - Add/update reaction to a message
 * - Remove reaction from a message
 * - Sync reactions across peers
 *
 * Single Responsibility: Reaction management only
 */
class ReactionRepository(
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
    private val settingsRepository: ISettingsRepository
) {

    /**
     * Add or update a reaction to a message.
     */
    suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val message = messageDao.getMessageById(messageId)
                ?: return@withContext Result.failure(Exception("Message not found"))

            val myId = settingsRepository.getDeviceId()

            // Update reaction in database
            messageDao.updateReaction(messageId, reaction)

            // Send reaction update to peer
            val update = ReactionUpdate(messageId, reaction, myId)
            val payload = Payload(
                senderId = myId,
                type = Payload.PayloadType.REACTION,
                data = Json.encodeToString(update).toByteArray()
            )

            val transport = transportManager.selectBestTransport(message.chatId).firstOrNull()
                ?: return@withContext Result.failure(Exception("No available transport"))

            transport.sendPayload(message.chatId, payload)

            Logger.d("ReactionRepository -> Reaction ${reaction ?: "removed"} for message: $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ReactionRepository -> Failed to add reaction to message: $messageId", e)
            Result.failure(e)
        }
    }

    /**
     * Remove reaction from a message.
     */
    suspend fun removeReaction(messageId: String): Result<Unit> {
        return addReaction(messageId, null)
    }

    /**
     * Get reaction for a specific message.
     */
    suspend fun getReaction(messageId: String): String? = withContext(Dispatchers.IO) {
        val message = messageDao.getMessageById(messageId)
        message?.reaction
    }
}
