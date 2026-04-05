package com.p2p.meshify.domain.model

/**
 * Represents a peer device discovered on the network.
 * This is a core domain model used across all layers.
 */
data class PeerDevice(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int? = null, // RSSI signal strength in dBm (optional for now)
    val isConnected: Boolean = false,
    val transportType: TransportType = TransportType.LAN
) {
    /**
     * Get signal strength based on RSSI value.
     * If RSSI is null but device is discovered/connected, default to MEDIUM instead of OFFLINE.
     */
    val signalStrength: SignalStrength
        get() = rssi?.let { SignalStrength.fromRssi(it) } ?: if (isConnected) SignalStrength.MEDIUM else SignalStrength.WEAK
}

/**
 * Transport type used to discover and connect to a peer.
 */
enum class TransportType {
    /** Discovered via LAN (mDNS / Wi-Fi) */
    LAN,

    /** Discovered via Bluetooth Low Energy */
    BLE,

    /** Available via both LAN and BLE */
    BOTH
}
