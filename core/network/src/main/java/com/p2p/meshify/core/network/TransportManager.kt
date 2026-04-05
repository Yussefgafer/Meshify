package com.p2p.meshify.core.network

import android.content.Context
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.network.ble.BleTransportImpl
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

/**
 * Central manager for all transport protocols.
 * Enables easy addition of new transports (Bluetooth, WiFi-Direct, DHT, etc.)
 * by registering them in a single place.
 *
 * Usage:
 * ```
 * val manager = TransportManager.createDefault(context, settingsRepository)
 *
 * // Future: Add Bluetooth transport (1 line)
 * // manager.registerTransport("bluetooth", BluetoothTransportImpl(context, settingsRepository))
 *
 * // Future: Add WiFi-Direct transport (1 line)
 * // manager.registerTransport("wifi_direct", WifiDirectTransportImpl(context, settingsRepository))
 *
 * // Future: Add DHT transport (1 line)
 * // manager.registerTransport("dht", DhtTransportImpl(context, settingsRepository))
 * ```
 */
class TransportManager(
    private val context: Context,
    private val settingsRepository: ISettingsRepository
) {
    internal val socketManager = SocketManager() // ✅ Changed from private to internal
    private val transports = mutableMapOf<String, IMeshTransport>()

    /**
     * Register a new transport protocol.
     * @param name Unique identifier (e.g., "lan", "bluetooth", "wifi_direct", "dht")
     * @param transport Transport implementation
     */
    fun registerTransport(name: String, transport: IMeshTransport) {
        transports[name] = transport
    }

    /**
     * Get a specific transport by name.
     * @param name Transport name
     * @return Transport implementation or null if not found
     */
    fun getTransport(name: String): IMeshTransport? = transports[name]

    /**
     * Get all registered transports.
     * @return List of all transport implementations
     */
    fun getAllTransports(): List<IMeshTransport> = transports.values.toList()

    /**
     * Get all available transports (hardware-supported).
     * @return List of transports that are available on current device
     */
    fun getAvailableTransports(): List<IMeshTransport> {
        return transports.values.filter { it.isAvailable }
    }

    /**
     * Get a transport that has a specific peer online.
     * @param peerId The peer ID to look for
     * @return Transport that has the peer online, or null if none found
     */
    fun getTransportWithPeer(peerId: String): IMeshTransport? {
        return transports.values.firstOrNull { transport ->
            transport.onlinePeers.value.contains(peerId)
        }
    }

    /**
     * Select the best transport for a given peer based on capabilities and availability.
     * @param peerId The peer ID to send data to
     * @param requiredCapabilities Optional capabilities required for the operation
     * @return Best available transport or null if none found
     */
    fun selectBestTransport(
        peerId: String,
        requiredCapabilities: Set<TransportCapability> = emptySet()
    ): IMeshTransport? {
        // First, try to find a transport that already has this peer online
        val transportWithPeer = getTransportWithPeer(peerId)
        if (transportWithPeer != null) {
            return transportWithPeer
        }

        // If no transport has the peer, select based on capabilities
        val availableTransports = getAvailableTransports()

        // Filter transports that have all required capabilities
        val capableTransports = availableTransports.filter { transport ->
            requiredCapabilities.all { transport.capabilities.contains(it) }
        }

        // Return the first capable transport, or fall back to LAN as default
        return capableTransports.firstOrNull()
            ?: availableTransports.firstOrNull { it.transportName == "lan" }
            ?: availableTransports.firstOrNull()
    }

    /**
     * Get merged events flow from all registered transports.
     * @return Flow of transport events from all transports
     */
    fun getAllEventsFlow(): Flow<com.p2p.meshify.core.network.base.TransportEvent> {
        val flows = transports.values.map { it.events }
        return merge(*flows.toTypedArray())
    }

    /**
     * Start all registered transports.
     * Each transport is started independently. Failures are logged but don't stop other transports.
     */
    suspend fun startAllTransports() {
        transports.forEach { (name, transport) ->
            try {
                transport.start()
            } catch (e: Exception) {
                // Log error but continue with other transports
            }
        }
    }

    /**
     * Stop all registered transports.
     * Each transport is stopped independently. Failures are logged but don't stop other transports.
     */
    suspend fun stopAllTransports() {
        transports.forEach { (name, transport) ->
            try {
                transport.stop()
            } catch (e: Exception) {
                // Log error but continue with other transports
            }
        }
    }

    /**
     * Start discovery on all registered transports.
     */
    suspend fun startDiscoveryOnAll() {
        transports.forEach { (name, transport) ->
            try {
                transport.startDiscovery()
            } catch (e: Exception) {
                // Log error but continue with other transports
            }
        }
    }

    /**
     * Stop discovery on all registered transports.
     */
    suspend fun stopDiscoveryOnAll() {
        transports.forEach { (name, transport) ->
            try {
                transport.stopDiscovery()
            } catch (e: Exception) {
                // Log error but continue with other transports
            }
        }
    }

    /**
     * Factory method to create default transport manager with all protocols.
     * @param context Android context
     * @param settingsRepository Settings repository for configuration
     * @param peerIdentity Peer identity repository for key exchange
     * @param sessionKeyStore Shared encrypted session key store
     * @return TransportManager with default transports registered
     */
    companion object {
        fun createDefault(
            context: Context,
            settingsRepository: ISettingsRepository,
            peerIdentity: PeerIdentityRepository,
            sessionKeyStore: EncryptedSessionKeyStore
        ): TransportManager {
            val manager = TransportManager(context, settingsRepository)

            // Register LAN transport (always available)
            manager.registerTransport(
                "lan",
                LanTransportImpl(context, manager.socketManager, settingsRepository, peerIdentity, sessionKeyStore)
            )

            // Register BLE transport
            val peerId = runBlocking(Dispatchers.IO) {
                try {
                    peerIdentity.getPeerId()
                } catch (e: Exception) {
                    "unknown"
                }
            }
            val deviceName = runBlocking(Dispatchers.IO) {
                var name = "Unknown"
                settingsRepository.displayName.collect {
                    name = it ?: "Unknown"
                    return@collect
                }
                name
            }
            manager.registerTransport(
                "ble",
                BleTransportImpl(context, settingsRepository, peerId, deviceName)
            )

            // ============================================
            // Future Transports - Add with 1 line each:
            // ============================================

            // ✅ Bluetooth transport
            // manager.registerTransport("bluetooth", BluetoothTransportImpl(context, settingsRepository))

            // ✅ WiFi-Direct transport
            // manager.registerTransport("wifi_direct", WifiDirectTransportImpl(context, settingsRepository))

            // ✅ DHT transport (for internet-based P2P like BitTorrent)
            // manager.registerTransport("dht", DhtTransportImpl(context, settingsRepository))

            // ✅ UWB (Ultra-Wideband) transport
            // manager.registerTransport("uwb", UwbTransportImpl(context, settingsRepository))

            return manager
        }
    }
}
