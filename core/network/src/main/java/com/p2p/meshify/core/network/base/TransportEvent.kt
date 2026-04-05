package com.p2p.meshify.core.network.base

import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportType

/**
 * Represents all possible events emitted by a transport layer.
 */
sealed class TransportEvent {
    data class DeviceDiscovered(
        val deviceId: String,
        val deviceName: String,
        val address: String,
        val avatarHash: String? = null,
        val rssi: Int? = null, // RSSI signal strength in dBm (optional)
        val transportType: TransportType = TransportType.LAN // Which transport discovered this device
    ) : TransportEvent()
    data class DeviceLost(val deviceId: String) : TransportEvent()
    data class ConnectionEstablished(val deviceId: String) : TransportEvent()
    data class ConnectionLost(val deviceId: String, val reason: String?) : TransportEvent()
    data class PayloadReceived(val deviceId: String, val payload: Payload) : TransportEvent()
    data class Error(val message: String, val exception: Throwable? = null) : TransportEvent()
}
