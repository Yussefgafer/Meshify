package com.p2p.meshify.core.data.repository

import com.p2p.meshify.domain.security.model.MessageEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEnvelopeCodecTest {

    private fun makeEnvelope(
        senderId: String = "sender-1",
        recipientId: String = "recipient-1",
        text: String = "hello",
        timestamp: Long = 1_700_000_000_000L,
        messageType: String = "text"
    ): MessageEnvelope = MessageEnvelope(
        senderId = senderId,
        recipientId = recipientId,
        text = text,
        timestamp = timestamp,
        messageType = messageType
    )

    @Test
    fun roundTrip_preservesAllFields() {
        val original = makeEnvelope(
            senderId = "alice",
            recipientId = "bob",
            text = "hi there",
            timestamp = 42L,
            messageType = "text"
        )
        val bytes = serializeMessageEnvelope(original)
        val decoded = deserializeMessageEnvelope(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun roundTrip_emptyStrings() {
        val original = makeEnvelope(senderId = "", recipientId = "", text = "", messageType = "")
        val bytes = serializeMessageEnvelope(original)
        val decoded = deserializeMessageEnvelope(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun roundTrip_unicodeText() {
        val original = makeEnvelope(text = "مرحبا — 你好 — 😀", messageType = "text")
        val bytes = serializeMessageEnvelope(original)
        val decoded = deserializeMessageEnvelope(bytes)
        assertEquals("مرحبا — 你好 — 😀", decoded.text)
    }

    @Test
    fun roundTrip_longSenderAndRecipient() {
        val original = makeEnvelope(
            senderId = "s".repeat(500),
            recipientId = "r".repeat(500),
            text = "x"
        )
        val bytes = serializeMessageEnvelope(original)
        val decoded = deserializeMessageEnvelope(bytes)
        assertEquals(original.senderId, decoded.senderId)
        assertEquals(original.recipientId, decoded.recipientId)
    }

    @Test
    fun truncated_throwsIllegalArgumentException() {
        val original = makeEnvelope()
        val bytes = serializeMessageEnvelope(original)
        // Drop the last 4 bytes to simulate truncation
        val truncated = bytes.copyOfRange(0, bytes.size - 4)
        assertThrows(IllegalArgumentException::class.java) {
            deserializeMessageEnvelope(truncated)
        }
    }

    @Test
    fun emptyArray_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            deserializeMessageEnvelope(ByteArray(0))
        }
    }

    @Test
    fun declaredLengthExceedsRemaining_throws() {
        val original = makeEnvelope(senderId = "alice", text = "hi")
        val bytes = serializeMessageEnvelope(original)
        // The first short is senderId length. Force it to a huge value that
        // exceeds buffer.remaining() so readSizedBytes throws.
        val tampered = bytes.copyOf()
        tampered[0] = 0x7F.toByte()
        tampered[1] = 0x00.toByte() // senderIdLen = 32512
        val ex = assertThrows(IllegalArgumentException::class.java) {
            deserializeMessageEnvelope(tampered)
        }
        assertTrue(
            "expected message about declared length, got: ${ex.message}",
            ex.message!!.contains("exceeds remaining")
        )
    }
}
