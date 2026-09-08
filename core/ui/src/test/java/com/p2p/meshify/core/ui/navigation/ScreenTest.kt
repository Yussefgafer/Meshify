package com.p2p.meshify.core.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

    @Test
    fun `Chat equals and component copy preserve peerId and peerName`() {
        val route = Screen.Chat("peer-1", "Peer One")
        val copy = route.copy(peerName = "Peer Two")
        assertEquals("peer-1", copy.peerId)
        assertEquals("Peer Two", copy.peerName)
    }

    @Test
    fun `Chat default peerName is null`() {
        val route = Screen.Chat("peer-1")
        assertEquals("peer-1", route.peerId)
        assertEquals(null, route.peerName)
    }

    @Test
    fun `all Screen subclasses are distinct sealed instances`() {
        val all = listOf(
            Screen.Onboarding,
            Screen.Home,
            Screen.Discovery,
            Screen.Settings,
            Screen.Developer,
            Screen.RealDeviceTesting,
            Screen.Chat("p1")
        )
        val distinct = all.toSet()
        assertEquals(all.size, distinct.size)
    }
}
