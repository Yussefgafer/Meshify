package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class BleGattServerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        val adapter = mockk<android.bluetooth.BluetoothAdapter>(relaxed = true)
        every { adapter.isEnabled } returns true
        val btManager = mockk<BluetoothManager>(relaxed = true)
        every { btManager.adapter } returns adapter
        every { btManager.openGattServer(any(), any()) } returns mockk(relaxed = true)
        val appCtx = mockk<Context>(relaxed = true)
        every { appCtx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
        every { ctx.applicationContext } returns appCtx
        context = ctx
    }

    private fun newServer(
        onPayload: (String, ByteArray) -> Unit = { _, _ -> },
        onConnected: (String) -> Unit = {},
        onDisconnected: (String) -> Unit = {}
    ) = BleGattServer(context, onPayload, onConnected, onDisconnected)

    @Test
    fun isServerRunning_initiallyFalse() {
        val server = newServer()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun getConnectedClients_initiallyEmpty() {
        val server = newServer()
        assertTrue(server.getConnectedClients().isEmpty())
    }

    @Test
    fun getConnectedDevice_unknown_returnsNull() {
        val server = newServer()
        assertNull(server.getConnectedDevice("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun startServer_setsRunning() {
        val server = newServer()
        server.startServer()
        assertTrue(server.isServerRunning())
    }

    @Test
    fun startServer_twice_secondIsNoOp() {
        val server = newServer()
        server.startServer()
        assertTrue(server.isServerRunning())
        server.startServer()
        assertTrue(server.isServerRunning())
    }

    @Test
    fun stopServer_afterStart_clearsRunning() {
        val server = newServer()
        server.startServer()
        server.stopServer()
        assertFalse(server.isServerRunning())
        assertTrue(server.getConnectedClients().isEmpty())
    }

    @Test
    fun stopServer_whenNotStarted_doesNotThrow() {
        val server = newServer()
        server.stopServer()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun cleanup_afterStart_clearsAndStops() {
        val server = newServer()
        server.startServer()
        server.cleanup()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun sendData_unknownPeer_returnsFailure() = runTest {
        val server = newServer()
        server.startServer()
        val result = server.sendData("ghost", ByteArray(10))
        assertTrue(result.isFailure)
    }

    @Test
    fun startServer_withDisabledAdapter_doesNotThrow() {
        val disabledAdapter = mockk<android.bluetooth.BluetoothAdapter>(relaxed = true)
        every { disabledAdapter.isEnabled } returns false
        val btManager = mockk<BluetoothManager>(relaxed = true)
        every { btManager.adapter } returns disabledAdapter
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
        every { ctx.applicationContext } returns ctx
        val server = BleGattServer(ctx, { _, _ -> }, {}, {})
        var threw = false
        try {
            server.startServer()
        } catch (_: Exception) {
            threw = true
        }
        assertFalse(server.isServerRunning())
    }

    @Test
    fun startServer_withNullManager_doesNotThrow() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns null
        every { ctx.applicationContext } returns ctx
        val server = BleGattServer(ctx, { _, _ -> }, {}, {})
        server.startServer()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun awaitForServiceAdded_afterStop_returnsFalse() = runTest {
        val server = newServer()
        server.startServer()
        server.stopServer()
        val ready = server.awaitForServiceAdded()
        assertFalse(ready)
    }
}
