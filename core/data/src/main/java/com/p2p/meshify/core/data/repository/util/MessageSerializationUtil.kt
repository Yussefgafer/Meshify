package com.p2p.meshify.core.data.repository.util

import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.security.model.MessageEnvelope
import java.nio.ByteBuffer

/**
 * Utility object for message serialization and helper functions.
 * Extracted from MessageSendingServiceImpl to reduce file size and improve maintainability.
 */
object MessageSerializationUtil {

    /**
     * Serialize a MessageEnvelope to ByteArray for network transmission.
     */
    fun serializeEnvelope(envelope: MessageEnvelope): ByteArray {
        val senderIdBytes = envelope.senderId.toByteArray(Charsets.UTF_8)
        val recipientIdBytes = envelope.recipientId.toByteArray(Charsets.UTF_8)

        return buildPacket(
            senderIdBytes,
            recipientIdBytes,
            envelope.nonce,
            envelope.timestamp,
            envelope.iv,
            envelope.ciphertext,
            envelope.signature
        )
    }

    /**
     * Build a network packet with proper formatting.
     */
    fun buildPacket(
        senderIdBytes: ByteArray,
        recipientIdBytes: ByteArray,
        nonce: ByteArray,
        timestamp: Long,
        iv: ByteArray,
        ciphertext: ByteArray,
        signature: ByteArray
    ): ByteArray {
        val totalSize = 2 + senderIdBytes.size +
                2 + recipientIdBytes.size +
                2 + nonce.size +
                8 +
                2 + iv.size +
                4 + ciphertext.size +
                2 + signature.size

        val buffer = ByteBuffer.allocate(totalSize).apply {
            putShort(senderIdBytes.size.toShort())
            put(senderIdBytes)
            putShort(recipientIdBytes.size.toShort())
            put(recipientIdBytes)
            putShort(nonce.size.toShort())
            put(nonce)
            putLong(timestamp)
            putShort(iv.size.toShort())
            put(iv)
            putInt(ciphertext.size.toInt())
            put(ciphertext)
            putShort(signature.size.toShort())
            put(signature)
        }
        return buffer.array()
    }

    /**
     * Build forward context string for a message.
     */
    fun buildForwardContext(
        stringProvider: StringResourceProvider,
        original: MessageEntity
    ): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(original.timestamp))
        val unknown = stringProvider.getString(R.string.label_unknown)
        val forwarded = stringProvider.getString(R.string.label_forwarded)
        val timestampFmt = stringProvider.getString(R.string.label_timestamp_format, timestamp)

        return when (original.type) {
            MessageType.TEXT -> {
                val preview = original.text?.take(100) ?: ""
                "$forwarded \"$preview\"$timestampFmt"
            }
            MessageType.IMAGE -> "$forwarded ${stringProvider.getString(R.string.forward_image_label)}$timestampFmt"
            MessageType.VIDEO -> "$forwarded ${stringProvider.getString(R.string.forward_video_label)}$timestampFmt"
            MessageType.AUDIO -> "$forwarded ${stringProvider.getString(R.string.forward_audio_label)}$timestampFmt"
            MessageType.FILE -> "$forwarded ${stringProvider.getString(R.string.forward_file_label, original.text ?: unknown)}$timestampFmt"
            MessageType.DOCUMENT -> "$forwarded ${stringProvider.getString(R.string.forward_document_label)}$timestampFmt"
            MessageType.ARCHIVE -> "$forwarded ${stringProvider.getString(R.string.forward_archive_label, original.text ?: unknown)}$timestampFmt"
            MessageType.APK -> "$forwarded ${stringProvider.getString(R.string.forward_apk_label, original.text ?: unknown)}$timestampFmt"
        }
    }

    /**
     * Parse peer name from raw handshake data.
     */
    fun parseName(peerName: String): String {
        return peerName.substringBefore(" (").trim()
    }
}
