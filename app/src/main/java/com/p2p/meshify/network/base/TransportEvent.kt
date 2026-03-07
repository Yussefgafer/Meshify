package com.p2p.meshify.network.base

import com.p2p.meshify.domain.model.Payload

/**
 * Represents all possible events emitted by a transport layer.
 */
sealed class TransportEvent {
    data class DeviceDiscovered(
        val deviceId: String,
        val deviceName: String,
        val address: String,
        val rssi: Int? = null // RSSI signal strength in dBm (optional)
    ) : TransportEvent()
    data class DeviceLost(val deviceId: String) : TransportEvent()
    data class ConnectionEstablished(val deviceId: String) : TransportEvent()
    data class ConnectionLost(val deviceId: String, val reason: String?) : TransportEvent()
    data class PayloadReceived(val deviceId: String, val payload: Payload) : TransportEvent()
    data class Error(val message: String, val exception: Throwable? = null) : TransportEvent()
}
