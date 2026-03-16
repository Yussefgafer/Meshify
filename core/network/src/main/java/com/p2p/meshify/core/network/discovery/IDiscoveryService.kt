package com.p2p.meshify.core.network.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic interface for all device discovery services.
 * Each discovery service represents a different discovery mechanism (mDNS, Bluetooth LE, etc.)
 *
 * Discovery services are separate from transports to allow:
 * - Multiple discovery mechanisms per transport
 * - Independent lifecycle management
 * - Easier addition of new discovery protocols (e.g., DHT, QR codes)
 */
interface IDiscoveryService {
    /**
     * Unique service identifier.
     * Examples: "lan_mdns", "bluetooth_le", "wifi_direct", "dht"
     */
    val serviceName: String

    /**
     * Check if this discovery service is available on current device.
     * @return true if hardware supports this discovery mechanism
     */
    val isAvailable: Boolean

    /**
     * Observable stream of discovered devices.
     * Emits updates when devices are discovered or lost.
     */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    /**
     * Start device discovery.
     * @param timeoutMs Optional timeout in milliseconds (null for indefinite)
     */
    suspend fun startDiscovery(timeoutMs: Long? = null)

    /**
     * Stop device discovery and clean up resources.
     */
    suspend fun stopDiscovery()

    /**
     * Clear all discovered devices from the cache.
     */
    fun clearDiscoveredDevices()
}
