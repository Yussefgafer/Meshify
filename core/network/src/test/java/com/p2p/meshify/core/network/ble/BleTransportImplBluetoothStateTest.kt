package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BleTransportImpl.handleBluetoothStateChange].
 *
 * Scope:
 *   We isolate the system-shade BT-toggle reaction path. The implementation
 *   exposes `internal suspend fun handleBluetoothStateChange(state: Int)` so
 *   this is exercisable without standing up the full GATT server / advertiser.
 *
 * What's tested:
 * - STATE_TURNING_OFF triggers the inline teardown (tearDownGattStack). On a
 *   freshly-constructed transport every BLE component is null, so the
 *   safe-call chain must complete without throwing and `bleRuntimeActive`
 *   must remain false.
 * - STATE_ON is a no-op when the transport was not started (default
 *   `bleWantedOn == false`) — it must NOT try to bring GATT up under a JVM
 *   sandbox where there's no BluetoothAdapter / permissions.
 * - Other unrelated states (STATE_OFF, STATE_TURNING_ON, etc.) are ignored.
 *
 * What's deliberately NOT tested here:
 * - The full GATT up path (server service-add, advertising, scan filter
 *   registration). That requires a real Bluetooth adapter and is covered by
 *   `feature:real-device-testing`.
 * - The system-shade STATE_ON restart path when `bleWantedOn == true`. We
 *   can't flip that flag from outside the class without invoking start()
 *   first, and start() fails fast without a real BluetoothAdapter — so
 *   exercising it requires two real devices.
 *
 * Why no Robolectric:
 *   Robolectric 4.16.1's android-all-instrumented artifacts are compiled to
 *   Java 24 (class file major version 70), which its bundled ASM cannot
 *   decode during `Shadows.reset()` on this JDK 26 setup. Any
 *   `@RunWith(RobolectricTestRunner::class)` test crashes during the
 *   finallyAfterTest reset. `BleTransportImpl.handleBluetoothStateChange`
 *   doesn't need Android resources / `Looper`, so a mockk Context with a
 *   null `BLUETOOTH_SERVICE` is enough to construct it (the `isAvailable`
 *   getter is never called from this path).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleTransportImplBluetoothStateTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: ISettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Logger delegates to android.util.Log.*. On a JVM unit test the Log
        // methods throw "Method ... not mocked" unless stubbed.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        // Mocked Context — getSystemService returns null for BLUETOOTH_SERVICE,
        // matching the "no BluetoothAdapter available" case. applicationContext
        // is required by other seams; return the same mock.
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns null
        every { ctx.applicationContext } returns ctx
        context = ctx

        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.notificationsEnabled } returns kotlinx.coroutines.flow.flowOf(false)
        coEvery { settingsRepository.getDeviceId() } returns "self-id"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    private fun newTransport(): BleTransportImpl =
        BleTransportImpl(context, settingsRepository, peerId = "test-peer-id")

    @Test
    fun `handleBluetoothStateChange — STATE_TURNING_OFF runs teardown without throwing`() = runTest {
        val transport = newTransport()

        // Pre-condition: runtime inactive.
        assertFalse(transport.runtimeActive.value)

        // Must NOT throw on a fresh transport whose BLE components are all null
        // (tearDownGattStack uses safe-call ?. everywhere, so this is the load-bearing
        // case for "Quick-Settings BT off doesn't crash us").
        transport.handleBluetoothStateChange(BluetoothAdapter.STATE_TURNING_OFF)

        // Post-teardown invariants: same shape as a freshly-constructed transport.
        assertFalse(transport.runtimeActive.value)
    }

    @Test
    fun `handleBluetoothStateChange — STATE_OFF is a no-op`() = runTest {
        val transport = newTransport()

        // STATE_OFF is not in the when() branches, so nothing should happen.
        transport.handleBluetoothStateChange(BluetoothAdapter.STATE_OFF)

        assertFalse(transport.runtimeActive.value)
    }

    @Test
    fun `handleBluetoothStateChange — STATE_TURNING_ON is a no-op`() = runTest {
        val transport = newTransport()

        transport.handleBluetoothStateChange(BluetoothAdapter.STATE_TURNING_ON)

        assertFalse(transport.runtimeActive.value)
    }

    @Test
    fun `handleBluetoothStateChange — STATE_ON is a no-op when transport was never started`() = runTest {
        // bleWantedOn defaults to false, so restartOnSystemBtOn() must short-circuit
        // and not call start() (which would fail without a real BluetoothAdapter).
        val transport = newTransport()

        transport.handleBluetoothStateChange(BluetoothAdapter.STATE_ON)

        assertFalse(transport.runtimeActive.value)
    }

    @Test
    fun `BleTransportImpl — construction produces a usable instance`() {
        // Sanity: ensure the test scaffold (mocked Context + relaxed SettingsRepository)
        // is enough to build a transport without throwing.
        val transport = newTransport()
        assertNotNull(transport)
        assertFalse(transport.runtimeActive.value)
    }
}
