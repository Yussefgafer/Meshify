package com.p2p.meshify.core.network.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "BleGattClient"

/**
 * BLE GATT Client using native Android BLE API.
 * No external dependencies.
 */
class BleGattClient(
    private val context: Context,
    private val onPayloadReceived: (String, ByteArray) -> Unit,
    private val onConnectionStateChanged: (String, Boolean) -> Unit
) {
    private val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
    private val rxCharUuid = UUID.fromString(AppConfig.BLE_RX_CHAR_UUID)
    private val txCharUuid = UUID.fromString(AppConfig.BLE_TX_CHAR_UUID)

    // Active GATT connections
    private val gattConnections = ConcurrentHashMap<String, BleGattConnection>()

    /**
     * Connect to a remote peer's GATT Server.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, peerId: String) {
        gattConnections[peerId]?.let { existing ->
            if (existing.isConnected) {
                Logger.d("BLE Already connected to $peerId", tag = TAG)
                return
            }
            Logger.d("BLE Stale connection entry for $peerId, reconnecting", tag = TAG)
            existing.failAndRelease("Superseded by reconnect")
        }

        val connection = BleGattConnection(
            peerId = peerId,
            device = device,
            onPayloadReceived = onPayloadReceived,
            onConnectionStateChanged = onConnectionStateChanged,
            removeFromRegistry = { conn -> gattConnections.remove(peerId, conn) }
        )

        try {
            gattConnections[peerId] = connection
            connection.connect(context)
            Logger.d("BLE Connecting to $peerId...", tag = TAG)
        } catch (e: SecurityException) {
            Logger.e("BLE Connect to $peerId: SecurityException - missing BLUETOOTH_CONNECT permission", tag = TAG)
            connection.failAndRelease("SecurityException during connect")
            gattConnections.remove(peerId, connection)
            throw e
        }
    }

    /**
     * Send data to a connected peer.
     */
    suspend fun sendData(peerId: String, data: ByteArray): Result<Unit> {
        val connection = gattConnections[peerId]
        if (connection == null) {
            Logger.e("BLE Not connected to $peerId", tag = TAG)
            return Result.failure(IllegalStateException("Not connected to $peerId"))
        }

        return connection.sendData(data)
    }

    /**
     * Check if connected to a peer.
     */
    fun isConnected(peerId: String): Boolean {
        return gattConnections[peerId]?.isConnected == true
    }

    /**
     * Wait until the peer's GATT readiness deferred completes (MTU negotiation + CCCD write).
     * Returns false on timeout or readiness failure. Abandoning the wait never completes or
     * cancels the shared deferred — the binder callback can still complete it afterwards.
     */
    suspend fun awaitReady(peerId: String, timeoutMs: Long = AppConfig.BLE_READY_TIMEOUT_MS): Boolean {
        val connection = gattConnections[peerId] ?: return false
        return try {
            withTimeout(timeoutMs) {
                connection.awaitReadiness()
                true
            }
        } catch (e: TimeoutCancellationException) {
            Logger.d("BLE Readiness wait timed out for $peerId", tag = TAG)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("BLE Readiness failed for $peerId: ${e.message}", tag = TAG)
            false
        }
    }

    /**
     * Negotiated MTU for a connected peer, or null when there is no active connection.
     * Falls back to the minimum MTU until negotiation completes.
     * Callers must awaitReady(peerId) == true first.
     */
    fun getNegotiatedMtu(peerId: String): Int? {
        val connection = gattConnections[peerId] ?: return null
        return if (connection.isConnected) connection.negotiatedMtu else null
    }

    /**
     * Get all connected peer IDs.
     */
    fun getConnectedPeers(): Set<String> {
        return gattConnections.filter { it.value.isConnected }.keys.toSet()
    }

    /**
     * Clean up all connections.
     */
    fun cleanup() {
        gattConnections.values.forEach { it.failAndRelease("Transport cleanup") }
        gattConnections.clear()
        Logger.d("BLE All client connections cleaned up", tag = TAG)
    }
}

/**
 * Wrapper for a single BLE GATT connection.
 */
class BleGattConnection(
    val peerId: String,
    val device: BluetoothDevice,
    private val onPayloadReceived: (String, ByteArray) -> Unit,
    private val onConnectionStateChanged: (String, Boolean) -> Unit,
    private val removeFromRegistry: (BleGattConnection) -> Unit
) {
    var gatt: BluetoothGatt? = null
        internal set
    var isConnected: Boolean = false
        private set

    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var effectiveMtu: Int = AppConfig.BLE_DEFAULT_MTU // min(negotiatedMtu, AppConfig.BLE_MTU_SIZE)

    val negotiatedMtu: Int
        get() = effectiveMtu

    private var characteristicsReady = CompletableDeferred<Unit>()
    private var released = false

    // Epoch-bound outcome slot for the in-flight acknowledged write; the epoch lets
    // onCharacteristicWrite refuse completions from an abandoned write's stale callback.
    private class PendingWrite(val epoch: Int, val deferred: CompletableDeferred<Int>)

    @Volatile
    private var pendingWrite: PendingWrite? = null
    private val writeEpochCounter = AtomicInteger(0)

    suspend fun awaitReadiness() {
        characteristicsReady.await()
    }

    /**
     * Terminal-path teardown: fails pending readiness waiters immediately, releases the
     * native GATT object and deregisters the connection. Idempotent — safe to invoke from
     * both binder callbacks and client-initiated disconnect. CompletableDeferred ignores
     * a second completion, so late callbacks after release are harmless no-ops.
     */
    @SuppressLint("MissingPermission")
    internal fun failAndRelease(cause: String) {
        synchronized(this) {
            if (released) return
            released = true
        }
        isConnected = false
        characteristicsReady.completeExceptionally(IllegalStateException(cause))
        Logger.e("BLE Connection $peerId terminated: $cause", tag = TAG)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        removeFromRegistry(this)
    }

    private val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
    private val rxCharUuid = UUID.fromString(AppConfig.BLE_RX_CHAR_UUID)
    private val txCharUuid = UUID.fromString(AppConfig.BLE_TX_CHAR_UUID)
    private val cccdUuid = UUID.fromString(AppConfig.BLE_CCCD_UUID)

    fun connect(context: Context) {
        // Reset readiness for new connection
        val oldDeferred = characteristicsReady
        if (!oldDeferred.isCompleted) {
            oldDeferred.completeExceptionally(IllegalStateException("Connection reset"))
        }
        characteristicsReady = CompletableDeferred()
        
        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        isConnected = true
                        onConnectionStateChanged(peerId, true)
                        Logger.d("BLE Connected to $peerId", tag = TAG)
                        gatt.discoverServices()
                    }
                    else -> {
                        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            isConnected = false
                            onConnectionStateChanged(peerId, false)
                            Logger.d("BLE Disconnected from $peerId", tag = TAG)
                        }
                        // Link loss surfaces as STATE_DISCONNECTED carrying an error status;
                        // either condition is terminal for this connection object
                        if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                            failAndRelease(if (status != BluetoothGatt.GATT_SUCCESS) "GATT error $status" else "Disconnected")
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(serviceUuid)
                    rxCharacteristic = service?.getCharacteristic(rxCharUuid)
                    txCharacteristic = service?.getCharacteristic(txCharUuid)

                    if (rxCharacteristic != null && txCharacteristic != null) {
                        // A rejected request means onMtuChanged never fires — terminal
                        if (!gatt.requestMtu(AppConfig.BLE_MTU_SIZE)) {
                            failAndRelease("requestMtu rejected for MTU ${AppConfig.BLE_MTU_SIZE}")
                        }
                    } else {
                        failAndRelease("Required characteristics not found")
                    }
                } else {
                    failAndRelease("Service discovery failed: $status")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    effectiveMtu = minOf(mtu, AppConfig.BLE_MTU_SIZE)
                    Logger.d("BLE MTU negotiated: $mtu (effective: $effectiveMtu) for $peerId", tag = TAG)
                } else {
                    // Keep default 23; effectiveMtu stays at 23
                    Logger.w("BLE MTU negotiation failed: $status for $peerId, using default $effectiveMtu", tag = TAG)
                }

                // Enable notifications on TX characteristic
                txCharacteristic?.let { txChar ->
                    val notifyEnabled = gatt.setCharacteristicNotification(txChar, true)
                    if (!notifyEnabled) {
                        failAndRelease("Notification enable failed")
                        return
                    }

                    val cccd = txChar.getDescriptor(cccdUuid)
                    if (cccd == null) {
                        failAndRelease("CCCD not found")
                        return
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val descResult = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        if (descResult != BluetoothStatusCodes.SUCCESS) {
                            failAndRelease("CCCD write failed: $descResult")
                            return
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        val descSuccess = gatt.writeDescriptor(cccd)
                        if (!descSuccess) {
                            failAndRelease("CCCD write failed (Legacy)")
                            return
                        }
                    }
                    // Acknowledged writes require WRITE_TYPE_DEFAULT; the remote ATT
                    // layer rejects default writes unless PROPERTY_WRITE is declared
                    rxCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
            }

            @SuppressLint("MissingPermission")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid != cccdUuid || gatt !== this@BleGattConnection.gatt) return

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Logger.d("BLE CCCD write confirmed for $peerId", tag = TAG)
                    characteristicsReady.complete(Unit)
                } else {
                    failAndRelease("CCCD write failed: $status")
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (characteristic.uuid != rxCharUuid) return
                val pw = pendingWrite
                // Epoch guard: a completion belonging to a timeout-abandoned write must not satisfy the current one
                if (pw != null && pw.epoch == writeEpochCounter.get()) pw.deferred.complete(status)
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == txCharUuid) {
                    @Suppress("DEPRECATION")
                    val data = characteristic.value ?: return
                    Logger.d("BLE Received ${data.size} bytes from $peerId", tag = TAG)
                    onPayloadReceived(peerId, data)
                }
            }
        }

        // Connect to device
        @SuppressLint("MissingPermission")
        gatt = device.connectGatt(context, false, callback)
    }

    /**
     * Send data to the remote peer.
     * Waits for characteristics to be ready before sending.
     * Data is already chunked by BlePayloadSerializer; send as-is with version checks.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendData(data: ByteArray): Result<Unit> {
        // Wait for service discovery + MTU negotiation + notification setup to complete
        try {
            characteristicsReady.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("BLE Readiness failed for $peerId: ${e.message}", tag = TAG)
            return Result.failure(IllegalStateException(e.message ?: "Characteristics not ready"))
        }

        val rxChar = rxCharacteristic
        val gatt = this.gatt

        if (rxChar == null || gatt == null) {
            Logger.e("BLE RX or GATT not available for $peerId", tag = TAG)
            return Result.failure(IllegalStateException("Characteristics not ready"))
        }

        return try {
            // Data is already chunked by BlePayloadSerializer. Safety check: ensure data fits
            // within the effective MTU (negotiated MTU when available, otherwise default 23).
            // The ATT protocol uses 3 bytes for headers, so the actual payload per packet is MTU - 3.
            val maxPayloadSize = effectiveMtu - 3
            if (data.size > maxPayloadSize) {
                Logger.e("BLE Payload size (${data.size}) exceeds effective MTU payload capacity ($maxPayloadSize) for $peerId. " +
                    "MTU negotiated: $effectiveMtu. Chunk size mismatch — BlePayloadSerializer must be aligned with negotiated MTU.",
                    tag = TAG)
                return Result.failure(IllegalStateException(
                    "Chunk size ${data.size} exceeds MTU payload limit $maxPayloadSize (negotiated MTU: $effectiveMtu)"
                ))
            }

            // Strictly sequential acknowledged write: initiate, then suspend until
            // onCharacteristicWrite reports the link-layer outcome. The transport's
            // outer BLE_SEND_TIMEOUT_MS bounds this await; no local timeout is added.
            val epoch = writeEpochCounter.incrementAndGet()
            val writeOutcome = CompletableDeferred<Int>()
            val pendingSlot = PendingWrite(epoch, writeOutcome)
            pendingWrite = pendingSlot
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val statusCode = gatt.writeCharacteristic(rxChar, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    if (statusCode != BluetoothStatusCodes.SUCCESS) {
                        return Result.failure(Exception("Chunk write to $peerId not initiated: status $statusCode"))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    rxChar.value = data
                    @Suppress("DEPRECATION")
                    val initiated = gatt.writeCharacteristic(rxChar)
                    if (!initiated) {
                        return Result.failure(Exception("Chunk write to $peerId not initiated (legacy stack)"))
                    }
                }

                val gattStatus = try {
                    writeOutcome.await()
                } catch (ce: CancellationException) {
                    // Abandoned acknowledged write leaves ATT link state unknown; teardown before propagating
                    if (!writeOutcome.isCompleted) failAndRelease("Write abandoned mid-flight")
                    throw ce
                }
                if (gattStatus != BluetoothGatt.GATT_SUCCESS) {
                    return Result.failure(Exception("Chunk write to $peerId failed: GATT status $gattStatus"))
                }
            } finally {
                if (pendingWrite === pendingSlot) {
                    pendingWrite = null
                }
            }

            Logger.d("BLE Sent ${data.size} bytes to $peerId", tag = TAG)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("BLE Exception sending data to $peerId: ${e.message}", e, tag = TAG)
            Result.failure(e)
        }
    }
}
