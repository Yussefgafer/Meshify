package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PeerDevice data class and TransportType enum.
 */
class PeerDeviceTest {

    // --- Construction ---

    @Test
    fun `PeerDevice constructed with all params returns correct values`() {
        val device = PeerDevice(
            id = "peer-123",
            name = "Test Device",
            address = "192.168.1.42",
            rssi = -45,
            isConnected = true,
            transportType = TransportType.LAN
        )

        assertEquals("peer-123", device.id)
        assertEquals("Test Device", device.name)
        assertEquals("192.168.1.42", device.address)
        assertEquals(-45, device.rssi)
        assertTrue(device.isConnected)
        assertEquals(TransportType.LAN, device.transportType)
    }

    @Test
    fun `PeerDevice uses default isConnected as false`() {
        val device = PeerDevice(
            id = "peer-1",
            name = "Device",
            address = "10.0.0.1"
        )

        assertFalse(device.isConnected)
    }

    @Test
    fun `PeerDevice uses default transportType as LAN`() {
        val device = PeerDevice(
            id = "peer-1",
            name = "Device",
            address = "10.0.0.1"
        )

        assertEquals(TransportType.LAN, device.transportType)
    }

    @Test
    fun `PeerDevice uses default rssi as null`() {
        val device = PeerDevice(
            id = "peer-1",
            name = "Device",
            address = "10.0.0.1"
        )

        assertNull(device.rssi)
    }

    // --- copy() ---

    @Test
    fun `PeerDevice copy creates equal instance with no overrides`() {
        val device = PeerDevice(
            id = "peer-42",
            name = "Original",
            address = "192.168.1.1",
            rssi = -60,
            isConnected = true,
            transportType = TransportType.BLE
        )

        val copy = device.copy()

        assertEquals(device, copy)
    }

    @Test
    fun `PeerDevice copy overrides specified field`() {
        val device = PeerDevice(
            id = "peer-42",
            name = "Original",
            address = "192.168.1.1"
        )

        val renamed = device.copy(name = "Renamed")

        assertEquals("Renamed", renamed.name)
        assertEquals(device.id, renamed.id)
        assertEquals(device.address, renamed.address)
    }

    // --- equals() / hashCode() ---

    @Test
    fun `PeerDevice equals returns true for same field values`() {
        val device1 = PeerDevice(
            id = "peer-1",
            name = "Same",
            address = "10.0.0.1",
            rssi = -50,
            isConnected = true,
            transportType = TransportType.LAN
        )
        val device2 = PeerDevice(
            id = "peer-1",
            name = "Same",
            address = "10.0.0.1",
            rssi = -50,
            isConnected = true,
            transportType = TransportType.LAN
        )

        assertEquals(device1, device2)
    }

    @Test
    fun `PeerDevice equals returns false for different id`() {
        val device1 = PeerDevice(id = "peer-1", name = "A", address = "10.0.0.1")
        val device2 = PeerDevice(id = "peer-2", name = "A", address = "10.0.0.1")

        assertNotEquals(device1, device2)
    }

    @Test
    fun `PeerDevice equals returns false for different name`() {
        val device1 = PeerDevice(id = "peer-1", name = "Alpha", address = "10.0.0.1")
        val device2 = PeerDevice(id = "peer-1", name = "Beta", address = "10.0.0.1")

        assertNotEquals(device1, device2)
    }

    @Test
    fun `PeerDevice hashCode is consistent for equal instances`() {
        val device1 = PeerDevice(id = "peer-1", name = "HashTest", address = "10.0.0.1")
        val device2 = PeerDevice(id = "peer-1", name = "HashTest", address = "10.0.0.1")

        assertEquals(device1.hashCode(), device2.hashCode())
    }

    @Test
    fun `PeerDevice hashCode differs for unequal instances`() {
        val device1 = PeerDevice(id = "peer-1", name = "A", address = "10.0.0.1")
        val device2 = PeerDevice(id = "peer-2", name = "A", address = "10.0.0.1")

        assertNotEquals(device1.hashCode(), device2.hashCode())
    }

    // --- toString() ---

    @Test
    fun `PeerDevice toString contains key fields`() {
        val device = PeerDevice(
            id = "peer-x",
            name = "MyPhone",
            address = "192.168.1.5"
        )

        val str = device.toString()

        assertTrue(str.contains("peer-x"))
        assertTrue(str.contains("MyPhone"))
        assertTrue(str.contains("192.168.1.5"))
    }

    // --- signalStrength ---

    @Test
    fun `signalStrength returns STRONG for RSSI greater than -50`() {
        val device = PeerDevice(
            id = "p1", name = "Strong", address = "a",
            rssi = -40, isConnected = true
        )

        assertEquals(SignalStrength.STRONG, device.signalStrength)
    }

    @Test
    fun `signalStrength returns MEDIUM for RSSI between -70 and -50 inclusive`() {
        val device = PeerDevice(
            id = "p1", name = "Medium", address = "a",
            rssi = -60, isConnected = true
        )

        assertEquals(SignalStrength.MEDIUM, device.signalStrength)
    }

    @Test
    fun `signalStrength returns WEAK for RSSI less than -70`() {
        val device = PeerDevice(
            id = "p1", name = "Weak", address = "a",
            rssi = -80, isConnected = true
        )

        assertEquals(SignalStrength.WEAK, device.signalStrength)
    }

    @Test
    fun `signalStrength returns MEDIUM when RSSI null and connected`() {
        val device = PeerDevice(
            id = "p1", name = "Connected", address = "a",
            rssi = null, isConnected = true
        )

        assertEquals(SignalStrength.MEDIUM, device.signalStrength)
    }

    @Test
    fun `signalStrength returns WEAK when RSSI null and not connected`() {
        val device = PeerDevice(
            id = "p1", name = "Disconnected", address = "a",
            rssi = null, isConnected = false
        )

        assertEquals(SignalStrength.WEAK, device.signalStrength)
    }

    // --- TransportType enum ---

    @Test
    fun `TransportType contains all expected values`() {
        assertEquals(3, TransportType.values().size)
        assertTrue(TransportType.values().toList().containsAll(listOf(
            TransportType.LAN,
            TransportType.BLE,
            TransportType.BOTH
        )))
    }

    @Test
    fun `TransportType valueOf returns correct enum for valid names`() {
        assertEquals(TransportType.LAN, TransportType.valueOf("LAN"))
        assertEquals(TransportType.BLE, TransportType.valueOf("BLE"))
        assertEquals(TransportType.BOTH, TransportType.valueOf("BOTH"))
    }

    @Test
    fun `TransportType LAN has expected ordinal`() {
        assertEquals(0, TransportType.LAN.ordinal)
    }

    @Test
    fun `TransportType BLE has expected ordinal`() {
        assertEquals(1, TransportType.BLE.ordinal)
    }

    @Test
    fun `TransportType BOTH has expected ordinal`() {
        assertEquals(2, TransportType.BOTH.ordinal)
    }
}
