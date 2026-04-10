package com.p2p.meshify.feature.realdevicetesting.adapter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.ble.BleAdvertiser
import com.p2p.meshify.core.network.ble.BleGattClient
import com.p2p.meshify.core.network.ble.BleGattServer
import com.p2p.meshify.core.network.ble.BlePayloadSerializer
import com.p2p.meshify.core.network.ble.BleScanner
import com.p2p.meshify.core.network.ble.BleDiscoveredDevice
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BleTransportTestAdapter"

/**
 * BLE Transport adapter for the Real Device Testing feature.
 *
 * Wraps BLE-specific components (BleAdvertiser, BleScanner, BleGattServer, BleGattClient)
 * with test-oriented orchestration. Unlike [LanTransportTestAdapter], this adapter
 * builds its own BLE stack rather than wrapping BleTransportImpl — because BleTransportImpl
 * bundles identity resolution (peerId/deviceName) that the test engine resolves dynamically.
 *
 * Lifecycle contract:
 * 1. [initialize] — starts BLE advertising + GATT server
 * 2. [discoverPeers] — starts BLE scanner, collects discovered devices with timeout
 * 3. [sendTestPayload] — serializes payload into BLE chunks, sends via GATT client
 * 4. [shutdown] — stops advertising, scanning, server, client, clears all state
 *
 * BLE-specific constraints:
 * - Max 7 simultaneous connections (hardware limit)
 * - Payloads are chunked to [AppConfig.BLE_MTU_SIZE] - [AppConfig.BLE_CHUNK_HEADER_SIZE] bytes
 * - 50ms delay between chunks to avoid BLE stack overflow
 * - Bluetooth must be enabled on the device (checked in [isAvailable])
 *
 * Thread safety: All public methods are suspend functions. Send operations are serialized
 * via [sendLock] to prevent interleaved BLE chunks.
 *
 * @param context Android application context.
 * @param settingsRepository Settings provider (for device ID, display name).
 * @param peerId This device's identity UUID (used in BLE advertising data).
 * @param deviceName This device's display name (embedded in BLE advertising data).
 */
class BleTransportTestAdapter(
    private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val peerId: String,
    private val deviceName: String
) : TransportTestAdapter {

    // Transport metadata
    override val transportType: TransportType = TransportType.BLE
    override val displayName: String = "Bluetooth LE"
    override val isAvailable: Boolean by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    }

    // BLE components
    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null
    private var bleGattServer: BleGattServer? = null
    private var bleGattClient: BleGattClient? = null

    // Event collection
    override val events: Flow<TransportEvent>
        get() = _events.asStateFlow().filterNotNull()

    private val _events = MutableStateFlow<TransportEvent?>(null)

    // Discovered peers (thread-safe)
    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredPeer>()

    // Peer address map: peerId -> Bluetooth MAC address (needed for GATT connection)
    private val peerAddressMap = ConcurrentHashMap<String, String>()

    // Send lock: prevents interleaved BLE chunks
    private val sendLock = Mutex()

    // Lifecycle state
    private var scope: CoroutineScope? = null
    private var isInitialized = false

    override suspend fun initialize() {
        if (isInitialized) {
            Logger.d(TAG, "Already initialized — skipping")
            return
        }

        if (!isAvailable) {
            Logger.e("Bluetooth is not available — cannot initialize BLE transport", tag = TAG)
            return
        }

        Logger.i(TAG, "Initializing BLE transport for testing")

        // Create scope
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Create BLE components
        bleAdvertiser = BleAdvertiser(peerId = peerId, deviceName = deviceName)
        bleScanner = BleScanner()
        bleGattServer = BleGattServer(
            context = context,
            onPayloadReceived = { fromPeerId, data ->
                Logger.d(TAG, "GATT server received payload from $fromPeerId (${data.size}B)")
                // In test mode, we don't need to process incoming payloads
            },
            onClientConnected = { connectedPeerId ->
                Logger.d(TAG, "GATT client connected: $connectedPeerId")
                _events.value = TransportEvent.ConnectionEstablished(connectedPeerId)
            },
            onClientDisconnected = { disconnectedPeerId ->
                Logger.d(TAG, "GATT client disconnected: $disconnectedPeerId")
                _events.value = TransportEvent.ConnectionLost(disconnectedPeerId, "BLE disconnect")
                discoveredPeers.remove(disconnectedPeerId)
            }
        )
        bleGattClient = BleGattClient(
            context = context,
            onPayloadReceived = { fromPeerId, data ->
                Logger.d(TAG, "GATT client received payload from $fromPeerId (${data.size}B)")
            },
            onConnectionStateChanged = { connectedPeerId, isConnected ->
                Logger.d(TAG, "GATT client connection state changed: $connectedPeerId -> $isConnected")
                if (isConnected) {
                    _events.value = TransportEvent.ConnectionEstablished(connectedPeerId)
                } else {
                    _events.value = TransportEvent.ConnectionLost(connectedPeerId, "Client disconnected")
                }
            }
        )

        // Start BLE advertising and GATT server
        bleAdvertiser?.startAdvertising()
        bleGattServer?.startServer()

        isInitialized = true
        Logger.i(TAG, "BLE transport initialized (advertising + GATT server started)")
    }

    override suspend fun discoverPeers(timeoutMs: Long): List<DiscoveredPeer> {
        if (!isInitialized) {
            Logger.e("discoverPeers called before initialize", tag = TAG)
            return emptyList()
        }

        if (!isAvailable) {
            Logger.e("Bluetooth not available during discovery", tag = TAG)
            return emptyList()
        }

        val effectiveTimeout = maxOf(timeoutMs, 1_000L)
        Logger.d(TAG, "Discovering BLE peers (timeout=${effectiveTimeout}ms)")

        // Clear previous discoveries
        discoveredPeers.clear()
        peerAddressMap.clear()

        val scanner = bleScanner ?: return emptyList()

        val result = withTimeoutOrNull(effectiveTimeout) {
            // Collect BLE scanner flow
            val collectJob = scope?.launch {
                scanner.discoveryFlow
                    .catch { Logger.e("BLE discovery error", it, tag = TAG) }
                    .conflate()
                    .collect { device ->
                        handleBleDeviceDiscovered(device)
                    }
            }

            // Start BLE scanning
            scanner.startScanning()

            // Wait for timeout
            collectJob?.join()

            // Stop scanning after timeout
            scanner.stopScanning()

            discoveredPeers.values.toList()
        }

        // Ensure scanning is stopped
        try {
            scanner.stopScanning()
        } catch (_: Exception) {
            // Ignore — scanner may already be stopped
        }

        val found = result ?: emptyList()
        Logger.i(TAG, "BLE discovery complete: ${found.size} peer(s) found")
        return found
    }

    override suspend fun sendTestPayload(
        peerId: String,
        payloadType: Payload.PayloadType,
        testData: ByteArray
    ): TestSendResult {
        if (!isInitialized) {
            return TestSendResult.failure(
                error = "BLE transport not initialized",
                durationMs = 0L,
                bytesSent = 0
            )
        }

        if (!isAvailable) {
            return TestSendResult.failure(
                error = "Bluetooth not available",
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

        // Acquire send lock to prevent interleaved chunks
        return sendLock.withLock {
            Logger.d(TAG, "Sending BLE test payload to $peerId (type=$payloadType, size=${testData.size}B)")

            val payload = Payload(
                id = UUID.randomUUID().toString(),
                senderId = this@BleTransportTestAdapter.peerId,
                timestamp = System.currentTimeMillis(),
                type = payloadType,
                data = testData
            )

            val startTime = System.currentTimeMillis()
            try {
                // Serialize payload into BLE chunks
                val chunks = BlePayloadSerializer.serializeToChunks(payload)
                val gattClient = bleGattClient ?: run {
                    return@withLock TestSendResult.failure(
                        error = "GATT client not available",
                        durationMs = System.currentTimeMillis() - startTime,
                        bytesSent = 0
                    )
                }

                // Connect to peer if not already connected
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val btDevice = bluetoothAdapter?.getRemoteDevice(peerAddressMap[peerId])
                if (btDevice != null && !gattClient.isConnected(peerId)) {
                    gattClient.connect(btDevice, peerId)
                    // sendData() suspends on characteristicsReady.await() — no delay needed
                }

                // Send each chunk
                return@withLock sendChunks(
                    gattClient = gattClient,
                    peerId = peerId,
                    chunks = chunks,
                    startTime = startTime
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Logger.e("BLE send exception: $peerId", e, tag = TAG)
                TestSendResult.failure(
                    error = e.message ?: "Unexpected BLE send error",
                    durationMs = duration,
                    bytesSent = 0,
                    exception = e
                )
            }
        }
    }

    /**
     * Sends all BLE chunks to the peer with proper serialization.
     * The GATT client handles inter-chunk pacing internally.
     *
     * @return [TestSendResult] — success if all chunks sent, failure on first error.
     */
    private suspend fun sendChunks(
        gattClient: BleGattClient,
        peerId: String,
        chunks: List<ByteArray>,
        startTime: Long
    ): TestSendResult {
        for ((index, chunk) in chunks.withIndex()) {
            val chunkResult = gattClient.sendData(peerId, chunk)
            if (chunkResult.isFailure) {
                val duration = System.currentTimeMillis() - startTime
                val error = chunkResult.exceptionOrNull()?.message ?: "Chunk $index send failed"
                Logger.e("BLE chunk $index/${chunks.size} failed: $error", tag = TAG)
                return TestSendResult.failure(
                    error = "Chunk $index failed: $error",
                    durationMs = duration,
                    bytesSent = index * BlePayloadSerializer.getMaxChunkDataSize(),
                    exception = chunkResult.exceptionOrNull()
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Logger.i(TAG, "BLE send success: $peerId in ${duration}ms (${chunks.size} chunks)")
        return TestSendResult.success(durationMs = duration, bytesSent = chunks.sumOf { it.size })
    }

    override suspend fun shutdown() {
        if (!isInitialized) {
            Logger.d(TAG, "Not initialized — nothing to shut down")
            return
        }

        Logger.i(TAG, "Shutting down BLE transport adapter")

        // Stop BLE components
        try {
            bleScanner?.stopScanning()
            bleAdvertiser?.stopAdvertising()
            bleGattServer?.stopServer()
            bleGattClient?.cleanup()
        } catch (e: Exception) {
            Logger.e("Error stopping BLE components", e, tag = TAG)
        }

        // Cancel scope
        scope?.cancel()
        scope = null

        // Clear state
        discoveredPeers.clear()
        peerAddressMap.clear()
        bleAdvertiser = null
        bleScanner = null
        bleGattServer = null
        bleGattClient = null
        isInitialized = false

        Logger.i(TAG, "BLE transport adapter shut down")
    }

    /**
     * Handles a BLE device discovered event from the scanner.
     * Converts [BleDiscoveredDevice] to [DiscoveredPeer] and stores in [discoveredPeers].
     */
    private fun handleBleDeviceDiscovered(device: BleDiscoveredDevice) {
        // Store MAC address for GATT connection
        peerAddressMap[device.peerId] = device.device.address

        val peer = DiscoveredPeer(
            id = device.peerId,
            name = device.deviceName,
            address = device.device.address,
            transportType = TransportType.BLE,
            rssi = device.rssi
        )
        discoveredPeers[device.peerId] = peer

        _events.value = TransportEvent.DeviceDiscovered(
            deviceId = device.peerId,
            deviceName = device.deviceName,
            address = device.device.address,
            rssi = device.rssi,
            transportType = TransportType.BLE
        )

        Logger.d(TAG, "BLE discovered: ${device.deviceName} (${device.peerId}) RSSI=${device.rssi}")
    }
}
