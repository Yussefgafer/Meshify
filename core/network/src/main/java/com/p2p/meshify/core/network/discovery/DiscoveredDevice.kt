package com.p2p.meshify.core.network.discovery

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a discovered device during network discovery.
 *
 * @param deviceId Unique identifier for the device (e.g., UUID, MAC address)
 * @param deviceName Human-readable device name
 * @param address Network address (IP, MAC, etc.)
 * @param transportType Transport protocol used (e.g., "lan", "bluetooth", "wifi_direct")
 * @param rssi Signal strength in dBm (optional, null if not available)
 * @param metadata Additional metadata (e.g., avatar hash, capabilities)
 */
data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val address: String,
    val transportType: String,
    val rssi: Int? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Check if this device is the same as another device.
     * Devices are considered the same if they have the same deviceId.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DiscoveredDevice
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}
