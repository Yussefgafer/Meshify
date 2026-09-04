package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerDeviceTest {

    @Test
    fun signalStrength_fromRssi() {
        assertEquals(
            SignalStrength.STRONG,
            PeerDevice(id = "1", name = "n", address = "a", rssi = -40).signalStrength
        )
        assertEquals(
            SignalStrength.MEDIUM,
            PeerDevice(id = "1", name = "n", address = "a", rssi = -60).signalStrength
        )
        assertEquals(
            SignalStrength.WEAK,
            PeerDevice(id = "1", name = "n", address = "a", rssi = -80).signalStrength
        )
    }

    @Test
    fun signalStrength_nullRssiDependsOnConnection() {
        assertEquals(
            SignalStrength.MEDIUM,
            PeerDevice(id = "1", name = "n", address = "a", rssi = null, isConnected = true).signalStrength
        )
        assertEquals(
            SignalStrength.WEAK,
            PeerDevice(id = "1", name = "n", address = "a", rssi = null, isConnected = false).signalStrength
        )
    }

    @Test
    fun transportType_enumIsComplete() {
        assertEquals(3, TransportType.values().size)
        TransportType.values().forEach { assertTrue(it.name.isNotEmpty()) }
    }
}
