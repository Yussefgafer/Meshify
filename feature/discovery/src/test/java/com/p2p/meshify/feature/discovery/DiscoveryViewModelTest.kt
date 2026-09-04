package com.p2p.meshify.feature.discovery

import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [DiscoveryViewModel].
 *
 * Scope:
 *   Drives DiscoveryViewModel as a plain class (no Hilt). Dependencies are the
 *   concrete [TransportManager] and the [WifiStateChecker] interface — both mocked
 *   with mockk. We stub `TransportManager.getAllEventsFlow()` to a controllable
 *   [MutableSharedFlow] so we can replay [TransportEvent]s and observe how the VM
 *   folds them into [DiscoveryUiState].
 *
 * Why no Robolectric:
 *   DiscoveryViewModel only touches `wifiStateChecker.isWifiEnabled` (a pure
 *   boolean interface) and `transportManager` flows — no Android resources /
 *   Context are read, so the test stays JVM-only, matching the Phase 5 pattern.
 *
 * What's tested (behavior, not private helpers):
 *   - DeviceDiscovered adds a peer and clears isSearching immediately.
 *   - DeviceLost removes the peer from the list.
 *   - mergeTransportType: LAN then BLE for the same deviceId => BOTH.
 *   - mergeRssi: keeps the strongest (least-negative) signal.
 *   - mergeName: prefers a real name over a synthetic "Peer_" label.
 *   - Error with an empty peer list => `errorMessage`; with a populated list =>
 *     `nonFatalError` (peer list is preserved).
 *   - clearNonFatalError resets the non-fatal field.
 *   - init seeds already-known peers from `transportManager.discoveredPeers`.
 *   - checkWifiState mirrors `isWifiEnabled` into `isWifiEnabled`/`canDiscover`.
 *   - the no-discovered fallback turns isSearching off after DISCOVERY_SCAN_DELAY_MS.
 *
 * Limitations:
 *   - `refresh()` restarts discovery via suspend `transportManager.start/stopDiscoveryOnAll`
 *     running on a real (Default) scope; we exercise checkWifiState + the error/isRefreshing
 *     transitions only indirectly through init seeding, not by invoking refresh's BLE/LAN cycle.
 *   - The merge helpers (mergeTransportType/mergeRssi/mergeName) are private; their
 *     logic is verified solely through the observable merged peer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    private lateinit var transportManager: TransportManager
    private lateinit var wifiStateChecker: WifiStateChecker
    private var currentVm: DiscoveryViewModel? = null

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(mainRule.dispatcher)
    }

    @After
    fun tearDownDispatchers() {
        currentVm?.viewModelScope?.cancel()
        currentVm = null
        Dispatchers.resetMain()
    }

    @Before
    fun setUp() {
        transportManager = mockk(relaxed = true)
        wifiStateChecker = mockk(relaxed = true)

        every { transportManager.getAllEventsFlow() } returns eventsFlow
        every { transportManager.discoveredPeers } returns MutableStateFlow(emptyMap())
        every { wifiStateChecker.isWifiEnabled } returns true
    }

    private fun newVm(): DiscoveryViewModel =
        DiscoveryViewModel(transportManager, wifiStateChecker).also { currentVm = it }

    private fun discovered(deviceId: String, name: String, rssi: Int?, transportType: TransportType) =
        TransportEvent.DeviceDiscovered(
            deviceId = deviceId,
            deviceName = name,
            address = "addr-$deviceId",
            rssi = rssi,
            transportType = transportType
        )

    // ===== discovery events =====

    @Test
    fun `DeviceDiscovered — adds peer to the list and clears isSearching`() = runTest {
        val vm = newVm()
        runCurrent()
        assertTrue(vm.uiState.value.isSearching)

        eventsFlow.emit(discovered("p1", "Alice", rssi = -50, transportType = TransportType.LAN))
        runCurrent()

        val peers = vm.uiState.value.discoveredPeers
        assertEquals(1, peers.size)
        assertEquals("p1", peers[0].id)
        assertEquals("Alice", peers[0].name)
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test
    fun `DeviceLost — removes the peer from the list`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Alice", rssi = -50, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(discovered("p2", "Bob", rssi = -50, transportType = TransportType.LAN))
        runCurrent()
        assertEquals(2, vm.uiState.value.discoveredPeers.size)

        eventsFlow.emit(TransportEvent.DeviceLost("p1"))
        runCurrent()

        val peers = vm.uiState.value.discoveredPeers
        assertEquals(1, peers.size)
        assertEquals("p2", peers[0].id)
    }

    // ===== merge helpers (observed via the merged peer) =====

    @Test
    fun `mergeTransportType — LAN then BLE for same device yields BOTH`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Alice", rssi = null, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(discovered("p1", "Alice", rssi = null, transportType = TransportType.BLE))
        runCurrent()

        assertEquals(TransportType.BOTH, vm.uiState.value.discoveredPeers[0].transportType)
    }

    @Test
    fun `mergeRssi — keeps the strongest (least-negative) signal`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Alice", rssi = -70, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(discovered("p1", "Alice", rssi = -40, transportType = TransportType.LAN))
        runCurrent()

        assertEquals(-40, vm.uiState.value.discoveredPeers[0].rssi)
    }

    @Test
    fun `mergeName — prefers a real name over a synthetic Peer_ label`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Peer_1234", rssi = null, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(discovered("p1", "Alice", rssi = null, transportType = TransportType.LAN))
        runCurrent()

        assertEquals("Alice", vm.uiState.value.discoveredPeers[0].name)
    }

    // ===== error handling =====

    @Test
    fun `Error — with empty peer list surfaces errorMessage`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(TransportEvent.Error("ble transport down"))
        runCurrent()

        assertEquals("ble transport down", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.nonFatalError)
    }

    @Test
    fun `Error — with populated peer list surfaces nonFatalError and keeps the list`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Alice", rssi = -50, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(TransportEvent.Error("ble transport down"))
        runCurrent()

        assertEquals("ble transport down", vm.uiState.value.nonFatalError)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.discoveredPeers.size)
    }

    @Test
    fun `clearNonFatalError — resets the non-fatal error`() = runTest {
        val vm = newVm()
        runCurrent()

        eventsFlow.emit(discovered("p1", "Alice", rssi = -50, transportType = TransportType.LAN))
        runCurrent()
        eventsFlow.emit(TransportEvent.Error("ble transport down"))
        runCurrent()
        assertNull(vm.uiState.value.errorMessage)

        vm.clearNonFatalError()
        runCurrent()

        assertNull(vm.uiState.value.nonFatalError)
    }

    // ===== init wiring =====

    @Test
    fun `init — seeds already-known peers from transportManager`() = runTest {
        val seeded = mapOf(
            "seed1" to PeerDevice(id = "seed1", name = "Bob", address = "addr-seed1")
        )
        every { transportManager.discoveredPeers } returns MutableStateFlow(seeded)

        val vm = DiscoveryViewModel(transportManager, wifiStateChecker)
        runCurrent()

        val peers = vm.uiState.value.discoveredPeers
        assertEquals(1, peers.size)
        assertEquals("seed1", peers[0].id)
        assertEquals("Bob", peers[0].name)
    }

    @Test
    fun `checkWifiState — disabled wifi flips isWifiEnabled and canDiscover off`() = runTest {
        every { wifiStateChecker.isWifiEnabled } returns false

        val vm = newVm()
        runCurrent()

        assertFalse(vm.uiState.value.isWifiEnabled)
        assertFalse(vm.uiState.value.canDiscover)
    }

    @Test
    fun `init — fallback turns isSearching off after the scan delay when no peer appears`() = runTest {
        val vm = newVm()
        runCurrent()
        assertTrue(vm.uiState.value.isSearching)

        advanceTimeBy(2500L)
        runCurrent()

        assertFalse(vm.uiState.value.isSearching)
    }
}
