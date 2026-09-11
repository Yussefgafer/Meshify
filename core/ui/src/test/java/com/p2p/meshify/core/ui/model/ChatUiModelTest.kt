package com.p2p.meshify.core.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatUiModelTest {

    @Test
    fun `defaults are zeroed unread count and zero timestamp`() {
        val chat = ChatUiModel(
            peerId = "peer-1",
            peerName = "Peer One",
            lastMessage = "hello"
        )

        assertEquals("peer-1", chat.peerId)
        assertEquals("Peer One", chat.peerName)
        assertEquals("hello", chat.lastMessage)
        assertEquals(0L, chat.lastTimestamp)
        assertEquals(0, chat.unreadCount)
    }

    @Test
    fun `copy overrides only the targeted fields`() {
        val chat = ChatUiModel(
            peerId = "peer-1",
            peerName = "Peer One",
            lastMessage = null,
            lastTimestamp = 10L,
            unreadCount = 3
        )

        val copy = chat.copy(unreadCount = 0)

        assertEquals("peer-1", copy.peerId)
        assertEquals("Peer One", copy.peerName)
        assertEquals(null, copy.lastMessage)
        assertEquals(10L, copy.lastTimestamp)
        assertEquals(0, copy.unreadCount)
    }

    @Test
    fun `missing lastMessage is distinguishable from empty lastMessage`() {
        val nullMessage = ChatUiModel("p", "P", null)
        val emptyMessage = ChatUiModel("p", "P", "")

        assertNotEquals(nullMessage, emptyMessage)
    }
}
