package com.p2p.meshify.domain.usecase

import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.model.DeleteType
import kotlinx.coroutines.flow.Flow

class GetMessagesUseCase(private val repository: IChatRepository) {
    operator fun invoke(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> {
        return repository.getMessagesPaged(chatId, limit, offset)
    }
}

class SendMessageUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(peerId: String, peerName: String, text: String, replyToId: String? = null): Result<Unit> {
        if (text.isBlank()) return Result.failure(Exception("Message cannot be empty"))
        return repository.sendMessage(peerId, peerName, text, replyToId)
    }
}

class DeleteMessagesUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(messageId: String, deleteType: DeleteType) {
        repository.deleteMessage(messageId, deleteType)
    }
}
