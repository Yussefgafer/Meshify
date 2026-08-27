package com.p2p.meshify.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Discovery cleanup pause before restarting scan (ms) */
private const val DISCOVERY_CLEANUP_DELAY_MS = 200L

/** Fallback window before turning off the search spinner if no peer ever appears. */
private const val DISCOVERY_SCAN_DELAY_MS = 2000L

/**
 * UI State for the Discovery Screen.
 */
data class DiscoveryUiState(
    val discoveredPeers: List<PeerDevice> = emptyList(),
    val isSearching: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val nonFatalError: String? = null,
    val isWifiEnabled: Boolean = true,
    val canDiscover: Boolean = true
)

/**
 * ViewModel for the Peer Discovery Screen.
 * Observes [TransportManager] events and maps them to [DiscoveryUiState].
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val transportManager: TransportManager,
    private val wifiStateChecker: WifiStateChecker
) : ViewModel() {

    internal fun checkWifiState() {
        val isWifiEnabled = wifiStateChecker.isWifiEnabled
        _uiState.update {
            it.copy(
                isWifiEnabled = isWifiEnabled,
                canDiscover = isWifiEnabled
            )
        }
    }

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    // MINOR FIX m3: Use Map for O(1) lookup instead of O(n) indexOfFirst
    private val peerMap = mutableMapOf<String, PeerDevice>()

    init {
        observeTransportEvents()
        // Seed already-known peers so returning to Discovery doesn't blank the list.
        // The merged event flow has replay=0, so a recreated ViewModel would otherwise
        // only refill on the next scan result (or stay empty until the next discovery).
        peerMap.putAll(transportManager.discoveredPeers.value)
        _uiState.update { it.copy(discoveredPeers = peerMap.values.toList()) }
        checkWifiState()
        // Fallback so the spinner does not spin forever if no DeviceDiscovered ever
        // arrives; handleDeviceDiscovered clears it earlier the moment a peer shows up,
        // and refresh() manages its own scan-bound isSearching lifecycle.
        viewModelScope.launch {
            kotlinx.coroutines.delay(DISCOVERY_SCAN_DELAY_MS)
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    private fun observeTransportEvents() {
        viewModelScope.launch {
            transportManager.getAllEventsFlow().collect { event ->
                when (event) {
                    is TransportEvent.DeviceDiscovered -> handleDeviceDiscovered(event)
                    is TransportEvent.DeviceLost -> handleDeviceLost(event)
                    is TransportEvent.Error -> handleError(event)
                    is TransportEvent.ConnectionEstablished -> {
                        Logger.i("DiscoveryViewModel -> Connection established: ${event.deviceId}")
                    }
                    is TransportEvent.ConnectionLost -> {
                        Logger.w("DiscoveryViewModel -> Connection lost: ${event.deviceId}, reason: ${event.reason ?: "unknown"}")
                    }
                    is TransportEvent.PayloadReceived -> {
                        // Payloads are handled by ChatRepository, just log here
                        Logger.d("DiscoveryViewModel -> Payload received from ${event.deviceId}")
                    }
                }
            }
        }
    }

    private fun handleDeviceDiscovered(event: TransportEvent.DeviceDiscovered) {
        val existingPeer = peerMap[event.deviceId]
        val mergedTransportType = mergeTransportType(existingPeer?.transportType, event.transportType)
        val bestRssi = mergeRssi(existingPeer?.rssi, event.rssi)
        val bestName = mergeName(existingPeer?.name, event.deviceName)
        val bestAddress = existingPeer?.address ?: event.address

        val updatedPeer = PeerDevice(
            id = event.deviceId,
            name = bestName,
            address = bestAddress,
            rssi = bestRssi,
            isConnected = existingPeer?.isConnected ?: false,
            transportType = mergedTransportType
        )
        peerMap[event.deviceId] = updatedPeer

        _uiState.update {
            it.copy(
                discoveredPeers = peerMap.values.toList(),
                isSearching = false,
                errorMessage = null
            )
        }
    }

    /** Merge transport types: LAN + BLE → BOTH */
    private fun mergeTransportType(existing: TransportType?, incoming: TransportType): TransportType = when {
        existing == null -> incoming
        existing == TransportType.BOTH -> TransportType.BOTH
        existing != incoming -> TransportType.BOTH
        else -> incoming
    }

    /** Keep strongest RSSI (less negative = stronger) */
    private fun mergeRssi(existing: Int?, incoming: Int?): Int? =
        if (existing != null && incoming != null) maxOf(existing, incoming)
        else incoming ?: existing

    /** Prefer real peer name over generic "Peer_XXXX" */
    private fun mergeName(existing: String?, incoming: String): String = when {
        existing?.startsWith("Peer_") == true && !incoming.startsWith("Peer_") -> incoming
        !incoming.startsWith("Peer_") -> incoming
        else -> existing ?: incoming
    }

    private fun handleDeviceLost(event: TransportEvent.DeviceLost) {
        // MINOR FIX m3: O(1) map removal
        peerMap.remove(event.deviceId)
        _uiState.update {
            it.copy(
                discoveredPeers = peerMap.values.toList()
            )
        }
    }

    private fun handleError(event: TransportEvent.Error) {
        Logger.e("DiscoveryViewModel -> Transport error: ${event.message}", event.exception)
        // Background transport errors must not blank a populated peer list.
        // When LAN is working but BLE (or another transport) failed, surface the
        // failure as a transient non-fatal message instead of swallowing it, so the
        // user is not left with a silently broken transport.
        if (peerMap.isEmpty()) {
            _uiState.update { it.copy(errorMessage = event.message) }
        } else {
            _uiState.update { it.copy(nonFatalError = event.message) }
        }
    }

    fun clearNonFatalError() {
        _uiState.update { it.copy(nonFatalError = null) }
    }

    /**
     * UX-05: Trigger manual refresh for pull-to-refresh
     * P0-3: Actually restart discovery instead of just delaying
     * P0-4: Clear error message on refresh
     */
    fun refresh() {
        checkWifiState()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isSearching = true, errorMessage = null, nonFatalError = null) }

            try {
                // Stop and restart discovery to find fresh devices
                transportManager.stopDiscoveryOnAll()
                kotlinx.coroutines.delay(DISCOVERY_CLEANUP_DELAY_MS) // Brief pause for cleanup
                transportManager.startDiscoveryOnAll()
                // Give discovery some time to find devices
                kotlinx.coroutines.delay(DISCOVERY_SCAN_DELAY_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Refresh failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isRefreshing = false, isSearching = false) }
            }
        }
    }
}
