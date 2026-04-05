package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BleTransport"

/**
 * BLE Transport Implementation.
 * 
 * Implements IMeshTransport for BLE communication.
 * Uses BleAdvertiser for discovery, BleScanner for peer discovery,
 * and BleGattServer/Client for data transfer.
 * 
 * Supports multi-path transmission (LAN + BLE simultaneously).
 */
class BleTransportImpl(
    private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val peerId: String,
    private val deviceName: String
) : IMeshTransport {

    // Transport metadata
    override val transportName: String = "ble"
    override val isAvailable: Boolean by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    }

    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.LOW_POWER,
        TransportCapability.LOW_LATENCY,
        TransportCapability.OFFLINE,
        TransportCapability.MESH_NETWORKING // Future-ready
    )

    // Event flows
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    // Peer tracking
    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    override val onlinePeers: StateFlow<Set<String>> = _onlinePeers

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    override val typingPeers: StateFlow<Set<String>> = _typingPeers

    // BLE components
    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null
    private var bleGattServer: BleGattServer? = null
    private var bleGattClient: BleGattClient? = null
    private var connectionPool: BleConnectionPool? = null

    // State
    private var isStarted = false
    private var isDiscovering = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null // Track discovery coroutine for cancellation
    private val sendLock = Mutex() // Prevent concurrent sends that would interleave chunks

    // Peer MAC address map: peerId -> BluetoothDevice MAC
    private val peerAddressMap = ConcurrentHashMap<String, String>()

    /**
     * Start the BLE transport (server + advertising).
     */
    override suspend fun start() {
        if (isStarted) {
            Logger.d("BLE Transport already started, skipping", tag = TAG)
            return
        }

        Logger.d("Starting BLE Transport...", tag = TAG)

        try {
            // Initialize connection pool
            connectionPool = BleConnectionPool()

            // Initialize GATT Server
            bleGattServer = BleGattServer(
                context = context,
                onPayloadReceived = { peerId, data ->
                    scope.launch { handleIncomingPayload(peerId, data) }
                },
                onClientConnected = { clientPeerId ->
                    scope.launch { handleClientConnected(clientPeerId) }
                },
                onClientDisconnected = { clientPeerId ->
                    scope.launch { handleClientDisconnected(clientPeerId) }
                }
            )
            bleGattServer?.startServer()

            // Initialize GATT Client
            bleGattClient = BleGattClient(
                context = context,
                onPayloadReceived = { peerId, data ->
                    scope.launch { handleIncomingPayload(peerId, data) }
                },
                onConnectionStateChanged = { peerId, connected ->
                    scope.launch { handleConnectionStateChanged(peerId, connected) }
                }
            )

            // Start advertising
            bleAdvertiser = BleAdvertiser(
                peerId = peerId,
                deviceName = deviceName
            )
            bleAdvertiser?.startAdvertising()

            isStarted = true
            Logger.d("BLE Transport started successfully", tag = TAG)

            _events.emit(TransportEvent.ConnectionEstablished("ble_transport"))
            
            // Start periodic cleanup of stale buffers and idle connections
            startPeriodicCleanup()
        } catch (e: Exception) {
            Logger.e("BLE Failed to start: ${e.message}", e, tag = TAG)
            _events.emit(TransportEvent.Error("BLE start failed: ${e.message}", e))
        }
    }

    /**
     * Stop the BLE transport.
     */
    override suspend fun stop() {
        if (!isStarted) return

        Logger.d("Stopping BLE Transport...", tag = TAG)

        try {
            bleAdvertiser?.stopAdvertising()
            bleScanner?.stopScanning()
            bleGattServer?.stopServer()
            bleGattClient?.cleanup()
            connectionPool?.clearAll()

            isStarted = false
            isDiscovering = false
            _onlinePeers.value = emptySet()
            _typingPeers.value = emptySet()
            peerAddressMap.clear()
            
            // Cancel all coroutines in this transport's scope
            scope.cancel()

            Logger.d("BLE Transport stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Start BLE peer discovery (scanning).
     */
    override suspend fun startDiscovery() {
        if (isDiscovering) {
            Logger.d("BLE Discovery already running, skipping", tag = TAG)
            return
        }

        Logger.d("Starting BLE Discovery...", tag = TAG)

        try {
            bleScanner = BleScanner()
            
            // Collect discovered devices — track job for cancellation
            discoveryJob = scope.launch {
                bleScanner?.discoveryFlow?.collect { device ->
                    handleDeviceDiscovered(device)
                }
            }

            bleScanner?.startScanning()
            isDiscovering = true
            Logger.d("BLE Discovery started", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to start discovery: ${e.message}", e, tag = TAG)
            _events.emit(TransportEvent.Error("BLE discovery failed: ${e.message}", e))
        }
    }

    /**
     * Stop BLE peer discovery.
     */
    override suspend fun stopDiscovery() {
        if (!isDiscovering) return

        Logger.d("Stopping BLE Discovery...", tag = TAG)

        try {
            // Cancel discovery coroutine
            discoveryJob?.cancel()
            discoveryJob = null
            
            bleScanner?.stopScanning()
            bleScanner?.cleanup()
            bleScanner = null
            isDiscovering = false
            Logger.d("BLE Discovery stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop discovery: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Send payload to a peer via BLE.
     * Chunks the payload if needed to fit BLE MTU.
     * Thread-safe: uses sendLock to prevent concurrent sends.
     */
    override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
        return sendLock.withLock {
            try {
                // Serialize payload to chunks
                val chunks = BlePayloadSerializer.serializeToChunks(payload)
                val client = bleGattClient ?: return@withLock Result.failure(IllegalStateException("BLE Client not initialized"))

                // Send all chunks sequentially, fail fast on any error
                for ((index, chunk) in chunks.withIndex()) {
                    val result = client.sendData(targetDeviceId, chunk)
                    if (result.isFailure) {
                        Logger.e("BLE Payload send failed at chunk $index/${chunks.size} to $targetDeviceId", tag = TAG)
                        return@withLock Result.failure(result.exceptionOrNull() ?: java.io.IOException("Chunk $index failed"))
                    }
                }

                // Update connection pool
                connectionPool?.updateLastUsed(targetDeviceId)

                Logger.d("BLE Sent payload ${payload.id} to $targetDeviceId (${payload.data.size} bytes in ${chunks.size} chunks)", tag = TAG)
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("BLE Failed to send payload to $targetDeviceId: ${e.message}", e, tag = TAG)
                Result.failure(e)
            }
        }
    }

    /**
     * Handle a discovered BLE device.
     */
    private suspend fun handleDeviceDiscovered(device: BleDiscoveredDevice) {
        Logger.d("BLE Device discovered: ${device.peerId} (${device.deviceName}) RSSI: ${device.rssi}", tag = TAG)

        // Store device address for later connection
        peerAddressMap[device.peerId] = device.device.address

        // Emit discovery event
        _events.emit(
            TransportEvent.DeviceDiscovered(
                deviceId = device.peerId,
                deviceName = device.deviceName,
                address = device.device.address,
                rssi = device.rssi,
                transportType = com.p2p.meshify.domain.model.TransportType.BLE
            )
        )

        // Update online peers
        val currentPeers = _onlinePeers.value.toMutableSet()
        currentPeers.add(device.peerId)
        _onlinePeers.value = currentPeers

        // Auto-connect to discovered peer — check pool room BEFORE connecting
        if (connectionPool?.addConnection(device.peerId, BleConnectionType.CLIENT) == true) {
            bleGattClient?.connect(device.device, device.peerId)
        } else {
            Logger.w("BLE Connection pool full, cannot auto-connect to ${device.peerId}", tag = TAG)
        }
    }

    /**
     * Handle incoming payload from a peer.
     * Reassembles chunks and emits to the event stream.
     */
    private suspend fun handleIncomingPayload(peerId: String, data: ByteArray) {
        try {
            val payload = BlePayloadSerializer.processChunkForKey(peerId, data)
            if (payload != null) {
                Logger.d("BLE Reassembled payload ${payload.id} from $peerId", tag = TAG)
                _events.emit(TransportEvent.PayloadReceived(peerId, payload))
            }
        } catch (e: Exception) {
            Logger.e("BLE Error processing payload from $peerId: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Handle client connected to GATT Server.
     */
    private suspend fun handleClientConnected(clientPeerId: String) {
        Logger.d("BLE Client connected: $clientPeerId", tag = TAG)

        connectionPool?.addConnection(clientPeerId, BleConnectionType.SERVER)

        val currentPeers = _onlinePeers.value.toMutableSet()
        currentPeers.add(clientPeerId)
        _onlinePeers.value = currentPeers

        _events.emit(TransportEvent.ConnectionEstablished(clientPeerId))
    }

    /**
     * Handle client disconnected from GATT Server.
     */
    private suspend fun handleClientDisconnected(clientPeerId: String) {
        Logger.d("BLE Client disconnected: $clientPeerId", tag = TAG)

        connectionPool?.removeConnection(clientPeerId)
        peerAddressMap.remove(clientPeerId)

        val currentPeers = _onlinePeers.value.toMutableSet()
        currentPeers.remove(clientPeerId)
        _onlinePeers.value = currentPeers

        _events.emit(TransportEvent.ConnectionLost(clientPeerId, "disconnected"))
    }

    /**
     * Handle BLE client connection state changes.
     */
    private suspend fun handleConnectionStateChanged(peerId: String, connected: Boolean) {
        if (connected) {
            handleClientConnected(peerId)
        } else {
            handleClientDisconnected(peerId)
        }
    }

    /**
     * Clean up periodic tasks.
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(30_000L) // Every 30 seconds
                connectionPool?.cleanupIdleConnections()
                BlePayloadSerializer.cleanupStaleBuffers()
            }
        }
    }

    /**
     * Get BluetoothAdapter for external use.
     */
    fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    /**
     * Check if BLE is enabled.
     */
    fun isBleEnabled(): Boolean {
        return getBluetoothAdapter()?.isEnabled == true
    }
}
