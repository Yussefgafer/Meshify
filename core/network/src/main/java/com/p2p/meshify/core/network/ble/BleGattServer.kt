package com.p2p.meshify.core.network.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await

private const val TAG = "BleGattServer"

/**
 * BLE GATT Server Manager using Android's native BluetoothGattServer.
 * 
 * Manages the GATT Server that allows other peers to connect
 * and send/receive data via characteristics.
 */
class BleGattServer(
    private val context: Context,
    private val onPayloadReceived: (String, ByteArray) -> Unit,
    private val onClientConnected: (String) -> Unit,
    private val onClientDisconnected: (String) -> Unit
) {
    private val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
    private val rxCharUuid = UUID.fromString(AppConfig.BLE_RX_CHAR_UUID)
    private val txCharUuid = UUID.fromString(AppConfig.BLE_TX_CHAR_UUID)
    private val cccdUuid = UUID.fromString(AppConfig.BLE_CCCD_UUID)

    private var gattServer: BluetoothGattServer? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    // Completes once addService has been confirmed by the system, with the success status.
    // Awaiters are decoupled from the binder callback via suspend awaitForServiceAdded().
    private var serviceAdded: java.util.concurrent.CompletableFuture<Boolean> =
        java.util.concurrent.CompletableFuture()

    // Track connected devices: device address -> BluetoothDevice
    // On Android 10+, MAC addresses are randomized per connection — the same physical
    // device may appear under a new `device.address` on reconnect. Without BLE bonding
    // we cannot map a randomized MAC back to a stable peer identity; every server-side
    // entry's lifetime is therefore scoped to the active GATT connection. Entries are
    // cleared in stopServer() and on each disconnect callback. Orphaned entries could
    // only occur on disconnected-but-uncallbacked links; that is an Android BLE
    // limitation we accept (no periodic GC runs against this map).
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    // Track which devices have enabled notifications: device address -> subscribed
    private val subscribedDevices = ConcurrentHashMap<String, Boolean>()
    // Track each client's negotiated MTU: device address -> mtu.
    // Defaults to BLE_DEFAULT_MTU until the client negotiates a larger value;
    // GATT callbacks arrive on binder threads, hence ConcurrentHashMap.
    private val negotiatedMtus = ConcurrentHashMap<String, Int>()

    /**
     * Start the GATT Server.
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        try {
            // Only one GATT server instance per process. A second openGattServer call would
            // either fail or create an orphan that leaks. Require stopServer() before re-init.
            if (gattServer != null) {
                Logger.e("BLE GATT Server already running, refusing double-start", tag = TAG)
                return
            }
            // Refuse cleanly if the adapter is off or absent — openGattServer would otherwise
            // return null and the rest of this method would proceed with a phantom gattServer.
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            if (bluetoothManager?.adapter?.isEnabled != true) {
                Logger.e("BLE GATT Server: Bluetooth adapter unavailable or disabled", tag = TAG)
                throw IllegalStateException("Bluetooth adapter not enabled")
            }
            // Recreate deferred so a restart after stopServer can be awaited again
            if (serviceAdded.isDone) serviceAdded = java.util.concurrent.CompletableFuture()

            gattServer = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
                .openGattServer(context, serverCallback)
            
            // Create service and characteristics
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // RX Characteristic (write from client, read by server)
            val rxChar = BluetoothGattCharacteristic(
                rxCharUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // TX Characteristic (notify from server, read by client)
            txCharacteristic = BluetoothGattCharacteristic(
                txCharUuid,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).also { txChar ->
                val cccd = BluetoothGattDescriptor(
                    cccdUuid,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                txChar.addDescriptor(cccd)
                service.addCharacteristic(txChar)
            }

            service.addCharacteristic(rxChar)
            gattServer?.addService(service)
            
            Logger.d("BLE GATT Server started", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to start GATT Server: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Suspends until the system confirms service addition (or fails the add).
     * Returns true on success, false on a non-success onServiceAdded callback.
     * Bounded by the in-call delay; callers should treat false as a transport start failure.
     */
    suspend fun awaitForServiceAdded(): Boolean {
        val future = serviceAdded
        return try {
            future.await()
        } catch (e: java.util.concurrent.CompletionException) {
            Logger.e("BLE Server service-add await failed: ${e.message}", tag = TAG)
            false
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Stop the GATT Server.
     */
    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            gattServer?.close()
            gattServer = null
            connectedDevices.clear()
            subscribedDevices.clear()
            negotiatedMtus.clear()
            // Fail any pending await so callers don't block forever on a torn-down server
            if (!serviceAdded.isDone) {
                serviceAdded.complete(false)
            }
            Logger.d("BLE GATT Server stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop GATT Server: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Send data to a connected client via notification.
     * Data is already chunked by BlePayloadSerializer; send as-is with version checks.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendData(peerId: String, data: ByteArray): Result<Unit> {
        val device = connectedDevices[peerId]
        val txChar = txCharacteristic

        if (device == null || txChar == null) {
            Logger.e("BLE Cannot send to $peerId: device or TX not available", tag = TAG)
            return Result.failure(IllegalStateException("Device or TX characteristic not available"))
        }

        if (subscribedDevices[peerId] != true) {
            Logger.w("BLE Peer $peerId not subscribed to notifications", tag = TAG)
            return Result.failure(IllegalStateException("Peer not subscribed"))
        }

        val maxNotifyPayloadSize =
            (negotiatedMtus[peerId] ?: AppConfig.BLE_DEFAULT_MTU) - AppConfig.BLE_ATT_OVERHEAD_BYTES
        if (data.size > maxNotifyPayloadSize) {
            Logger.e(
                "BLE Outgoing data (${data.size}B) exceeds $peerId MTU payload capacity ($maxNotifyPayloadSize) - skipping to avoid silent truncation",
                tag = TAG
            )
            return Result.failure(IllegalStateException("Data exceeds peer MTU payload limit $maxNotifyPayloadSize"))
        }

        return try {
            // Data is already chunked by BlePayloadSerializer to fit within BLE MTU.
            // Use modern notifyCharacteristicChanged for API 33+ or legacy fallback.
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusCode = gattServer?.notifyCharacteristicChanged(device, txChar, false, data) ?: -1
                statusCode == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                txChar.value = data
                gattServer?.notifyCharacteristicChanged(device, txChar, false) ?: false
            }

            if (!success) {
                Logger.e("BLE Server notification failed to $peerId", tag = TAG)
                return Result.failure(java.io.IOException("Notification failed"))
            }

            Logger.d("BLE Server sent ${data.size} bytes to $peerId", tag = TAG)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("BLE Server failed to send data to $peerId: ${e.message}", e, tag = TAG)
            Result.failure(e)
        }
    }

    /**
     * Get list of connected clients.
     */
    fun getConnectedClients(): Set<String> {
        return connectedDevices.keys.toSet()
    }

    /**
     * The BluetoothDevice currently connected to our server under [address], if any.
     */
    fun getConnectedDevice(address: String): BluetoothDevice? = connectedDevices[address]

    /**
     * Check if server is running.
     */
    fun isServerRunning(): Boolean = gattServer != null

    /**
     * Clean up resources.
     */
    fun cleanup() {
        stopServer()
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val peerAddress = device.address.uppercase()
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // A repeat connection from a tracked address replaces the entry and resets
                    // its per-link state; entries under other addresses are left untouched
                    if (connectedDevices.containsKey(peerAddress)) {
                        subscribedDevices.remove(peerAddress)
                        negotiatedMtus.remove(peerAddress)
                    }
                    connectedDevices[peerAddress] = device
                    onClientConnected(peerAddress)
                    Logger.d("BLE Client connected: $peerAddress (name: ${device.name ?: "unknown"})", tag = TAG)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(peerAddress)
                    subscribedDevices.remove(peerAddress)
                    negotiatedMtus.remove(peerAddress)
                    onClientDisconnected(peerAddress)
                    Logger.d("BLE Client disconnected: $peerAddress", tag = TAG)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == rxCharUuid) {
                val peerAddress = device.address.uppercase()
                Logger.d("BLE Received ${value.size} bytes from $peerAddress", tag = TAG)
                onPayloadReceived(peerAddress, value)
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == cccdUuid) {
                val peerAddress = device.address.uppercase()
                val subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                subscribedDevices[peerAddress] = subscribed
                Logger.d("BLE Peer $peerAddress ${if (subscribed) "subscribed" else "unsubscribed"} to notifications", tag = TAG)
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val peerAddress = device.address.uppercase()
            if (!connectedDevices.containsKey(peerAddress)) {
                Logger.d("BLE Ignoring late server MTU callback for removed $peerAddress", tag = TAG)
                return
            }
            negotiatedMtus[peerAddress] = maxOf(AppConfig.BLE_DEFAULT_MTU, mtu)
            Logger.d("BLE Server MTU for $peerAddress: ${maxOf(AppConfig.BLE_DEFAULT_MTU, mtu)}", tag = TAG)
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (!serviceAdded.isDone) {
                serviceAdded.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }
}
