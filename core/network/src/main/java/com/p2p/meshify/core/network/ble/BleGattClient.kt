package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

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
    fun connect(device: BluetoothDevice, peerId: String) {
        if (gattConnections.containsKey(peerId)) {
            Logger.d("BLE Already connected to $peerId", tag = TAG)
            return
        }

        val connection = BleGattConnection(
            peerId = peerId,
            device = device,
            onPayloadReceived = onPayloadReceived,
            onConnectionStateChanged = onConnectionStateChanged
        )

        gattConnections[peerId] = connection
        connection.connect(context)
        Logger.d("BLE Connecting to $peerId...", tag = TAG)
    }

    /**
     * Disconnect from a peer.
     */
    fun disconnect(peerId: String) {
        gattConnections.remove(peerId)?.let { connection ->
            connection.gatt?.close()
            connection.gatt = null
            Logger.d("BLE Disconnected from $peerId", tag = TAG)
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
     * Get all connected peer IDs.
     */
    fun getConnectedPeers(): Set<String> {
        return gattConnections.filter { it.value.isConnected }.keys.toSet()
    }

    /**
     * Clean up all connections.
     */
    fun cleanup() {
        gattConnections.values.forEach { it.gatt?.close() }
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
    private val onConnectionStateChanged: (String, Boolean) -> Unit
) {
    var gatt: BluetoothGatt? = null
        internal set
    var isConnected: Boolean = false
        private set

    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var currentMtu = AppConfig.BLE_MTU_SIZE
    private var characteristicsReady = CompletableDeferred<Unit>()

    private val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
    private val rxCharUuid = UUID.fromString(AppConfig.BLE_RX_CHAR_UUID)
    private val txCharUuid = UUID.fromString(AppConfig.BLE_TX_CHAR_UUID)

    fun connect(context: Context) {
        // Reset readiness for new connection
        val oldDeferred = characteristicsReady
        if (!oldDeferred.isCompleted) {
            oldDeferred.completeExceptionally(IllegalStateException("Connection reset"))
        }
        characteristicsReady = CompletableDeferred()
        
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        onConnectionStateChanged(peerId, true)
                        Logger.d("BLE Connected to $peerId", tag = TAG)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        onConnectionStateChanged(peerId, false)
                        Logger.d("BLE Disconnected from $peerId", tag = TAG)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(serviceUuid)
                    rxCharacteristic = service?.getCharacteristic(rxCharUuid)
                    txCharacteristic = service?.getCharacteristic(txCharUuid)

                    if (rxCharacteristic != null && txCharacteristic != null) {
                        // Request MTU
                        gatt.requestMtu(AppConfig.BLE_MTU_SIZE)
                    } else {
                        Logger.e("BLE Required characteristics not found", tag = TAG)
                    }
                } else {
                    Logger.e("BLE Service discovery failed: $status", tag = TAG)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentMtu = mtu
                    Logger.d("BLE MTU negotiated: $mtu for $peerId", tag = TAG)
                }

                // Enable notifications on TX characteristic
                txCharacteristic?.let { txChar ->
                    gatt.setCharacteristicNotification(txChar, true)
                    val cccd = txChar.getDescriptor(UUID.fromString(AppConfig.BLE_CCCD_UUID))
                    cccd?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }
                
                // Signal that characteristics are ready for sending
                if (!characteristicsReady.isCompleted) {
                    characteristicsReady.complete(Unit)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == txCharUuid) {
                    val data = characteristic.value ?: return
                    Logger.d("BLE Received ${data.size} bytes from $peerId", tag = TAG)
                    onPayloadReceived(peerId, data)
                }
            }
        }

        // Connect to device
        gatt = device.connectGatt(context, false, callback)
    }

    /**
     * Send data to the remote peer.
     * Waits for characteristics to be ready before sending.
     */
    suspend fun sendData(data: ByteArray): Result<Unit> {
        // Wait for service discovery + MTU negotiation + notification setup to complete
        try {
            characteristicsReady.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("BLE Characteristics readiness timeout for $peerId", tag = TAG)
            return Result.failure(IllegalStateException("Characteristics not ready"))
        }

        val rxChar = rxCharacteristic
        val gatt = this.gatt

        if (rxChar == null || gatt == null) {
            Logger.e("BLE RX or GATT not available for $peerId", tag = TAG)
            return Result.failure(IllegalStateException("Characteristics not ready"))
        }

        return try {
            val maxChunkSize = currentMtu - 3
            val chunks = data.toList().chunked(maxChunkSize)

            for ((index, chunk) in chunks.withIndex()) {
                rxChar.value = chunk.toByteArray()
                val success = gatt.writeCharacteristic(rxChar)
                if (!success) {
                    Logger.e("BLE Write failed for chunk $index", tag = TAG)
                    return Result.failure(Exception("Write failed for chunk $index"))
                }
                // Small delay to avoid BLE congestion
                kotlinx.coroutines.delay(50)
            }

            Logger.d("BLE Sent ${data.size} bytes to $peerId in ${chunks.size} chunks", tag = TAG)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("BLE Exception sending data to $peerId: ${e.message}", e, tag = TAG)
            Result.failure(e)
        }
    }
}
