package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportModeTest {

    @Test
    fun values_hasFourModes() {
        assertEquals(4, TransportMode.values().size)
    }

    @Test
    fun everyModeHasNonEmptyDescription() {
        TransportMode.values().forEach { assertTrue(it.description.isNotEmpty()) }
    }

    @Test
    fun exhaustiveWhen_resolvesAllModes() {
        for (mode in TransportMode.values()) {
            val label = when (mode) {
                TransportMode.MULTI_PATH -> "multi"
                TransportMode.LAN_ONLY -> "lan"
                TransportMode.BLE_ONLY -> "ble"
                TransportMode.AUTO -> "auto"
            }
            assertNotNull(label)
        }
    }
}
