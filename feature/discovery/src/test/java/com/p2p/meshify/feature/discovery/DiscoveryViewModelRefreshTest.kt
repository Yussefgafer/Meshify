package com.p2p.meshify.feature.discovery

import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelRefreshTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var transportManager: TransportManager
    private lateinit var wifiStateChecker: WifiStateChecker
    private lateinit var eventsFlow: MutableSharedFlow<TransportEvent>

    @Before
    fun setUp() {
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        transportManager = mockk(relaxed = true)
        wifiStateChecker = mockk(relaxed = true)
        every { transportManager.getAllEventsFlow() } returns eventsFlow
        every { transportManager.discoveredPeers } returns MutableStateFlow(emptyMap())
        every { wifiStateChecker.isWifiEnabled } returns true
    }

    @Test
    fun refresh_setsRefreshingAndSearchingBeforeStoppingDiscovery() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertTrue(viewModel.uiState.value.isSearching)
        coVerify(exactly = 1) { transportManager.stopDiscoveryOnAll() }
        coVerify(exactly = 0) { transportManager.startDiscoveryOnAll() }
    }

    @Test
    fun refresh_restartsDiscoveryAfterCleanupDelay() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(199L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        coVerify(exactly = 0) { transportManager.startDiscoveryOnAll() }

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { transportManager.startDiscoveryOnAll() }
    }

    @Test
    fun refresh_finishesLifecycleAfterScanDelay() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        // Let the init fallback elapse so it is not conflated with refresh's own
        // DISCOVERY_SCAN_DELAY_MS window.
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(2_500L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertTrue(viewModel.uiState.value.isSearching)

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(200L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { transportManager.startDiscoveryOnAll() }

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_999L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isSearching)
        coVerify(exactly = 1) { transportManager.stopDiscoveryOnAll() }
    }

    @Test
    fun refresh_clearsErrorMessagesBeforeRestartingDiscovery() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(TransportEvent.Error("previous fatal error"))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals("previous fatal error", viewModel.uiState.value.errorMessage)

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.nonFatalError)
        assertTrue(viewModel.uiState.value.isRefreshing)

        // Also verify the non-fatal lane is cleared on a subsequent refresh cycle
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(2_500L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(discovered("seed", "Seed", -60, TransportType.LAN))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(TransportEvent.Error("transport warning"))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals("transport warning", viewModel.uiState.value.nonFatalError)

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertNull(viewModel.uiState.value.nonFatalError)
    }

    @Test
    fun refresh_checksWifiState() = runTest {
        every { wifiStateChecker.isWifiEnabled } returns false
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.refresh()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isWifiEnabled)
        assertFalse(viewModel.uiState.value.canDiscover)
        coVerify(exactly = 1) { transportManager.stopDiscoveryOnAll() }
    }

    @Test
    fun mergeAcrossThreeDiscoveriesKeepsStrongestNameAndBothTransports() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        eventsFlow.emit(discovered("shared", "Peer_abcd", -80, TransportType.LAN))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(discovered("other", "Temporary", -45, TransportType.BLE))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(TransportEvent.DeviceLost("other"))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(discovered("shared", "Alice", -55, TransportType.BLE))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        val peer = viewModel.uiState.value.discoveredPeers.single()
        assertEquals("shared", peer.id)
        assertEquals("Alice", peer.name)
        assertEquals(-55, peer.rssi)
        assertEquals(TransportType.BOTH, peer.transportType)
        assertEquals(1, viewModel.uiState.value.discoveredPeers.size)
    }

    @Test
    fun deviceLostForOneTransportDoesNotRemovePeerDiscoveredOnAnotherTransport() = runTest {
        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        eventsFlow.emit(discovered("shared", "Alice", -70, TransportType.LAN))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(discovered("shared", "Alice", -65, TransportType.BLE))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(TransportEvent.DeviceLost("shared"))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        eventsFlow.emit(discovered("shared", "Alice", -60, TransportType.LAN))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        val peer = viewModel.uiState.value.discoveredPeers.single()
        assertEquals("shared", peer.id)
        assertEquals("Alice", peer.name)
        assertEquals(-60, peer.rssi)
        assertEquals(TransportType.LAN, peer.transportType)
    }

    @Test
    fun discoveredPeerSeedsFromTransportManagerAtInitialization() = runTest {
        val seededPeer = PeerDevice(
            id = "seeded",
            name = "Seed",
            address = "10.0.0.20",
            rssi = -58,
            transportType = TransportType.LAN
        )
        every { transportManager.discoveredPeers } returns MutableStateFlow(mapOf("seeded" to seededPeer))

        val viewModel = DiscoveryViewModel(transportManager, wifiStateChecker)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(listOf(seededPeer), viewModel.uiState.value.discoveredPeers)
    }

    private fun discovered(
        deviceId: String,
        deviceName: String,
        rssi: Int,
        transportType: TransportType
    ) = TransportEvent.DeviceDiscovered(
        deviceId = deviceId,
        deviceName = deviceName,
        address = "10.0.0.$deviceId",
        rssi = rssi,
        transportType = transportType
    )
}
