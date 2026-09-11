package com.p2p.meshify.core.ui.model

import com.p2p.meshify.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageUiModelTest {

    @Test
    fun `default timestamp is zero and copy preserves id and type`() {
        val message = MessageUiModel(
            id = "msg-1",
            text = "hi",
            type = MessageType.TEXT
        )

        assertEquals("msg-1", message.id)
        assertEquals("hi", message.text)
        assertEquals(MessageType.TEXT, message.type)
        assertEquals(0L, message.timestamp)

        val copy = message.copy(timestamp = 42L)
        assertEquals("msg-1", copy.id)
        assertEquals("hi", copy.text)
        assertEquals(MessageType.TEXT, copy.type)
        assertEquals(42L, copy.timestamp)
    }
}
