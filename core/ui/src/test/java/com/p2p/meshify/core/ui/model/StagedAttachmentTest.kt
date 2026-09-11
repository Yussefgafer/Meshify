package com.p2p.meshify.core.ui.model

import android.net.Uri
import com.p2p.meshify.domain.model.MessageType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StagedAttachmentTest {

    @Test
    fun `copy changes bytes while preserving uri and type`() {
        val uri = Uri.parse("content://media/1")
        val original = StagedAttachment(
            uri = uri,
            bytes = byteArrayOf(1, 2, 3),
            type = MessageType.IMAGE
        )

        val copy = original.copy(bytes = byteArrayOf(4, 5, 6))

        assertEquals(uri, copy.uri)
        assertEquals(MessageType.IMAGE, copy.type)
        assertArrayEquals(byteArrayOf(4, 5, 6), copy.bytes)
    }

    @Test
    fun `different byte arrays make models unequal`() {
        val uri = Uri.parse("content://media/1")
        val a = StagedAttachment(uri, byteArrayOf(1), MessageType.TEXT)
        val b = StagedAttachment(uri, byteArrayOf(2), MessageType.TEXT)

        assertNotEquals(a, b)
    }
}
