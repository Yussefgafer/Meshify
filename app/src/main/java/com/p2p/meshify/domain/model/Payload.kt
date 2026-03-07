package com.p2p.meshify.domain.model

import java.util.UUID

/**
 * Data packet sent across the mesh network.
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
        SYSTEM_CONTROL
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
