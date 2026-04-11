package com.p2p.meshify.feature.realdevicetesting.adapter

import android.content.Context
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TestRegistry"

/**
 * Centralized registry for transport test adapters.
 *
 * This is the single entry point for the Test Engine (Session 4) and ViewModel (Session 5)
 * to access available transports. It handles:
 * - One-time initialization with Android dependencies
 * - Adapter registration and lookup
 * - Querying available transports (filtered by [TransportTestAdapter.isAvailable])
 * - Bulk shutdown of all registered adapters
 *
 * Thread safety: All mutable state is protected by a [Mutex].
 * Lifecycle: Call [initialize] exactly once before any other method.
 * Call [shutdownAll] when the testing session ends.
 *
 * This is NOT a DI-bound singleton — it uses a companion object [INSTANCE]
 * for simple global access within the feature module.
 */
class TestRegistry private constructor() {

    private val mutex = Mutex()
    private val adapters = ConcurrentHashMap<String, TransportTestAdapter>()
    private var isInitialized = false

    /**
     * Initializes the registry with all available transport adapters.
     *
     * This method MUST be called exactly once before any other registry operation.
     * It creates and registers [LanTransportTestAdapter] and [BleTransportTestAdapter]
     * (if Bluetooth is available).
     *
     * @param context Android application context.
     * @param settingsRepository Settings provider.
     * @param peerIdProvider Simple peer ID provider.
     * @param sessionKeyStore Encrypted session key store.
     * @param peerId This device's peer identity (for BLE advertising).
     * @param deviceName This device's display name (for BLE advertising).
     */
    suspend fun initialize(
        context: Context,
        settingsRepository: ISettingsRepository,
        peerIdProvider: SimplePeerIdProvider,
        sessionKeyStore: EncryptedSessionKeyStore,
        peerId: String,
        deviceName: String
    ) = mutex.withLock {
        if (isInitialized) {
            Logger.d(TAG, "Registry already initialized — skipping")
            return@withLock
        }

        Logger.i(TAG, "Initializing transport test adapter registry")

        // Register LAN transport
        val lanAdapter = LanTransportTestAdapter(
            context = context,
            settingsRepository = settingsRepository,
            peerIdProvider = peerIdProvider,
            sessionKeyStore = sessionKeyStore
        )
        adapters[lanAdapter.transportType.name.lowercase()] = lanAdapter
        Logger.i(TAG, "Registered LAN transport: ${lanAdapter.displayName}")

        // Register BLE transport (if available)
        val bleAdapter = BleTransportTestAdapter(
            context = context,
            settingsRepository = settingsRepository,
            peerId = peerId,
            deviceName = deviceName
        )
        if (bleAdapter.isAvailable) {
            adapters[bleAdapter.transportType.name.lowercase()] = bleAdapter
            Logger.i(TAG, "Registered BLE transport: ${bleAdapter.displayName}")
        } else {
            Logger.w(TAG, "BLE not available — skipping registration")
        }

        isInitialized = true
        Logger.i(TAG, "Registry initialized with ${adapters.size} transport(s)")
    }

    /**
     * Gets a transport adapter by its [TransportType].
     *
     * @param transportType The transport type to look up (LAN, BLE).
     * @return The adapter, or null if not registered or not initialized.
     */
    fun getTransport(transportType: TransportType): TransportTestAdapter? {
        if (!isInitialized) {
            Logger.e("getTransport called before initialize", tag = TAG)
            return null
        }
        return adapters[transportType.name.lowercase()]
    }

    /**
     * Gets a transport adapter by its string key.
     *
     * @param key The transport key ("lan", "ble").
     * @return The adapter, or null if not found or not initialized.
     */
    fun getTransport(key: String): TransportTestAdapter? {
        if (!isInitialized) {
            Logger.e("getTransport('$key') called before initialize", tag = TAG)
            return null
        }
        return adapters[key.lowercase()]
    }

    /**
     * Returns all registered transport adapters (including unavailable ones).
     *
     * @return List of all adapters. Empty if not initialized.
     */
    fun getAllTransports(): List<TransportTestAdapter> {
        if (!isInitialized) {
            Logger.e("getAllTransports called before initialize", tag = TAG)
            return emptyList()
        }
        return adapters.values.toList()
    }

    /**
     * Returns only the transports that are currently available on this device.
     *
     * @return List of available adapters. Empty if not initialized.
     */
    fun getAvailableTransports(): List<TransportTestAdapter> {
        if (!isInitialized) {
            Logger.e("getAvailableTransports called before initialize", tag = TAG)
            return emptyList()
        }
        return adapters.values.filter { it.isAvailable }
    }

    /**
     * Shuts down all registered transport adapters and clears the registry.
     *
     * After calling this method, the registry must be re-initialized via [initialize]
     * before it can be used again.
     *
     * This method is idempotent — safe to call multiple times.
     */
    suspend fun shutdownAll() = mutex.withLock {
        if (!isInitialized) {
            Logger.d(TAG, "Not initialized — nothing to shut down")
            return@withLock
        }

        Logger.i(TAG, "Shutting down all transport adapters (${adapters.size})")

        var successCount = 0
        var failCount = 0
        adapters.values.forEach { adapter ->
            try {
                adapter.shutdown()
                successCount++
            } catch (e: Exception) {
                Logger.e("Failed to shut down ${adapter.displayName}", e, tag = TAG)
                failCount++
            }
        }

        adapters.clear()
        isInitialized = false
        Logger.i(TAG, "All adapters shut down: $successCount OK, $failCount failed")
    }

    /**
     * Checks if the registry has been initialized.
     */
    val initialized: Boolean
        get() = isInitialized

    companion object {
        /** Global singleton instance — access via this only. */
        val INSTANCE = TestRegistry()
    }
}
