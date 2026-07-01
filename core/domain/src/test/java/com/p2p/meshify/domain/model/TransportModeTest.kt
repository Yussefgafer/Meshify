package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TransportMode enum.
 */
class TransportModeTest {

    @Test
    fun `TransportMode contains all expected values`() {
        val expected = listOf(
            TransportMode.MULTI_PATH,
            TransportMode.LAN_ONLY,
            TransportMode.BLE_ONLY,
            TransportMode.AUTO
        )

        assertEquals(expected.size, TransportMode.values().size)
        assertTrue(TransportMode.values().toList().containsAll(expected))
    }

    @Test
    fun `TransportMode values have unique names`() {
        val names = TransportMode.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `MULTI_PATH has correct description`() {
        assertEquals("LAN + Bluetooth simultaneously", TransportMode.MULTI_PATH.description)
    }

    @Test
    fun `LAN_ONLY has correct description`() {
        assertEquals("Wi-Fi / Ethernet only", TransportMode.LAN_ONLY.description)
    }

    @Test
    fun `BLE_ONLY has correct description`() {
        assertEquals("Short-range Bluetooth only", TransportMode.BLE_ONLY.description)
    }

    @Test
    fun `AUTO has correct description`() {
        assertEquals("System picks best available", TransportMode.AUTO.description)
    }

    @Test
    fun `MULTI_PATH has ordinal 0`() {
        assertEquals(0, TransportMode.MULTI_PATH.ordinal)
    }

    @Test
    fun `LAN_ONLY has ordinal 1`() {
        assertEquals(1, TransportMode.LAN_ONLY.ordinal)
    }

    @Test
    fun `BLE_ONLY has ordinal 2`() {
        assertEquals(2, TransportMode.BLE_ONLY.ordinal)
    }

    @Test
    fun `AUTO has ordinal 3`() {
        assertEquals(3, TransportMode.AUTO.ordinal)
    }

    @Test
    fun `valueOf returns correct enum for valid names`() {
        assertEquals(TransportMode.MULTI_PATH, TransportMode.valueOf("MULTI_PATH"))
        assertEquals(TransportMode.LAN_ONLY, TransportMode.valueOf("LAN_ONLY"))
        assertEquals(TransportMode.BLE_ONLY, TransportMode.valueOf("BLE_ONLY"))
        assertEquals(TransportMode.AUTO, TransportMode.valueOf("AUTO"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf throws for invalid name`() {
        TransportMode.valueOf("INVALID")
    }
}
