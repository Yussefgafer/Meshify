package com.p2p.meshify.core.network

import android.content.Context
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.domain.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
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

    // Current transport mode (updated reactively by AppContainer)
    @Volatile
    private var transportMode: TransportMode = TransportMode.MULTI_PATH

    /**
     * Register a new transport protocol.
     * @param name Unique identifier (e.g., "lan", "bluetooth", "wifi_direct", "dht")
     * @param transport Transport implementation
     */
    fun registerTransport(name: String, transport: IMeshTransport) {
        transports[name] = transport
    }

    /**
     * Update the transport mode reactively.
     * Called by AppContainer when settings change.
     */
    fun setTransportMode(mode: TransportMode) {
        transportMode = mode
    }

    /**
     * Get a specific transport by name.
     * @param name Transport name
     * @return Transport implementation or null if not found
     */
    fun getTransport(name: String): IMeshTransport? = transports[name]

    /**
     * Unregister a transport protocol by name.
     * @param name Transport name to remove
     */
    fun unregisterTransport(name: String) {
        transports.remove(name)
    }

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
     * Select the best transport(s) for a given peer based on mode, capabilities, and availability.
     *
     * @param peerId The peer ID to send data to
     * @param requiredCapabilities Optional capabilities required for the operation
     * @return List of transports to use (single element for most modes, multiple for MULTI_PATH)
     */
    fun selectBestTransport(
        peerId: String,
        requiredCapabilities: Set<TransportCapability> = emptySet()
    ): List<IMeshTransport> {
        val availableTransports = getAvailableTransports()
        val capableTransports = availableTransports.filter { transport ->
            requiredCapabilities.isEmpty() || transport.capabilities.intersect(requiredCapabilities).isNotEmpty()
        }

        return when (transportMode) {
            TransportMode.MULTI_PATH -> {
                // Return ALL available transports for multi-path sending
                if (capableTransports.isNotEmpty()) capableTransports else availableTransports
            }
            TransportMode.LAN_ONLY -> {
                listOfNotNull(getTransport("lan"))
            }
            TransportMode.BLE_ONLY -> {
                listOfNotNull(getTransport("ble"))
            }
            TransportMode.AUTO -> {
                // Original behavior — pick the best single transport
                val transportWithPeer = getTransportWithPeer(peerId)
                if (transportWithPeer != null) {
                    listOf(transportWithPeer)
                } else {
                    listOfNotNull(
                        capableTransports.firstOrNull()
                            ?: availableTransports.firstOrNull { it.transportName == "lan" }
                            ?: availableTransports.firstOrNull()
                    )
                }
            }
        }
    }

    /**
     * Legacy single-transport selection (for backward compatibility).
     * @deprecated Use [selectBestTransport] which returns a list for multi-path support.
     */
    @Deprecated("Use selectBestTransport() which returns List<IMeshTransport>", ReplaceWith("selectBestTransport(peerId, requiredCapabilities).firstOrNull()"))
    fun selectBestTransportSingle(
        peerId: String,
        requiredCapabilities: Set<TransportCapability> = emptySet()
    ): IMeshTransport? {
        return selectBestTransport(peerId, requiredCapabilities).firstOrNull()
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
                Logger.e("TransportManager -> Failed to start transport '$name': ${e.message}", e)
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
                Logger.e("TransportManager -> Failed to stop transport '$name': ${e.message}", e)
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
                Logger.e("TransportManager -> Failed to start discovery on transport '$name': ${e.message}", e)
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
                Logger.e("TransportManager -> Failed to stop discovery on transport '$name': ${e.message}", e)
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

            // NOTE: BLE transport is NOT registered here — it is managed by AppContainer
            // based on user settings (bleEnabled). AppContainer creates, registers, and
            // controls BLE lifecycle dynamically.

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
