package com.p2p.meshify.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Data disk sent across the mesh network.
 */
@Serializable
data class Payload(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: PayloadType,
    val data: ByteArray,
    val isEncrypted: Boolean = false
) {
    // ByteArray forces manual equals/hashCode — data class would use reference equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payload) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    enum class PayloadType {
        TEXT,
        FILE,
        HANDSHAKE,
        SYSTEM_CONTROL,
        DELETE_REQUEST,
        REACTION,
        AVATAR_REQUEST,
        AVATAR_RESPONSE,
        VIDEO
    }
}

@Serializable
data class Handshake(
    val version: Int = 4,
    val name: String,
    val avatarHash: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val publicKeyBase64: String? = null
)

@Serializable
enum class DeleteType {
    DELETE_FOR_ME,
    DELETE_FOR_EVERYONE
}

@Serializable
data class DeleteRequest(
    val messageId: String,
    val deleteType: DeleteType,
    val deletedBy: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ReactionUpdate(
    val messageId: String,
    val reaction: String?,
    val senderId: String
)

