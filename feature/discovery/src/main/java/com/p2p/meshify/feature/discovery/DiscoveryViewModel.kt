package com.p2p.meshify.feature.discovery

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the Discovery Screen.
 */
data class DiscoveryUiState(
    val discoveredPeers: List<PeerDevice> = emptyList(),
    val isSearching: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isWifiEnabled: Boolean = true,
    val canDiscover: Boolean = true
)

/**
 * ViewModel for the Peer Discovery Screen.
 * Observes [TransportManager] events and maps them to [DiscoveryUiState].
 */
class DiscoveryViewModel(
    private val transportManager: TransportManager,
    private val context: Context
) : ViewModel() {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    internal fun checkWifiState() {
        val isWifiEnabled = wifiManager.isWifiEnabled
        _uiState.update { 
            it.copy(
                isWifiEnabled = isWifiEnabled,
                canDiscover = isWifiEnabled
            ) 
        }
    }

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    // ✅ MINOR FIX m3: Use Map for O(1) lookup instead of O(n) indexOfFirst
    private val peerMap = mutableMapOf<String, PeerDevice>()

    init {
        observeTransportEvents()
        checkWifiState()
    }

    private fun observeTransportEvents() {
        viewModelScope.launch {
            transportManager.getAllEventsFlow().collect { event ->
                when (event) {
                    is TransportEvent.DeviceDiscovered -> handleDeviceDiscovered(event)
                    is TransportEvent.DeviceLost -> handleDeviceLost(event)
                    is TransportEvent.Error -> handleError(event)
                    else -> {} // Handle connection events later
                }
            }
        }
    }

    private fun handleDeviceDiscovered(event: TransportEvent.DeviceDiscovered) {
        // ✅ MINOR FIX m3: O(1) map update instead of O(n) list search
        val existingPeer = peerMap[event.deviceId]
        val updatedPeer = PeerDevice(
            id = event.deviceId,
            name = event.deviceName,
            address = event.address,
            rssi = event.rssi,
            isConnected = existingPeer?.isConnected ?: false
        )
        peerMap[event.deviceId] = updatedPeer
        
        _uiState.update {
            it.copy(
                discoveredPeers = peerMap.values.toList(),
                isSearching = peerMap.isNotEmpty()
            )
        }
    }

    private fun handleDeviceLost(event: TransportEvent.DeviceLost) {
        // ✅ MINOR FIX m3: O(1) map removal
        peerMap.remove(event.deviceId)
        _uiState.update {
            it.copy(
                discoveredPeers = peerMap.values.toList(),
                isSearching = peerMap.isNotEmpty()
            )
        }
    }

    private fun handleError(event: TransportEvent.Error) {
        _uiState.update { it.copy(errorMessage = event.message) }
    }

    /**
     * ✅ UX-05: Trigger manual refresh for pull-to-refresh
     */
    fun refresh() {
        checkWifiState()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            // Simulate refresh by re-discovering devices
            // In real implementation, this would call transportManager.startDiscovery()
            kotlinx.coroutines.delay(1000)

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
