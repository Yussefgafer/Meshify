package com.p2p.meshify.core.network.base

import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic interface for all transport implementations (LAN, BT, Wi-Fi Direct, DHT).
 * Each transport represents a different communication medium.
 */
interface IMeshTransport {
    /**
     * Unique transport identifier.
     * Examples: "lan", "bluetooth", "wifi_direct", "dht"
     */
    val transportName: String

    /**
     * Check if this transport is available on current device.
     * @return true if hardware supports this transport
     */
    val isAvailable: Boolean

    /**
     * Transport capabilities for smart selection.
     * Used by TransportManager to choose best transport for a given use case.
     */
    val capabilities: Set<TransportCapability>

    /**
     * Observable stream of transport events.
     */
    val events: Flow<TransportEvent>

    /**
     * Set of currently online peer IDs.
     */
    val onlinePeers: StateFlow<Set<String>>

    /**
     * Set of peer IDs currently typing.
     */
    val typingPeers: StateFlow<Set<String>>

    /**
     * Starts the transport service (e.g., binds sockets, starts mDNS).
     */
    suspend fun start()

    /**
     * Stops the transport service and cleans up resources.
     */
    suspend fun stop()

    /**
     * Discovers peers on the current medium.
     */
    suspend fun startDiscovery()

    /**
     * Stops peer discovery and cleans up related resources.
     */
    suspend fun stopDiscovery()

    /**
     * Sends a payload to a specific device.
     * @param targetDeviceId The unique identifier of the target peer
     * @param payload The payload to send
     * @return Result indicating success or failure
     */
    suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit>
}
