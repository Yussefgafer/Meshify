package com.p2p.meshify.core.util

import com.p2p.meshify.domain.model.Payload
import java.nio.ByteBuffer
import java.util.*

/**
 * Enhanced Utility for serializing and deserializing Payload objects.
 * Wire Format V2: [Int: TotalLength] [Int: Version] [Long: Timestamp] [Int: TypeOrdinal] [UUID: MessageID (16 bytes)] [UUID: SenderID (16 bytes)] [Data: Remaining]
 */
object PayloadSerializer {

    private const val CURRENT_VERSION = 2
    private const val HEADER_SIZE = 4 + 4 + 8 + 4 + 16 + 16 // Length + Version + Timestamp + Type + MsgID + SenderID

    fun serialize(payload: Payload): ByteArray {
        val dataSize = payload.data.size
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize)
        
        buffer.putInt(HEADER_SIZE + dataSize)
        buffer.putInt(CURRENT_VERSION)
        buffer.putLong(payload.timestamp)
        buffer.putInt(payload.type.ordinal)
        
        // Message ID - Safety first
        val msgUuid = try { UUID.fromString(payload.id) } catch (e: Exception) { UUID.randomUUID() }
        buffer.putLong(msgUuid.mostSignificantBits)
        buffer.putLong(msgUuid.leastSignificantBits)

        // Sender ID - Safety first
        val senderUuid = try { UUID.fromString(payload.senderId) } catch (e: Exception) { UUID.randomUUID() }
        buffer.putLong(senderUuid.mostSignificantBits)
        buffer.putLong(senderUuid.leastSignificantBits)
        
        buffer.put(payload.data)
        
        return buffer.array()
    }

    fun deserialize(bytes: ByteArray): Payload {
        val buffer = ByteBuffer.wrap(bytes)
        
        buffer.getInt() // Total Length
        val version = buffer.getInt()
        
        // Handle different versions if needed in future
        val timestamp = buffer.getLong()
        val typeOrdinal = buffer.getInt()
        val type = Payload.PayloadType.values().getOrElse(typeOrdinal) { Payload.PayloadType.SYSTEM_CONTROL }
        
        // Message ID
        val msgMsb = buffer.getLong()
        val msgLsb = buffer.getLong()
        val msgId = UUID(msgMsb, msgLsb).toString()

        // Sender ID
        val senderMsb = buffer.getLong()
        val senderLsb = buffer.getLong()
        val senderId = UUID(senderMsb, senderLsb).toString()
        
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        return Payload(
            id = msgId,
            senderId = senderId,
            timestamp = timestamp,
            type = type,
            data = data
        )
    }
}
