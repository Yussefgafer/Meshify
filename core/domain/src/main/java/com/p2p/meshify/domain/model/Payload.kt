package com.p2p.meshify.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Data disk sent across the mesh network.
 */
data class Payload(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: PayloadType,
    val data: ByteArray
) {
    enum class PayloadType {
        TEXT,
        FILE,
        HANDSHAKE,
        SYSTEM_CONTROL,
        DELETE_REQUEST,
        REACTION,
        DELIVERY_ACK,
        AVATAR_REQUEST,
        AVATAR_RESPONSE,
        VIDEO
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Payload
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Serializable
data class Handshake(
    val version: Int = 2,
    val name: String,
    val avatarHash: String? = null,
    val timestamp: Long = System.currentTimeMillis()
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

/**
 * Extension function for safe enum lookup from String.
 */
fun PayloadTypeFromString(name: String): Payload.PayloadType? {
    return try {
        Payload.PayloadType.valueOf(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Extension function for safe enum lookup from String.
 */
fun String.toPayloadType(): Payload.PayloadType? {
    return PayloadTypeFromString(this)
}
