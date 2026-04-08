package com.p2p.meshify

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.data.security.impl.PeerIdentityManagerImpl
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.ble.BleTransportImpl
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.receivers.ReplyReceiver
import com.p2p.meshify.domain.repository.ISettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class.
 * Initializes Dependencies and Global Configurations.
 */
@HiltAndroidApp
class MeshifyApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var chatRepository: ChatRepositoryImpl
    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var settingsRepository: ISettingsRepository
    @Inject lateinit var peerIdentity: PeerIdentityManagerImpl

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // BLE Transport instance (created but not started until enabled in settings)
    private var bleTransport: BleTransportImpl? = null

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        Logger.i("MeshifyApp -> Application onCreate START")
        Logger.d("MeshifyApp -> Process Name: ${packageName}")

        // Start all transports
        applicationScope.launch {
            transportManager.startAllTransports()
        }

        // Start discovery on all transports
        applicationScope.launch {
            transportManager.startDiscoveryOnAll()
        }

        // Collect events from ALL transports (merged flow)
        applicationScope.launch {
            transportManager.getAllEventsFlow().collect { event ->
                when (event) {
                    is TransportEvent.PayloadReceived -> {
                        Logger.d("MeshifyApp -> Received payload from ${event.deviceId}, type=${event.payload.type}")
                        chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                    }
                    is TransportEvent.DeviceDiscovered -> {
                        Logger.i("MeshifyApp -> Device discovered: ${event.deviceId} at ${event.address} via ${event.rssi} dBm")
                    }
                    is TransportEvent.DeviceLost -> {
                        Logger.w("MeshifyApp -> Device lost: ${event.deviceId}")
                    }
                    else -> {
                        Logger.d("MeshifyApp -> Transport event: ${event::class.simpleName}")
                    }
                }
            }
        }

        // Monitor BLE enabled setting and start/stop BLE transport accordingly
        applicationScope.launch {
            settingsRepository.bleEnabled.collect { enabled ->
                if (enabled) {
                    if (bleTransport == null) {
                        val peerId = try { peerIdentity.getPeerId() } catch (e: Exception) { "unknown" }
                        val deviceName = settingsRepository.displayName.first()
                        bleTransport = BleTransportImpl(this@MeshifyApp, settingsRepository, peerId, deviceName)
                        transportManager.registerTransport("ble", bleTransport!!)
                        bleTransport?.start()
                        bleTransport?.startDiscovery()
                        Logger.i("MeshifyApp -> BLE transport enabled and started")
                    }
                } else {
                    bleTransport?.let { transport ->
                        transport.stopDiscovery()
                        transport.stop()
                        transportManager.unregisterTransport("ble")
                        Logger.i("MeshifyApp -> BLE transport disabled, stopped, and unregistered")
                    }
                    bleTransport = null
                }
            }
        }

        // Monitor transport mode setting and update TransportManager
        applicationScope.launch {
            settingsRepository.transportMode.collect { mode ->
                transportManager.setTransportMode(mode)
                Logger.i("MeshifyApp -> Transport mode set to $mode")
            }
        }

        Logger.i("MeshifyApp -> Application onCreate COMPLETE")
    }

    /**
     * Called when the application is terminating.
     * Clean up global resources to prevent memory leaks.
     */
    override fun onTerminate() {
        super.onTerminate()
        Logger.d("MeshifyApp -> Application onTerminate, cleaning up resources")
        // Clean up ReplyReceiver resources (RateLimiter coroutine scope)
        ReplyReceiver.cleanup()

        // Clean up AppContainer resources using applicationScope
        // Use launch + join to ensure cleanup completes before process death
        val cleanupJob = applicationScope.launch {
            kotlin.runCatching {
                // Stop BLE transport first
                bleTransport?.let { transport ->
                    kotlin.runCatching { transport.stop() }
                        .onFailure { Logger.e("MeshifyApp -> Failed to stop BLE transport", it) }
                }

                // Stop all transports
                transportManager.stopAllTransports()

                // Close chat repository
                chatRepository.close()
            }.onFailure { Logger.e("MeshifyApp -> Failed to cleanup resources", it) }
        }

        // Cancel scope after cleanup
        applicationScope.launch {
            cleanupJob.join()
            cancel()
        }

        Logger.d("MeshifyApp -> Cleanup completed")
    }

    /**
     * Configures Coil 3 with a robust caching strategy to prevent UI stutter.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Logger.d("MeshifyApp -> Creating Coil 3 ImageLoader")
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 2% of storage
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
