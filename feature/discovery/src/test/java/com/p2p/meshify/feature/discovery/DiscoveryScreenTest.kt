package com.p2p.meshify.feature.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoveryScreenTest {
    @get:Rule val dispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val compose = createComposeRule()

    private fun createVm(
        isWifiEnabled: Boolean = true,
        peers: List<PeerDevice> = emptyList(),
        isSearching: Boolean = false,
        error: String? = null,
        isRefreshing: Boolean = false
    ): DiscoveryViewModel {
        val tm = mockk<TransportManager>(relaxed = true)
        every { tm.getAllEventsFlow() } returns MutableSharedFlow()
        every { tm.discoveredPeers } returns MutableStateFlow(emptyMap())
        val wifi = mockk<WifiStateChecker>(relaxed = true)
        every { wifi.isWifiEnabled } returns isWifiEnabled
        val vm = DiscoveryViewModel(tm, wifi)
        injectDiscoveryState(vm, "_uiState", DiscoveryUiState(
            discoveredPeers = peers,
            isSearching = isSearching,
            errorMessage = error,
            isWifiEnabled = isWifiEnabled,
            isRefreshing = isRefreshing
        ))
        return vm
    }

    @Test fun wifiDisabledShowsTitle() {
        val vm = createVm(isWifiEnabled = false)
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("Wi-Fi is disabled").assertIsDisplayed()
        compose.onNodeWithText("Open Wi-Fi Settings").assertIsDisplayed()
    }

    @Test fun errorStateShowsRetry() {
        val vm = createVm(error = "Transport error")
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("An error occurred").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test fun searchingWithEmptyShowsProgressEmpty() {
        val vm = createVm(isSearching = true, peers = emptyList())
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("No devices nearby").assertIsDisplayed()
    }

    @Test fun emptyShowsNoDevices() {
        val vm = createVm(peers = emptyList(), isSearching = false)
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("No devices nearby").assertIsDisplayed()
    }

    @Test fun listShowsPeersWithTransportBadges() {
        val peers = listOf(
            PeerDevice(id = "1", name = "Alice", address = "192.168.1.2", rssi = -40, transportType = TransportType.LAN),
            PeerDevice(id = "2", name = "Bob", address = "192.168.1.3", rssi = -60, transportType = TransportType.BLE),
            PeerDevice(id = "3", name = "Carol", address = "192.168.1.4", rssi = -80, transportType = TransportType.BOTH)
        )
        val vm = createVm(peers = peers)
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("BLE").assertIsDisplayed()
        compose.onNodeWithText("LAN").assertIsDisplayed()
        compose.onNodeWithText("Both").assertIsDisplayed()
    }

    @Test fun refreshEnabledGatingShowsRefreshIcon() {
        val vm = createVm(isRefreshing = false)
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNode(hasContentDescription("Refresh")).assertIsDisplayed()
    }

    @Test fun peerClickTriggersCallback() {
        val peers = listOf(PeerDevice(id = "1", name = "ClickMe", address = "addr", transportType = TransportType.LAN))
        val vm = createVm(peers = peers)
        var clicked = false
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = { clicked = true }, onBackClick = {}) } }
        compose.onNodeWithText("ClickMe").performClick()
        assert(clicked)
    }

    @Test fun rtlNoCrash() {
        val vm = createVm(peers = emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) }
            }
        }
        compose.onNodeWithText("No devices nearby").assertIsDisplayed()
    }

    @Test fun many60PeersRenders() {
        val peers = (0..59).map { i -> PeerDevice(id = "$i", name = "Peer $i", address = "addr $i", transportType = TransportType.LAN) }
        val vm = createVm(peers = peers)
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("Peer 0").assertIsDisplayed()
    }

    @Test fun wifiPriorityOverError() {
        val vm = createVm(isWifiEnabled = false, error = "some error")
        compose.setContent { DxThemeTestDisc { DiscoveryScreen(viewModel = vm, onPeerClick = {}, onBackClick = {}) } }
        compose.onNodeWithText("Wi-Fi is disabled").assertIsDisplayed()
    }
}
