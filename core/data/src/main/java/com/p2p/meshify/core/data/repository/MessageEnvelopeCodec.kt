package com.p2p.meshify.core.data.repository

import com.p2p.meshify.domain.security.model.MessageEnvelope
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

// Single wire format for TEXT payloads: [short senderLen][sender]
// [short recipientLen][recipient][int textLen][text][long timestamp]
// [short typeLen][type] — all UTF-8. Shared by the live send path and the
// offline retry path so a queued message replays in exactly the frame shape
// it was first sent with.

internal fun serializeMessageEnvelope(envelope: MessageEnvelope): ByteArray {
    val textBytes = envelope.text.toByteArray(Charsets.UTF_8)
    val senderIdBytes = envelope.senderId.toByteArray(Charsets.UTF_8)
    val recipientIdBytes = envelope.recipientId.toByteArray(Charsets.UTF_8)
    val messageTypeBytes = envelope.messageType.toByteArray(Charsets.UTF_8)

    val totalSize = 2 + senderIdBytes.size +
            2 + recipientIdBytes.size +
            4 + textBytes.size +
            8 +
            2 + messageTypeBytes.size

    return ByteBuffer.allocate(totalSize).apply {
        putShort(senderIdBytes.size.toShort())
        put(senderIdBytes)
        putShort(recipientIdBytes.size.toShort())
        put(recipientIdBytes)
        putInt(textBytes.size)
        put(textBytes)
        putLong(envelope.timestamp)
        putShort(messageTypeBytes.size.toShort())
        put(messageTypeBytes)
    }.array()
}

internal fun deserializeMessageEnvelope(data: ByteArray): MessageEnvelope {
    val buffer = ByteBuffer.wrap(data)

    fun readSizedBytes(len: Int): ByteArray {
        if (len < 0 || len > buffer.remaining()) {
            throw IllegalArgumentException(
                "Malformed envelope: declared length $len exceeds remaining ${buffer.remaining()} bytes"
            )
        }
        return ByteArray(len).also { buffer.get(it) }
    }

    try {
        val senderIdLen = buffer.short.toInt()
        val senderId = String(readSizedBytes(senderIdLen), Charsets.UTF_8)

        val recipientIdLen = buffer.short.toInt()
        val recipientId = String(readSizedBytes(recipientIdLen), Charsets.UTF_8)

        val textLen = buffer.int
        val text = String(readSizedBytes(textLen), Charsets.UTF_8)

        val timestamp = buffer.long

        val messageTypeLen = buffer.short.toInt()
        val messageType = String(readSizedBytes(messageTypeLen), Charsets.UTF_8)

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            text = text,
            timestamp = timestamp,
            messageType = messageType
        )
    } catch (e: BufferUnderflowException) {
        throw IllegalArgumentException("Malformed envelope: truncated data (${data.size} bytes)", e)
    }
}
