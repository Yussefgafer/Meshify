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
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "BleTransport"

private const val SCAN_EXPIRY_MS = 30_000L // Drop peers not seen advertising for this long

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
    override val isAvailable: Boolean
        get() {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return bluetoothManager?.adapter?.isEnabled == true
        }

    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.LOW_POWER,
        TransportCapability.OFFLINE
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
    private var scope: CoroutineScope? = null
    private var discoveryJob: Job? = null // Track discovery coroutine for cancellation

    // One mutex per resolved connection key: serializes chunks to a single peer without
    // head-of-line blocking sends to other peers
    private val sendLocks = ConcurrentHashMap<String, Mutex>()

    private fun sendLockFor(connectionKey: String): Mutex =
        sendLocks.computeIfAbsent(connectionKey) { Mutex() }

    // Canonical identity registry: mesh UUID -> MAC (device.address). The MAC is the one
    // internal connection key shared by the scan path, the GATT client registry and the
    // GATT server registry; the mesh UUID stays the public API surface (sendPayload targets,
    // onlinePeers, emitted events). Reverse lookup is a linear scan bounded by the
    // distinct peers seen this session; alias convergence in handleIncomingPayload
    // keeps it near one alias per peer.
    private val macByMeshId = ConcurrentHashMap<String, String>()

    private fun registerAlias(meshId: String, mac: String) {
        if (meshId.isBlank() || mac.isBlank()) return
        macByMeshId[meshId] = mac
    }

    private fun meshIdForMac(mac: String): String? =
        macByMeshId.entries.firstOrNull { it.value == mac }?.key

    // MACs linked to OUR GATT server whose alias is still unknown; drained when the
    // alias resolves (connect with known alias, first inbound payload, disconnect)
    private val pendingLinkMacs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Open GATT links per MAC (client + server roles combined). Presence for a MAC
    // survives disconnect callbacks until its last link drops.
    private val activeLinkCounts = ConcurrentHashMap<String, AtomicInteger>()

    private fun incLink(mac: String) {
        activeLinkCounts.getOrPut(mac) { AtomicInteger(0) }.incrementAndGet()
    }

    /** Returns true when at least one link to [mac] remains open after this decrement */
    private fun decLink(mac: String): Boolean {
        val counter = activeLinkCounts[mac] ?: return false
        val remaining = counter.decrementAndGet()
        if (remaining <= 0) activeLinkCounts.remove(mac, counter)
        return remaining > 0
    }

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
            // Create a fresh scope for this transport instance
            // Reinitialize if null or cancelled from a previous stop() cycle
            if (scope == null || !scope!!.isActive) {
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            }

            // Initialize connection pool
            connectionPool = BleConnectionPool()

            // Initialize GATT Server
            bleGattServer = BleGattServer(
                context = context,
                onPayloadReceived = { peerId, data ->
                    scope?.launch { handleIncomingPayload(peerId, data) }
                },
                onClientConnected = { address ->
                    scope?.launch { handleClientConnected(address) }
                },
                onClientDisconnected = { address ->
                    scope?.launch { handleClientDisconnected(address) }
                }
            )
            bleGattServer?.startServer()
            // Honest started-state: only mark transport started once the system confirms
            // service addition (or fail loudly if it does not).
            val serverReady = try {
                withTimeout(AppConfig.BLE_READY_TIMEOUT_MS) {
                    bleGattServer?.awaitForServiceAdded() ?: false
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Logger.e("BLE Server service-add await timed out after ${AppConfig.BLE_READY_TIMEOUT_MS}ms", tag = TAG)
                false
            } catch (e: CancellationException) {
                throw e
            }
            if (!serverReady) {
                Logger.e("BLE GATT server service-add failed; tearing down transport", tag = TAG)
                bleGattServer?.stopServer()
                _events.emit(TransportEvent.Error("BLE GATT service-add failed", null))
                return
            }

            // Initialize GATT Client
            bleGattClient = BleGattClient(
                context = context,
                onPayloadReceived = { peerId, data ->
                    scope?.launch { handleIncomingPayload(peerId, data) }
                },
                onConnectionStateChanged = { peerId, connected ->
                    scope?.launch { handleConnectionStateChanged(peerId, connected) }
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

            _events.emit(TransportEvent.ConnectionEstablished(peerId))
            
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
            macByMeshId.clear()
            pendingLinkMacs.clear()
            activeLinkCounts.clear()

            // Cancel and nullify the scope
            scope?.cancel()
            scope = null

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
            val currentScope = scope ?: return
            discoveryJob = currentScope.launch {
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
     * Thread-safe: per-peer lock prevents chunk interleaving to the same peer.
     * Bounded by BLE_SEND_TIMEOUT_MS so one hung GATT operation cannot wedge the sender.
     * Resolves the mesh UUID to its MAC alias; when only a server-side link exists,
     * bridges it into a client connection before sending.
     */
    override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
        val client = bleGattClient
            ?: return Result.failure(IllegalStateException("BLE Client not initialized"))
        val mac = macByMeshId[targetDeviceId]
            ?: return Result.failure(IllegalStateException("Unknown peer $targetDeviceId"))
        return try {
            withTimeout(AppConfig.BLE_SEND_TIMEOUT_MS) {
                sendLockFor(mac).withLock {
                    var ready = client.awaitReady(mac)
                    if (!ready) {
                        // Peer links to OUR GATT server while we hold no client link to them:
                        // bridge by connecting out under the same address, then recheck readiness
                        val serverSideDevice = bleGattServer?.getConnectedDevice(mac)
                        if (serverSideDevice != null) {
                            client.connect(serverSideDevice, mac)
                            ready = client.awaitReady(mac)
                            if (ready) connectionPool?.markActive(mac)
                        }
                    }
                    if (!ready) {
                        return@withLock Result.failure(IllegalStateException("Not connected to $targetDeviceId"))
                    }
                    val negotiatedMtu = client.getNegotiatedMtu(mac)
                        ?: return@withLock Result.failure(IllegalStateException("Not connected to $targetDeviceId"))

                    // Size chunks from the live negotiated MTU, not the requested one
                    val chunks = BlePayloadSerializer.serializeToChunks(
                        payload,
                        negotiatedMtu - AppConfig.BLE_ATT_OVERHEAD_BYTES
                    )
                    if (chunks.isEmpty()) {
                        return@withLock Result.failure(IllegalStateException("Serialization produced no chunks for payload ${payload.id}"))
                    }

                    // Send all chunks sequentially, fail fast on any error
                    for ((index, chunk) in chunks.withIndex()) {
                        val result = client.sendData(mac, chunk)
                        if (result.isFailure) {
                            Logger.e("BLE Payload send failed at chunk $index/${chunks.size} to $targetDeviceId", tag = TAG)
                            return@withLock Result.failure(result.exceptionOrNull() ?: java.io.IOException("Chunk $index failed"))
                        }
                    }

                    Logger.d("BLE Sent payload ${payload.id} to $targetDeviceId (${payload.data.size} bytes in ${chunks.size} chunks)", tag = TAG)
                    connectionPool?.markActive(mac)
                    Result.success(Unit)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("BLE Send timed out after ${AppConfig.BLE_SEND_TIMEOUT_MS}ms to $targetDeviceId", tag = TAG)
            Result.failure(java.io.IOException("BLE send timeout after ${AppConfig.BLE_SEND_TIMEOUT_MS}ms"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("BLE Failed to send payload to $targetDeviceId: ${e.message}", e, tag = TAG)
            Result.failure(e)
        }
    }

    /**
     * Handle a discovered BLE device.
     */
    private suspend fun handleDeviceDiscovered(device: BleDiscoveredDevice) {
        Logger.d("BLE Device discovered: ${device.peerId} (${device.deviceName}) RSSI: ${device.rssi}", tag = TAG)

        val mac = device.device.address.uppercase()

        // Advertised mesh id -> canonical MAC, registered before any connection exists
        registerAlias(device.peerId, mac)

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

        // Update online peers atomically
        _onlinePeers.update { it + device.peerId }

        // Auto-connect to discovered peer — check pool room BEFORE connecting
        if (connectionPool?.addConnection(mac, BleConnectionType.CLIENT) == true) {
            bleGattClient?.connect(device.device, mac)
        } else {
            Logger.w("BLE Connection pool full, cannot auto-connect to ${device.peerId}", tag = TAG)
        }
    }

    /**
     * Handle incoming payload from a peer.
     * Reassembles chunks and emits to the event stream.
     */
    private suspend fun handleIncomingPayload(linkKey: String, data: ByteArray) {
        try {
            val payload = BlePayloadSerializer.processChunkForKey(linkKey, data)
            if (payload != null) {
                // linkKey is the MAC of whichever GATT side (client or server) delivered the data
                val senderIdentity = payload.senderId.ifBlank { meshIdForMac(linkKey) }
                if (senderIdentity.isNullOrBlank()) {
                    Logger.e("BLE Dropping payload ${payload.id} with no resolvable sender identity", tag = TAG)
                    return
                }
                // Identity convergence: this MAC now belongs to senderIdentity alone.
                // Evict competing aliases (e.g. a scan-time synthetic ble_* id) pointing
                // at the same MAC, then re-key presence to the real mesh UUID in one
                // atomic transform.
                val staleIds = macByMeshId.entries.asSequence()
                    .filter { it.key != senderIdentity && it.value == linkKey }
                    .map { it.key }
                    .toList()
                staleIds.forEach { macByMeshId.remove(it, linkKey) }
                registerAlias(senderIdentity, linkKey)
                pendingLinkMacs.remove(linkKey)
                _onlinePeers.update { peers -> (peers - staleIds.toSet()) + senderIdentity }
                Logger.d("BLE Reassembled payload ${payload.id} from $senderIdentity", tag = TAG)
                connectionPool?.markActive(linkKey)
                _events.emit(TransportEvent.PayloadReceived(senderIdentity, payload))
            }
        } catch (e: Exception) {
            Logger.e("BLE Error processing payload from $linkKey: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Handle client connected to GATT Server.
     */
    private suspend fun handleClientConnected(address: String, connectionType: BleConnectionType = BleConnectionType.SERVER) {
        Logger.d("BLE Client connected: $address (${connectionType.name})", tag = TAG)

        connectionPool?.addConnection(address, connectionType)
        // Client-role link-ups reach this handler too via handleConnectionStateChanged,
        // so one increment here counts every live GATT link regardless of its side
        incLink(address)

        // Unknown alias means no mesh UUID to key the event with yet —
        // ConnectionEstablished is deferred to the first inbound payload
        val meshId = meshIdForMac(address)
        if (meshId == null) {
            pendingLinkMacs.add(address)
            return
        }
        pendingLinkMacs.remove(address)
        _onlinePeers.update { it + meshId }

        _events.emit(TransportEvent.ConnectionEstablished(meshId))
    }

    /**
     * Handle client disconnected from GATT Server.
     */
    private suspend fun handleClientDisconnected(address: String) {
        Logger.d("BLE Client disconnected: $address", tag = TAG)

        connectionPool?.removeConnection(address)
        pendingLinkMacs.remove(address)

        // The opposite-direction link (client or server side) may still hold this MAC
        // open; presence is dropped only when the last link goes away
        if (decLink(address)) return

        val meshId = meshIdForMac(address) ?: return
        _onlinePeers.update { it - meshId }

        _events.emit(TransportEvent.ConnectionLost(meshId, "disconnected"))
    }

    /**
     * Handle BLE client connection state changes.
     */
    private suspend fun handleConnectionStateChanged(address: String, connected: Boolean) {
        if (connected) {
            handleClientConnected(address, BleConnectionType.CLIENT)
        } else {
            handleClientDisconnected(address)
        }
    }

    /**
     * Clean up periodic tasks.
     */
    private fun startPeriodicCleanup() {
        val currentScope = scope ?: return
        currentScope.launch {
            while (isActive) {
                delay(30_000L) // Every 30 seconds
                connectionPool?.cleanupIdleConnections()
                BlePayloadSerializer.cleanupStaleBuffers()
                emitScanExpiry()
            }
        }
    }

    /**
     * Emits DeviceLost for any peer in _onlinePeers whose MAC hasn't been seen in the
     * scanner's seenDevices map for longer than [SCAN_EXPIRY_MS]. Drops them from
     * _onlinePeers. Bridges the gap that BLE scan callbacks never fire `onLost`.
     *
     * Only acts when scanning is active; offline transports never expiry peers.
     */
    private suspend fun emitScanExpiry() {
        if (!isDiscovering) return
        val scanner = bleScanner ?: return
        val now = System.currentTimeMillis()
        // A peer with a live GATT link (link-up on either side) stays online regardless of
        // advertising visibility — Android routinely drops advertisements under 2.4GHz
        // interference or Doze mode while the GATT link stays alive. Only expiry peers
        // that have been scan-quiet AND have no link surviving.
        val stalePeerIds = _onlinePeers.value.filter { meshId ->
            val mac = macByMeshId[meshId] ?: return@filter true
            if ((activeLinkCounts[mac]?.get() ?: 0) > 0) return@filter false
            val lastSeen = scanner.getLastSeenFor(mac)
            lastSeen == null || now - lastSeen > SCAN_EXPIRY_MS
        }.toList()
        if (stalePeerIds.isEmpty()) return
        _onlinePeers.update { it - stalePeerIds.toSet() }
        stalePeerIds.forEach { meshId ->
            // Drop the alias from the registry so a re-discovery with a fresh synthetic
            // ble_* id starts clean and does not collide with a dead meshId.
            macByMeshId.remove(meshId)
            Logger.w("BLE Scan expiry for $meshId (no advertisement seen for ${SCAN_EXPIRY_MS}ms)", tag = TAG)
            _events.emit(TransportEvent.DeviceLost(meshId))
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
