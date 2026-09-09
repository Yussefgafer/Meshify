package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BleTransportImplTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: ISettingsRepository
    private var isAdapterEnabled = false

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        val adapter = mockk<BluetoothAdapter>(relaxed = true)
        every { adapter.isEnabled } answers { isAdapterEnabled }
        every { adapter.bluetoothLeAdvertiser } returns null
        every { adapter.bluetoothLeScanner } returns null

        val btManager = mockk<BluetoothManager>(relaxed = true)
        every { btManager.adapter } returns adapter

        val appCtx = mockk<Context>(relaxed = true)
        every { appCtx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
        every { appCtx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
        every { ctx.applicationContext } returns appCtx

        context = ctx
        isAdapterEnabled = false

        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.displayName } returns MutableStateFlow("TestDevice")
        every { settingsRepository.isNetworkVisible } returns MutableStateFlow(true)
        coEvery { settingsRepository.getDeviceId() } returns "self-id"
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun newTransport(): BleTransportImpl =
        BleTransportImpl(context, settingsRepository, peerId = "test-peer-id-1234")

    @Test
    fun transportMetadata_correct() {
        val t = newTransport()
        assertEquals("ble", t.transportName)
        assertTrue(t.capabilities.contains(TransportCapability.LOW_POWER))
        assertTrue(t.capabilities.contains(TransportCapability.OFFLINE))
        assertFalse(t.capabilities.contains(TransportCapability.FILE_TRANSFER))
        assertFalse(t.capabilities.contains(TransportCapability.HIGH_BANDWIDTH))
        assertEquals(2, t.capabilities.size)
    }

    @Test
    fun isAvailable_whenAdapterDisabled_returnsFalse() {
        isAdapterEnabled = false
        val t = newTransport()
        assertFalse(t.isAvailable)
    }

    @Test
    fun isAvailable_whenAdapterEnabled_returnsTrue() {
        isAdapterEnabled = true
        val t = newTransport()
        assertTrue(t.isAvailable)
    }

    @Test
    fun isBleEnabled_mirrorsAdapterState() {
        isAdapterEnabled = false
        val t = newTransport()
        assertFalse(t.isBleEnabled())
        isAdapterEnabled = true
        assertTrue(t.isBleEnabled())
    }

    @Test
    fun getBluetoothAdapter_returnsAdapter() {
        val t = newTransport()
        assertNotNull(t.getBluetoothAdapter())
    }

    @Test
    fun getBluetoothAdapter_withNullManager_returnsNull() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns null
        every { ctx.applicationContext } returns ctx
        val t = BleTransportImpl(ctx, settingsRepository, peerId = "id")
        assertNull(t.getBluetoothAdapter())
        assertFalse(t.isBleEnabled())
    }

    @Test
    fun runtimeActive_initiallyFalse() {
        val t = newTransport()
        assertFalse(t.runtimeActive.value)
        assertTrue(t.onlinePeers.value.isEmpty())
        assertTrue(t.typingPeers.value.isEmpty())
    }

    @Test
    fun startStop_idempotent_withoutAdapter() = runTest {
        val t = newTransport()
        // Without enabled adapter, start() will attempt GATT server creation and fail,
        // but must not throw.
        t.start()
        assertFalse(t.runtimeActive.value)
        // Second start is idempotent when already failed? First may not set isStarted,
        // so second attempts again — must not throw either.
        t.start()
        t.stop()
        assertFalse(t.runtimeActive.value)
        // Stop again is no-op
        t.stop()
    }

    @Test
    fun sendPayload_unknownPeer_returnsFailure() = runTest {
        val t = newTransport()
        val payload = Payload(senderId = "self", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        val result = t.sendPayload("unknown-peer", payload)
        assertTrue(result.isFailure)
    }

    @Test
    fun sendPayload_withoutClient_notInitialized() = runTest {
        val t = newTransport()
        val payload = Payload(senderId = "self", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        // No start() called, bleGattClient is null
        val result = t.sendPayload("any-peer", payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not initialized", ignoreCase = true) ||
                   result.exceptionOrNull()!!.message!!.contains("Unknown peer", ignoreCase = true))
    }

    @Test
    fun startDiscovery_idempotent() = runTest {
        val t = newTransport()
        t.startDiscovery()
        t.startDiscovery()
        t.stopDiscovery()
        t.stopDiscovery()
    }

    @Test
    fun stopDiscovery_withoutStart_doesNotThrow() = runTest {
        val t = newTransport()
        t.stopDiscovery()
    }

    @Test
    fun construction_producesUsableInstance() {
        val t = newTransport()
        assertNotNull(t)
        assertEquals("ble", t.transportName)
    }

    @Test
    fun handleBluetoothStateChange_stateOff_isNoOp() = runTest {
        val t = newTransport()
        t.handleBluetoothStateChange(BluetoothAdapter.STATE_OFF)
        assertFalse(t.runtimeActive.value)
    }

    @Test
    fun handleBluetoothStateChange_stateOn_whenNotWanted_isNoOp() = runTest {
        val t = newTransport()
        t.handleBluetoothStateChange(BluetoothAdapter.STATE_ON)
        assertFalse(t.runtimeActive.value)
    }

    @Test
    fun handleBluetoothStateChange_stateTurningOff_tearsDown() = runTest {
        val t = newTransport()
        t.handleBluetoothStateChange(BluetoothAdapter.STATE_TURNING_OFF)
        assertFalse(t.runtimeActive.value)
        assertTrue(t.onlinePeers.value.isEmpty())
    }

    @Test
    fun onlinePeers_reflectsTransportLifecycle() = runTest {
        val t = newTransport()
        assertTrue(t.onlinePeers.value.isEmpty())
        t.start()
        assertTrue(t.onlinePeers.value.isEmpty())
        t.stop()
        assertTrue(t.onlinePeers.value.isEmpty())
    }
}
