package com.p2p.meshify.feature.realdevicetesting.adapter

import android.content.Context
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LanTransportTestAdapter"

/**
 * LAN Transport adapter for the Real Device Testing feature.
 *
 * Wraps [LanTransportImpl] with test-specific orchestration:
 * - Own [SocketManager] instance (not shared with production TransportManager)
 * - Discovery with timeout and structured [DiscoveredPeer] results
 * - Test payload send with timing measurement and [TestSendResult]
 * - Clean lifecycle management (initialize/shutdown are idempotent)
 *
 * Thread safety: All public methods are suspend functions on a [SupervisorJob] scope.
 * Internal state is protected via [ConcurrentHashMap] and atomic flags.
 *
 * @param context Android application context.
 * @param settingsRepository Settings provider (for device ID, display name, etc.).
 * @param peerIdProvider Simple peer ID provider.
 */
class LanTransportTestAdapter(
    private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val peerIdProvider: SimplePeerIdProvider
) : TransportTestAdapter {

    // Transport metadata
    override val transportType: TransportType = TransportType.LAN
    override val displayName: String = "LAN (Wi-Fi)"
    override val isAvailable: Boolean = true // LAN is always available on Android

    // Underlying transport
    private val socketManager = SocketManager()
    private lateinit var transport: LanTransportImpl

    // Event collection
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)

    override val events: Flow<TransportEvent> = _events.asSharedFlow()
    private var eventCollectionJob: Job? = null

    // Discovered peers (thread-safe)
    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredPeer>()

    // Lifecycle state
    private var scope: CoroutineScope? = null
    private var isInitialized = false

    override suspend fun initialize() {
        if (isInitialized) {
            Logger.d("Already initialized — skipping", tag = TAG)
            return
        }

        Logger.i("Initializing LAN transport for testing", tag = TAG)

        // Create the underlying transport
        transport = LanTransportImpl(
            context = context,
            socketManager = socketManager,
            settingsRepository = settingsRepository,
            peerIdProvider = peerIdProvider
        )

        // Create scope for this adapter's lifecycle
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Start the transport
        transport.start()

        // Start discovery
        transport.startDiscovery()

        // Begin collecting events
        startEventCollection()

        isInitialized = true
        Logger.i("LAN transport initialized, discovery started", tag = TAG)
    }

    override suspend fun discoverPeers(timeoutMs: Long): List<DiscoveredPeer> {
        if (!isInitialized) {
            Logger.e("discoverPeers called before initialize", tag = TAG)
            return emptyList()
        }

        val effectiveTimeout = maxOf(timeoutMs, 1_000L)
        Logger.d("Discovering peers (timeout=${effectiveTimeout}ms)", tag = TAG)

        // Clear previous discoveries for a fresh scan
        discoveredPeers.clear()

        // Collector runs as a child of the timeout scope so it is cancelled automatically on
        // timeout — no leaked (zombie) collector. Peers are read from the map after the timeout,
        // not from the block's return value (which is null on timeout).
        withTimeoutOrNull(effectiveTimeout) {
            launch {
                transport.events
                    .catch { Logger.e("Event collection error", it, tag = TAG) }
                    .conflate()
                    .collect { event -> handleTransportEvent(event) }
            }
            awaitCancellation()
        }

        val found = discoveredPeers.values.toList()
        Logger.i("Discovery complete: ${found.size} peer(s) found", tag = TAG)
        return found
    }

    override suspend fun sendTestPayload(
        peerId: String,
        payloadType: Payload.PayloadType,
        testData: ByteArray
    ): TestSendResult {
        if (!isInitialized) {
            return TestSendResult.failure(
                error = "Transport not initialized",
                durationMs = 0L,
                bytesSent = 0
            )
        }

        val peer = discoveredPeers[peerId]
        if (peer == null) {
            return TestSendResult.failure(
                error = "Peer '$peerId' not in discovered list — call discoverPeers first",
                durationMs = 0L,
                bytesSent = 0
            )
        }

        Logger.d("Sending test payload to $peerId (type=$payloadType, size=${testData.size}B)", tag = TAG)

        val payload = Payload(
            id = UUID.randomUUID().toString(),
            senderId = settingsRepository.getDeviceId(),
            timestamp = System.currentTimeMillis(),
            type = payloadType,
            data = testData
        )

        val startTime = System.currentTimeMillis()
        return try {
            val result = transport.sendPayload(peerId, payload)
            val duration = System.currentTimeMillis() - startTime
            result.fold(
                onSuccess = {
                    Logger.i("Send success: $peerId in ${duration}ms", tag = TAG)
                    TestSendResult.success(
                        durationMs = duration,
                        bytesSent = testData.size
                    )
                },
                onFailure = { ex ->
                    Logger.e("Send failed: $peerId in ${duration}ms", ex, tag = TAG)
                    TestSendResult.failure(
                        error = ex.message ?: "Unknown send error",
                        durationMs = duration,
                        bytesSent = 0,
                        exception = ex
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Logger.e("Send exception: $peerId", e, tag = TAG)
            TestSendResult.failure(
                error = e.message ?: "Unexpected error",
                durationMs = duration,
                bytesSent = 0,
                exception = e
            )
        }
    }

    override suspend fun shutdown() {
        if (!isInitialized) {
            Logger.d("Not initialized — nothing to shut down", tag = TAG)
            return
        }

        Logger.i("Shutting down LAN transport adapter", tag = TAG)

        // Stop event collection
        eventCollectionJob?.cancel()
        eventCollectionJob = null

        // Stop the underlying transport
        try {
            transport.stop()
        } catch (e: Exception) {
            Logger.e("Error stopping transport", e, tag = TAG)
        }

        // Cancel scope
        scope?.cancel()
        scope = null

        // Clear state
        discoveredPeers.clear()
        isInitialized = false

        Logger.i("LAN transport adapter shut down", tag = TAG)
    }

    /**
     * Handles a transport event during discovery.
     * Updates the [discoveredPeers] map and logs appropriately.
     */
    private fun handleTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.DeviceDiscovered -> {
                val peer = DiscoveredPeer(
                    id = event.deviceId,
                    name = event.deviceName,
                    address = event.address,
                    transportType = event.transportType,
                    rssi = event.rssi
                )
                discoveredPeers[event.deviceId] = peer
                Logger.d("Discovered: ${event.deviceName} (${event.address})", tag = TAG)
            }
            is TransportEvent.DeviceLost -> {
                discoveredPeers.remove(event.deviceId)
                Logger.d("Lost: ${event.deviceId}", tag = TAG)
            }
            is TransportEvent.Error -> {
                Logger.w("Discovery error: ${event.message}", tag = TAG)
            }
            else -> { /* Ignore non-discovery events */ }
        }
    }

    /**
     * Starts collecting transport events into the internal [_events] state flow.
     * Must be called after [transport] is created.
     */
    private fun startEventCollection() {
        eventCollectionJob = scope?.launch {
            transport.events
                .catch { Logger.e("Transport event error", it, tag = TAG) }
                .collect { event ->
                    _events.tryEmit(event)
                }
        }
    }
}
