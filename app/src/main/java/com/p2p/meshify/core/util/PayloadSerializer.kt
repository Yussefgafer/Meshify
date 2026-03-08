package com.p2p.meshify.core.util

import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.toPayloadType
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Enhanced Utility for serializing and deserializing Payload objects.
 *
 * Wire Format V3 (Current): [Int: TotalLength] [Int: Version] [Long: Timestamp]
 *   [Int: TypeLength] [String: TypeName] [UUID: MessageID (16 bytes)]
 *   [UUID: SenderID (16 bytes)] [Data: Remaining]
 *
 * Wire Format V2 (Legacy): [Int: TotalLength] [Int: Version] [Long: Timestamp]
 *   [Int: TypeOrdinal] [UUID: MessageID (16 bytes)] [UUID: SenderID (16 bytes)]
 *   [Data: Remaining]
 *
 * V3 adds explicit version bump for string-based type encoding to maintain
 * backward compatibility with V2 ordinal-based payloads during mixed-version rollout.
 */
object PayloadSerializer {

    private const val CURRENT_VERSION = 3
    private const val V2_VERSION = 2
    private const val MAX_TYPE_LENGTH = 64 // Safety bound for type string
    private const val MIN_PAYLOAD_SIZE = 4 + 4 + 8 // Minimum: length + version + timestamp
    private const val MAX_DATA_SIZE = 10 * 1024 * 1024 // 10MB max data size safety bound

    /**
     * Result wrapper for deserialization to handle failures safely.
     */
    sealed class DeserializeResult {
        data class Success(val payload: Payload) : DeserializeResult()
        data class Error(val reason: String, val bytes: ByteArray) : DeserializeResult()
    }

    fun serialize(payload: Payload): ByteArray {
        val typeBytes = payload.type.name.toByteArray()
        val dataSize = payload.data.size
        val headerSize = 4 + 4 + 8 + 4 + typeBytes.size + 16 + 16 // Length + Version + Timestamp + TypeLength + TypeName + MsgID + SenderID
        val buffer = ByteBuffer.allocate(headerSize + dataSize)

        buffer.putInt(headerSize + dataSize)
        buffer.putInt(CURRENT_VERSION)
        buffer.putLong(payload.timestamp)
        buffer.putInt(typeBytes.size)
        buffer.put(typeBytes)

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
        return when (val result = deserializeSafe(bytes)) {
            is DeserializeResult.Success -> result.payload
            is DeserializeResult.Error -> {
                // Log corrupted payload for debugging
                Logger.w("PayloadSerializer -> Corrupted payload received: ${result.reason}")
                // Return a safe empty payload instead of preserving corrupted data
                Payload(
                    id = UUID.randomUUID().toString(),
                    senderId = "unknown",
                    timestamp = System.currentTimeMillis(),
                    type = Payload.PayloadType.SYSTEM_CONTROL,
                    data = ByteArray(0) // Empty data instead of corrupted bytes
                )
            }
        }
    }

    /**
     * Safe deserialization with bounds checking and version validation.
     * Returns a Result wrapper instead of throwing exceptions.
     */
    fun deserializeSafe(bytes: ByteArray): DeserializeResult {
        // Minimum size check
        if (bytes.size < MIN_PAYLOAD_SIZE) {
            return DeserializeResult.Error("Payload too small: ${bytes.size} bytes", bytes)
        }

        val buffer = ByteBuffer.wrap(bytes)

        try {
            val totalLength = buffer.getInt()
            
            // Validate total length
            if (totalLength <= 0 || totalLength > bytes.size) {
                return DeserializeResult.Error("Invalid payload length: $totalLength (actual: ${bytes.size})", bytes)
            }

            val version = buffer.getInt()

            // Check remaining bytes before reading timestamp
            if (buffer.remaining() < 8) {
                return DeserializeResult.Error("Insufficient bytes for timestamp", bytes)
            }
            val timestamp = buffer.long

            // Branch on version for backward compatibility with bounds checking
            val type = when (version) {
                V2_VERSION -> {
                    // V2: Type encoded as ordinal (Int)
                    if (buffer.remaining() < 4) {
                        return DeserializeResult.Error("Insufficient bytes for V2 type ordinal", bytes)
                    }
                    val typeOrdinal = buffer.int
                    if (typeOrdinal < 0 || typeOrdinal >= Payload.PayloadType.values().size) {
                        Payload.PayloadType.SYSTEM_CONTROL
                    } else {
                        Payload.PayloadType.values()[typeOrdinal]
                    }
                }
                CURRENT_VERSION -> {
                    // V3: Type encoded as String with length prefix
                    if (buffer.remaining() < 4) {
                        return DeserializeResult.Error("Insufficient bytes for V3 type length", bytes)
                    }
                    val typeLength = buffer.int

                    // Bounds check for type length
                    if (typeLength < 0 || typeLength > MAX_TYPE_LENGTH || typeLength > buffer.remaining()) {
                        // Skip remaining fields safely by returning error
                        return DeserializeResult.Error("Invalid V3 type length: $typeLength", bytes)
                    }

                    val typeBytes = ByteArray(typeLength)
                    buffer.get(typeBytes)
                    // Use explicit UTF-8 charset to avoid platform-dependent decoding
                    val typeName = try {
                        String(typeBytes, StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        Logger.w("PayloadSerializer -> Invalid UTF-8 type name, using default charset")
                        String(typeBytes) // Fallback to default charset
                    }
                    typeName.toPayloadType() ?: Payload.PayloadType.SYSTEM_CONTROL
                }
                else -> {
                    // Unknown version: fail safely
                    return DeserializeResult.Error("Unknown payload version: $version", bytes)
                }
            }

            // Validate remaining bytes for UUIDs (32 bytes) + at least some data
            if (buffer.remaining() < 32) {
                return DeserializeResult.Error("Insufficient bytes for UUIDs", bytes)
            }

            // Message ID
            val msgMsb = buffer.long
            val msgLsb = buffer.long
            val msgId = UUID(msgMsb, msgLsb).toString()

            // Sender ID
            val senderMsb = buffer.long
            val senderLsb = buffer.long
            val senderId = UUID(senderMsb, senderLsb).toString()

            // Data (remaining bytes) with max size check
            val remainingBytes = buffer.remaining()
            if (remainingBytes > MAX_DATA_SIZE) {
                return DeserializeResult.Error(
                    "Payload data too large: $remainingBytes bytes (max: $MAX_DATA_SIZE)",
                    bytes
                )
            }
            val data = ByteArray(remainingBytes)
            buffer.get(data)

            return DeserializeResult.Success(
                Payload(
                    id = msgId,
                    senderId = senderId,
                    timestamp = timestamp,
                    type = type,
                    data = data
                )
            )

        } catch (e: BufferUnderflowException) {
            return DeserializeResult.Error("Buffer underflow: ${e.message}", bytes)
        } catch (e: Exception) {
            return DeserializeResult.Error("Deserialization failed: ${e.message}", bytes)
        }
    }
}
