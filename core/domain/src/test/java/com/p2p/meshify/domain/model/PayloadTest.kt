package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class PayloadTest {

    @Test
    fun equals_isBasedOnIdOnly() {
        val id = "same-id"
        val p1 = Payload(id = id, senderId = "a", timestamp = 1, type = Payload.PayloadType.TEXT, data = byteArrayOf(1))
        val p2 = Payload(id = id, senderId = "b", timestamp = 2, type = Payload.PayloadType.FILE, data = byteArrayOf(2, 3))
        assertEquals(p1, p2)
    }

    @Test
    fun notEquals_whenIdsDiffer() {
        val p1 = Payload(id = "x", senderId = "a", timestamp = 1, type = Payload.PayloadType.TEXT, data = byteArrayOf(1))
        val p2 = Payload(id = "y", senderId = "a", timestamp = 1, type = Payload.PayloadType.TEXT, data = byteArrayOf(1))
        assertFalse(p1 == p2)
    }

    @Test
    fun hashCode_equalWhenIdsEqual() {
        val id = "same-id"
        val p1 = Payload(id = id, senderId = "a", type = Payload.PayloadType.TEXT, data = byteArrayOf(1))
        val p2 = Payload(id = id, senderId = "b", type = Payload.PayloadType.FILE, data = byteArrayOf(2))
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun payloadType_hasNineValues() {
        assertEquals(9, Payload.PayloadType.values().size)
    }

    @Test
    fun payloadType_exhaustiveWhen_compilesForAll() {
        for (type in Payload.PayloadType.values()) {
            val label = when (type) {
                Payload.PayloadType.TEXT -> "text"
                Payload.PayloadType.FILE -> "file"
                Payload.PayloadType.HANDSHAKE -> "handshake"
                Payload.PayloadType.SYSTEM_CONTROL -> "system_control"
                Payload.PayloadType.DELETE_REQUEST -> "delete_request"
                Payload.PayloadType.REACTION -> "reaction"
                Payload.PayloadType.AVATAR_REQUEST -> "avatar_request"
                Payload.PayloadType.AVATAR_RESPONSE -> "avatar_response"
                Payload.PayloadType.VIDEO -> "video"
            }
            assertNotNull(label)
        }
    }
}
