package com.p2p.meshify.domain.usecase

import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.model.DeleteType

/**
 * Use case for sending messages.
 */
class SendMessageUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(peerId: String, peerName: String, text: String, replyToId: String? = null): Result<Unit> {
        if (text.isBlank()) return Result.failure(Exception("Message cannot be empty"))
        return repository.sendMessage(peerId, peerName, text, replyToId)
    }
}

/**
 * Use case for deleting messages.
 */
class DeleteMessagesUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(messageId: String, deleteType: DeleteType) {
        repository.deleteMessage(messageId, deleteType)
    }
}

// Note: GetMessagesUseCase removed temporarily as it depends on MessageEntity
// This should be refactored to use domain models in the future
