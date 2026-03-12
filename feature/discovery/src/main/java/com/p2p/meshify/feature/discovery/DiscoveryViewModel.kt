package com.p2p.meshify.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.core.network.base.IMeshTransport
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
    val errorMessage: String? = null
)

data class PeerDevice(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int? = null, // RSSI signal strength in dBm (optional for now)
    val isConnected: Boolean = false
) {
    /**
     * Get signal strength based on RSSI value.
     * If RSSI is null but device is discovered/connected, default to MEDIUM instead of OFFLINE.
     */
    val signalStrength: SignalStrength
        get() = rssi?.let { SignalStrength.fromRssi(it) } ?: if (isConnected) SignalStrength.MEDIUM else SignalStrength.WEAK
}

/**
 * ViewModel for the Peer Discovery Screen.
 * Observes [IMeshTransport] events and maps them to [DiscoveryUiState].
 */
class DiscoveryViewModel(
    private val meshTransport: IMeshTransport
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        observeTransportEvents()
    }

    private fun observeTransportEvents() {
        viewModelScope.launch {
            meshTransport.events.collect { event ->
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
        _uiState.update { currentState ->
            val updatedList = currentState.discoveredPeers.toMutableList()
            val existingIndex = updatedList.indexOfFirst { it.id == event.deviceId }

            if (existingIndex != -1) {
                // Update existing device info (like IP if changed) with new RSSI
                updatedList[existingIndex] = PeerDevice(
                    id = event.deviceId,
                    name = event.deviceName,
                    address = event.address,
                    rssi = event.rssi,
                    isConnected = updatedList[existingIndex].isConnected
                )
            } else {
                updatedList.add(PeerDevice(event.deviceId, event.deviceName, event.address, event.rssi))
            }
            currentState.copy(discoveredPeers = updatedList)
        }
    }

    private fun handleDeviceLost(event: TransportEvent.DeviceLost) {
        _uiState.update { currentState ->
            val updatedList = currentState.discoveredPeers.filterNot { it.id == event.deviceId }
            currentState.copy(discoveredPeers = updatedList)
        }
    }

    private fun handleError(event: TransportEvent.Error) {
        _uiState.update { it.copy(errorMessage = event.message) }
    }
}
