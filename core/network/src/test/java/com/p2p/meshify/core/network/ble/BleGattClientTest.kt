package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleGattClientTest {

    private lateinit var context: Context
    private lateinit var client: BleGattClient
    private val received = mutableListOf<Pair<String, ByteArray>>()
    private val connChanges = mutableListOf<Pair<String, Boolean>>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        received.clear()
        connChanges.clear()
        client = BleGattClient(
            context = context,
            onPayloadReceived = { id, data -> received.add(id to data) },
            onConnectionStateChanged = { id, connected -> connChanges.add(id to connected) }
        )
    }

    @Test
    fun isConnected_initiallyFalse() {
        assertFalse(client.isConnected("unknown"))
    }

    @Test
    fun getConnectedPeers_initiallyEmpty() {
        assertTrue(client.getConnectedPeers().isEmpty())
    }

    @Test
    fun getNegotiatedMtu_unknownPeer_returnsNull() {
        assertNull(client.getNegotiatedMtu("ghost"))
    }

    @Test
    fun awaitReady_unknownPeer_returnsFalse() = runTest {
        val ready = client.awaitReady("ghost", timeoutMs = 100)
        assertFalse(ready)
    }

    @Test
    fun sendData_notConnected_returnsFailure() = runTest {
        val result = client.sendData("ghost", ByteArray(10))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun cleanup_doesNotThrow() {
        client.cleanup()
        assertTrue(client.getConnectedPeers().isEmpty())
    }

    @Test
    fun connect_securityException_propagatesAndDoesNotLeak() {
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { device.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } throws SecurityException("missing BLUETOOTH_CONNECT")
        var threw = false
        try {
            client.connect(device, "AA:BB:CC:DD:EE:01")
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(client.isConnected("AA:BB:CC:DD:EE:01"))
        assertTrue(client.getConnectedPeers().isEmpty())
    }

    @Test
    fun connect_validDevice_registersAndIsNotYetConnected() {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { device.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns gatt
        client.connect(device, "AA:BB:CC:DD:EE:02")
        assertFalse(client.isConnected("AA:BB:CC:DD:EE:02"))
        assertTrue(client.getConnectedPeers().isEmpty())
        assertNull(client.getNegotiatedMtu("AA:BB:CC:DD:EE:02"))
    }

    @Test
    fun sendData_afterConnectionFaulted_inRegistry_returnsFastFailure() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { device.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns gatt
        client.connect(device, "peerX")

        // Keep the connection in the registry but mark its readiness as failed
        // — the real faulted-connection path, not the trivial `connection == null` branch.
        val connField = BleGattClient::class.java.getDeclaredField("gattConnections")
        connField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = connField.get(client) as java.util.concurrent.ConcurrentHashMap<String, BleGattConnection>
        val conn = map["peerX"]!!
        val readyField = BleGattConnection::class.java.getDeclaredField("characteristicsReady")
        readyField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val ready = readyField.get(conn) as kotlinx.coroutines.CompletableDeferred<Unit>
        ready.completeExceptionally(IllegalStateException("simulated fault"))

        val result = client.sendData("peerX", ByteArray(5))
        assertTrue(result.isFailure)
    }

    @Test
    fun awaitReady_withConnectionButNotReady_timesOut() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { device.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns gatt
        client.connect(device, "peerY")
        val ready = client.awaitReady("peerY", timeoutMs = 50)
        assertFalse(ready)
    }

    @Test
    fun isConnected_afterCleanup_false() {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        every { device.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns gatt
        client.connect(device, "peerZ")
        client.cleanup()
        assertFalse(client.isConnected("peerZ"))
        assertTrue(client.getConnectedPeers().isEmpty())
    }
}
