package com.p2p.meshify.core.network.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.network.discovery.DiscoveredDevice
import com.p2p.meshify.core.network.discovery.IDiscoveryService
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections.newSetFromMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.firstOrNull

/**
 * LAN Discovery Service using mDNS (Multicast DNS).
 * Discovers peers on the local network using NSD (Network Service Discovery).
 *
 * Features:
 * - Reactive discovery based on settings
 * - Retry logic with exponential backoff
 * - Duplicate prevention
 * - Proper cleanup on stop
 */
class LanDiscoveryService(
    private val context: Context,
    private val settingsRepository: ISettingsRepository
) : IDiscoveryService {

    override val serviceName: String = "lan_mdns"
    override val isAvailable: Boolean = true // mDNS is always available on Android

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Thread-safe set for tracking devices being resolved
    private val resolvingDevices = newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Mutex for protecting discovered devices list
    private val devicesMutex = Mutex()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null

    companion object {
        private const val NSD_RETRY_DELAY_MS = 2000L // 2s initial retry
        private const val NSD_MAX_RETRIES = 3
        private const val DISCOVERY_WATCHDOG_INTERVAL_MS = 30000L // 30 seconds
    }

    override suspend fun startDiscovery(timeoutMs: Long?) {
        // Check if already running
        if (discoveryListener != null) return

        var retryCount = 0
        var started = false

        while (!started && retryCount < NSD_MAX_RETRIES) {
            try {
                discoveryListener = createDiscoveryListener()

                withTimeout(5000) { // 5s timeout for NSD operation
                    nsdManager.discoverServices(AppConfig.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    Logger.d("NSD Discovery -> Discovery started (attempt ${retryCount + 1})")
                    started = true
                }

                // Start watchdog to restart discovery if it stops
                startWatchdog()

            } catch (e: TimeoutCancellationException) {
                Logger.e("NSD Discovery -> Discovery start timeout (attempt ${retryCount + 1})", e)
                discoveryListener = null
                retryCount++

                if (retryCount < NSD_MAX_RETRIES) {
                    val delay = NSD_RETRY_DELAY_MS * (1L shl retryCount) // Exponential backoff
                    Logger.w("NSD Discovery -> Retrying in ${delay}ms...")
                    delay(delay)
                }
            } catch (e: Exception) {
                Logger.e("NSD Discovery -> Discovery Start Failed (attempt ${retryCount + 1})", e)
                discoveryListener = null
                retryCount++

                if (retryCount < NSD_MAX_RETRIES) {
                    val delay = NSD_RETRY_DELAY_MS * (1L shl retryCount)
                    Logger.w("NSD Discovery -> Retrying in ${delay}ms...")
                    delay(delay)
                }
            }
        }

        if (!started) {
            Logger.e("NSD Discovery -> Failed to start discovery after $NSD_MAX_RETRIES attempts")
        }

        // Handle optional timeout
        if (timeoutMs != null && timeoutMs > 0) {
            scope.launch {
                delay(timeoutMs)
                stopDiscovery()
            }
        }
    }

    override suspend fun stopDiscovery() {
        watchdogJob?.cancel()

        try {
            withTimeout(3000) { // 3s timeout
                discoveryListener?.let {
                    nsdManager.stopServiceDiscovery(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("NSD Discovery -> Stop Discovery Timeout", e)
        } catch (e: Exception) {
            Logger.e("NSD Discovery -> Stop Discovery Failed", e)
        } finally {
            discoveryListener = null
            resolvingDevices.clear()
        }

        Logger.d("NSD Discovery -> Discovery stopped")
    }

    override fun clearDiscoveredDevices() {
        scope.launch {
            devicesMutex.withLock {
                _discoveredDevices.value = emptyList()
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(DISCOVERY_WATCHDOG_INTERVAL_MS)
                // Restart discovery if it was stopped
                if (discoveryListener == null) {
                    Logger.d("NSD Discovery -> Watchdog restarting discovery")
                    startDiscovery(null)
                }
            }
        }
    }

    private fun createDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Logger.d("NSD Discovery -> Discovery started")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName
            if (name.startsWith("Meshify_") && !resolvingDevices.contains(name)) {
                resolvingDevices.add(name)
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, createResolveListener())
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName
            resolvingDevices.remove(name)
            if (name.startsWith("Meshify_")) {
                val peerId = name.removePrefix("Meshify_")
                scope.launch {
                    devicesMutex.withLock {
                        _discoveredDevices.value = _discoveredDevices.value.filter { it.deviceId != peerId }
                    }
                }
            }
        }

        override fun onDiscoveryStopped(regType: String) {
            Logger.d("NSD Discovery -> Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Logger.e("NSD Discovery -> Start Discovery Failed: $errorCode")
            discoveryListener = null
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Logger.e("NSD Discovery -> Stop Discovery Failed: $errorCode")
            discoveryListener = null
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            Logger.w("NSD Discovery -> Resolve failed for ${info.serviceName}: ${resolveErrorToString(errorCode)}")
            resolvingDevices.remove(info.serviceName)
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
            @Suppress("DEPRECATION")
            val address = info.host?.hostAddress ?: return
            val name = info.serviceName
            resolvingDevices.remove(name)
            val peerId = name.removePrefix("Meshify_")

            scope.launch {
                val myId = settingsRepository.getDeviceId()
                if (peerId == myId) {
                    Logger.d("NSD Discovery -> Ignoring self-discovery: $peerId")
                    return@launch
                }

                val deviceName = settingsRepository.displayName.firstOrNull() ?: "Peer_${peerId.take(4)}"

                val device = DiscoveredDevice(
                    deviceId = peerId,
                    deviceName = deviceName,
                    address = address,
                    transportType = "lan",
                    rssi = null, // RSSI will be updated by transport
                    metadata = emptyMap()
                )

                devicesMutex.withLock {
                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.deviceId == peerId }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = device
                    } else {
                        currentList.add(device)
                    }
                    _discoveredDevices.value = currentList
                }

                Logger.i("NSD Discovery -> Resolved peer: $peerId at $address")
            }
        }
    }

    private fun resolveErrorToString(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_ALREADY_ACTIVE -> "Already active"
            NsdManager.FAILURE_INTERNAL_ERROR -> "Internal error"
            else -> "Error code: $errorCode"
        }
    }
}
