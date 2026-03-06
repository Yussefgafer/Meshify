package com.p2p.meshify.domain.usecase

import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.domain.repository.IChatRepository
import kotlinx.coroutines.flow.Flow

/**
 * Collection of Use Cases for Chat logic.
 */
class GetMessagesUseCase(private val repository: IChatRepository) {
    /**
     * Now supports real offset for pagination.
     */
    operator fun invoke(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> {
        return repository.getMessagesPaged(chatId, limit, offset)
    }
}

class SendMessageUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(peerId: String, peerName: String, text: String): Result<Unit> {
        if (text.isBlank()) return Result.failure(Exception("Message cannot be empty"))
        return repository.sendMessage(peerId, peerName, text)
    }
}

class DeleteMessagesUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(messageIds: List<String>) {
        repository.deleteMessages(messageIds)
    }
}
