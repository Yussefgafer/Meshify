package com.p2p.meshify.core.ui.model

import com.p2p.meshify.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachmentUiModelTest {

    @Test
    fun `all properties are exposed and retained through copy`() {
        val model = AttachmentUiModel(
            id = "attachment-1",
            type = MessageType.IMAGE,
            filePath = "/data/local/attachment-1.jpg"
        )

        val copy = model.copy(filePath = "/data/local/attachment-1-copy.jpg")

        assertEquals("attachment-1", copy.id)
        assertEquals(MessageType.IMAGE, copy.type)
        assertEquals("/data/local/attachment-1-copy.jpg", copy.filePath)
    }

    @Test
    fun `different ids are not equal`() {
        val a = AttachmentUiModel("a", MessageType.TEXT, "/a.txt")
        val b = AttachmentUiModel("b", MessageType.TEXT, "/a.txt")

        assertNotEquals(a, b)
    }
}
