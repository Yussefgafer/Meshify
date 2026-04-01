package com.p2p.meshify.core.network.discovery

import android.content.Context
import com.p2p.meshify.core.network.lan.LanDiscoveryService
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge

/**
 * Central manager for all discovery services.
 * Enables easy addition of new discovery mechanisms (Bluetooth LE, WiFi-Direct, DHT, etc.)
 * by registering them in a single place.
 *
 * Features:
 * - Merges discovered devices from all services
 * - Deduplicates devices discovered via multiple mechanisms
 * - Allows starting/stopping all services at once
 *
 * Usage:
 * ```
 * val manager = DiscoveryManager.createDefault(context, settingsRepository)
 *
 * // Future: Add Bluetooth LE discovery (1 line)
 * // manager.registerService("bluetooth_le", BluetoothLeDiscoveryService(context))
 *
 * // Future: Add WiFi-Direct discovery (1 line)
 * // manager.registerService("wifi_direct", WifiDirectDiscoveryService(context))
 *
 * // Future: Add DHT discovery (1 line)
 * // manager.registerService("dht", DhtDiscoveryService(context))
 * ```
 */
class DiscoveryManager {
    private val services = mutableMapOf<String, IDiscoveryService>()

    /**
     * Register a new discovery service.
     * @param name Unique identifier (e.g., "lan_mdns", "bluetooth_le", "wifi_direct")
     * @param service Discovery service implementation
     */
    fun registerService(name: String, service: IDiscoveryService) {
        services[name] = service
    }

    /**
     * Get a specific discovery service by name.
     * @param name Service name
     * @return Discovery service or null if not found
     */
    fun getService(name: String): IDiscoveryService? = services[name]

    /**
     * Get all registered discovery services.
     * @return List of all discovery services
     */
    fun getAllServices(): List<IDiscoveryService> = services.values.toList()

    /**
     * Get all available discovery services (hardware-supported).
     * @return List of available discovery services
     */
    fun getAvailableServices(): List<IDiscoveryService> {
        return services.values.filter { it.isAvailable }
    }

    /**
     * Get merged flow of all discovered devices from all services.
     * Deduplicates devices by deviceId.
     * @return Flow of combined discovered devices list
     */
    fun getAllDiscoveredDevicesFlow(): Flow<List<DiscoveredDevice>> {
        if (services.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        val flows = services.values.map { it.discoveredDevices }
        return combine(flows) { deviceLists: Array<out List<DiscoveredDevice>> ->
            // Merge all lists and deduplicate by deviceId
            deviceLists
                .flatMap { it }
                .distinctBy { it.deviceId }
        }
    }

    /**
     * Start discovery on all registered services.
     * Each service is started independently. Failures are logged but don't stop other services.
     * @param timeoutMs Optional timeout in milliseconds (null for indefinite)
     */
    suspend fun startDiscoveryOnAll(timeoutMs: Long? = null) {
        services.forEach { (name, service) ->
            try {
                if (service.isAvailable) {
                    service.startDiscovery(timeoutMs)
                }
            } catch (e: Exception) {
                // Log error but continue with other services
            }
        }
    }

    /**
     * Stop discovery on all registered services.
     * Each service is stopped independently. Failures are logged but don't stop other services.
     */
    suspend fun stopDiscoveryOnAll() {
        services.forEach { (name, service) ->
            try {
                service.stopDiscovery()
            } catch (e: Exception) {
                // Log error but continue with other services
            }
        }
    }

    /**
     * Clear discovered devices cache on all services.
     */
    fun clearAllDiscoveredDevices() {
        services.forEach { (_, service) ->
            try {
                service.clearDiscoveredDevices()
            } catch (e: Exception) {
                // Log error but continue with other services
            }
        }
    }

    /**
     * Factory method to create default discovery manager with all services.
     * @param context Android context
     * @param settingsRepository Settings repository for configuration
     * @return DiscoveryManager with default services registered
     */
    companion object {
        fun createDefault(
            context: Context,
            settingsRepository: ISettingsRepository
        ): DiscoveryManager {
            val manager = DiscoveryManager()

            // Register LAN mDNS discovery (always available)
            manager.registerService(
                "lan_mdns",
                LanDiscoveryService(context, settingsRepository)
            )

            // ============================================
            // Future Services - Add with 1 line each:
            // ============================================

            // ✅ Bluetooth LE discovery
            // manager.registerService("bluetooth_le", BluetoothLeDiscoveryService(context))

            // ✅ WiFi-Direct discovery
            // manager.registerService("wifi_direct", WifiDirectDiscoveryService(context))

            // ✅ DHT discovery (for internet-based P2P)
            // manager.registerService("dht", DhtDiscoveryService(context, settingsRepository))

            // ✅ QR code discovery (for manual peer addition)
            // manager.registerService("qr_code", QrCodeDiscoveryService())

            return manager
        }
    }
}
