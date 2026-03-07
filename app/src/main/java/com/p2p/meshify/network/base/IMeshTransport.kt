package com.p2p.meshify.network.base

import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.Flow

/**
 * Generic interface for all transport implementations (LAN, BT, Wi-Fi Direct).
 */
interface IMeshTransport {
    /**
     * Observable stream of transport events.
     */
    val events: Flow<TransportEvent>

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
     * @return Result indicating success or failure.
     */
    suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit>
}
