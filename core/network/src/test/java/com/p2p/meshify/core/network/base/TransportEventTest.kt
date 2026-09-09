package com.p2p.meshify.core.network.base

import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransportEventTest {

    @Test
    fun deviceDiscovered_equalityAndCopy() {
        val a = TransportEvent.DeviceDiscovered(
            deviceId = "peerA",
            deviceName = "Alice",
            address = "10.0.0.2",
            avatarHash = "hash1",
            rssi = -55,
            transportType = TransportType.LAN
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = a.copy(deviceId = "peerB")
        assertNotEquals(a, c)
    }

    @Test
    fun deviceDiscovered_defaults() {
        val e = TransportEvent.DeviceDiscovered(
            deviceId = "x",
            deviceName = "Name",
            address = "1.2.3.4"
        )
        assertNull(e.avatarHash)
        assertNull(e.rssi)
        assertEquals(TransportType.LAN, e.transportType)
    }

    @Test
    fun deviceDiscovered_withBleFields() {
        val e = TransportEvent.DeviceDiscovered(
            deviceId = "ble_peer",
            deviceName = "Bob",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -72,
            transportType = TransportType.BLE
        )
        assertEquals(TransportType.BLE, e.transportType)
        assertEquals(-72, e.rssi)
    }

    @Test
    fun deviceLost_equality() {
        val a = TransportEvent.DeviceLost("peerA")
        val b = TransportEvent.DeviceLost("peerA")
        val c = TransportEvent.DeviceLost("peerB")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun connectionEstablished_equality() {
        val a = TransportEvent.ConnectionEstablished("peerA")
        val b = TransportEvent.ConnectionEstablished("peerA")
        assertEquals(a, b)
        assertNotEquals(a, TransportEvent.ConnectionEstablished("peerB"))
    }

    @Test
    fun connectionLost_fields() {
        val e = TransportEvent.ConnectionLost("peerA", "timeout")
        assertEquals("peerA", e.deviceId)
        assertEquals("timeout", e.reason)
        val withNull = TransportEvent.ConnectionLost("peerA", null)
        assertNull(withNull.reason)
        assertNotEquals(e, withNull)
        assertEquals(withNull, withNull.copy())
    }

    @Test
    fun payloadReceived_equalityByPayloadId() {
        val p1 = Payload(
            id = "id-1",
            senderId = "peerA",
            type = Payload.PayloadType.TEXT,
            data = "hello".toByteArray()
        )
        val p2 = Payload(
            id = "id-1",
            senderId = "peerA",
            type = Payload.PayloadType.TEXT,
            data = "different bytes but same id".toByteArray()
        )
        val e1 = TransportEvent.PayloadReceived("peerA", p1)
        val e2 = TransportEvent.PayloadReceived("peerA", p2)
        assertEquals(e1, e2)
        val e3 = TransportEvent.PayloadReceived("peerA", p1.copy(id = "id-2"))
        assertNotEquals(e1, e3)
    }

    @Test
    fun error_fields() {
        val ex = RuntimeException("boom")
        val e = TransportEvent.Error("something failed", ex)
        assertEquals("something failed", e.message)
        assertEquals(ex, e.exception)
        val noEx = TransportEvent.Error("no cause")
        assertNull(noEx.exception)
        assertNotEquals(e, noEx)
    }

    @Test
    fun sealedWhen_exhaustive() {
        val events: List<TransportEvent> = listOf(
            TransportEvent.DeviceDiscovered("a", "A", "addr"),
            TransportEvent.DeviceLost("a"),
            TransportEvent.ConnectionEstablished("a"),
            TransportEvent.ConnectionLost("a", null),
            TransportEvent.PayloadReceived("a", Payload(senderId = "a", type = Payload.PayloadType.TEXT, data = ByteArray(0))),
            TransportEvent.Error("e")
        )
        val labels = events.map { e ->
            when (e) {
                is TransportEvent.DeviceDiscovered -> "discovered"
                is TransportEvent.DeviceLost -> "lost"
                is TransportEvent.ConnectionEstablished -> "established"
                is TransportEvent.ConnectionLost -> "connLost"
                is TransportEvent.PayloadReceived -> "payload"
                is TransportEvent.Error -> "error"
            }
        }
        assertEquals(listOf("discovered", "lost", "established", "connLost", "payload", "error"), labels)
    }

    @Test
    fun deviceDiscovered_copyPreservesAllFields() {
        val original = TransportEvent.DeviceDiscovered("id", "name", "addr", "hash", -60, TransportType.BLE)
        val copy = original.copy(rssi = -80)
        assertEquals("id", copy.deviceId)
        assertEquals("name", copy.deviceName)
        assertEquals("hash", copy.avatarHash)
        assertEquals(-80, copy.rssi)
        assertEquals(TransportType.BLE, copy.transportType)
    }
}
