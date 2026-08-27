package com.p2p.meshify.core.network

import android.content.Context
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.domain.model.TransportMode
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

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
    internal val socketManager = SocketManager() // Changed from private to internal
    private val transports = ConcurrentHashMap<String, IMeshTransport>()
    private val transportJobs = ConcurrentHashMap<String, Job>()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Stable merged event flow — fed by a per-transport subscription created on
    // registerTransport and cancelled on unregisterTransport, so swapping a transport
    // (e.g. BLE enable/disable) never tears down the collector's flow (no event gap).
    private val _allEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)

    // Current transport mode (updated reactively by MeshifyApp)
    @Volatile
    private var transportMode: TransportMode = TransportMode.MULTI_PATH

    /**
     * Register a new transport protocol.
     * @param name Unique identifier (e.g., "lan", "bluetooth", "wifi_direct", "dht")
     * @param transport Transport implementation
     */
    fun registerTransport(name: String, transport: IMeshTransport) {
        // Cancel any existing forwarder for this name to prevent orphaned emission
        // if re-registered (e.g., BLE toggle). The prior transport is also removed
        // from the registry so it will never be returned by getTransport/... calls.
        transportJobs.remove(name)?.cancel()
        transports[name] = transport
        transportJobs[name] = transport.events
            .onEach { _allEvents.emit(it) }
            .launchIn(managerScope)
    }

    /**
     * Update the transport mode reactively.
     * Called by MeshifyApp when settings change.
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
        if (transports.remove(name) != null) {
            transportJobs.remove(name)?.cancel()
        }
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
                // Return transports where the peer is actually online
                val transportsWithPeerOnline = capableTransports.filter { transport ->
                    transport.onlinePeers.value.contains(peerId)
                }
                if (transportsWithPeerOnline.isNotEmpty()) {
                    transportsWithPeerOnline
                } else if (capableTransports.isNotEmpty()) {
                    capableTransports
                } else {
                    availableTransports
                }
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
     * Get the merged events flow from all registered transports.
     *
     * The returned flow is stable for the manager's lifetime: each transport's own
     * events flow is funneled into a single internal SharedFlow on registration and
     * its subscription is cancelled on unregistration. The collector therefore never
     * sees the flow torn down during a transport swap (e.g. BLE enable/disable),
     * so no events are dropped in the gap.
     */
    fun getAllEventsFlow(): Flow<TransportEvent> = _allEvents.asSharedFlow()

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
     * @param peerIdProvider Simple peer ID provider (UUID)
     * @return TransportManager with default transports registered
     */
    companion object {
        fun createDefault(
            context: Context,
            settingsRepository: ISettingsRepository,
            peerIdProvider: SimplePeerIdProvider
        ): TransportManager {
            val manager = TransportManager(context, settingsRepository)

            // Register LAN transport (always available)
            manager.registerTransport(
                "lan",
                LanTransportImpl(context, manager.socketManager, settingsRepository, peerIdProvider)
            )

            // NOTE: BLE transport is NOT registered here — it is managed by MeshifyApp
            // based on user settings (bleEnabled). MeshifyApp creates, registers, and
            // controls BLE lifecycle dynamically.

            // ============================================
            // Future Transports - Add with 1 line each:
            // ============================================

            // Bluetooth transport
            // manager.registerTransport("bluetooth", BluetoothTransportImpl(context, settingsRepository))

            // WiFi-Direct transport
            // manager.registerTransport("wifi_direct", WifiDirectTransportImpl(context, settingsRepository))

            // DHT transport (for internet-based P2P like BitTorrent)
            // manager.registerTransport("dht", DhtTransportImpl(context, settingsRepository))

            // UWB (Ultra-Wideband) transport
            // manager.registerTransport("uwb", UwbTransportImpl(context, settingsRepository))

            return manager
        }
    }
}
