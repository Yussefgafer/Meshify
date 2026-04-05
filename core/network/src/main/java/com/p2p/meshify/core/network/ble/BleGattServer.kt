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
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    // Track connected devices: device address -> BluetoothDevice
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    // Track which devices have enabled notifications
    private val subscribedDevices = ConcurrentHashMap<String, Boolean>()

    /**
     * Start the GATT Server.
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        try {
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
     * Stop the GATT Server.
     */
    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            gattServer?.close()
            gattServer = null
            connectedDevices.clear()
            subscribedDevices.clear()
            Logger.d("BLE GATT Server stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop GATT Server: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * Send data to a connected client via notification.
     * Returns Result<Unit> indicating success or failure.
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

        return try {
            // Use serializer's max chunk size for consistency
            val maxChunkSize = BlePayloadSerializer.getMaxChunkDataSize()
            val chunks = data.toList().chunked(maxChunkSize)

            for ((index, chunk) in chunks.withIndex()) {
                txChar.value = chunk.toByteArray()
                val success = gattServer?.notifyCharacteristicChanged(device, txChar, false) ?: false
                if (!success) {
                    Logger.e("BLE Server notification failed for chunk $index to $peerId", tag = TAG)
                    return Result.failure(java.io.IOException("Notification failed for chunk $index"))
                }
                // Small delay to avoid BLE congestion
                kotlinx.coroutines.delay(50)
            }

            Logger.d("BLE Server sent ${data.size} bytes to $peerId in ${chunks.size} chunks", tag = TAG)
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
            val peerAddress = device.address
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[peerAddress] = device
                    onClientConnected(peerAddress)
                    Logger.d("BLE Client connected: $peerAddress", tag = TAG)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(peerAddress)
                    subscribedDevices.remove(peerAddress)
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
                val peerAddress = device.address
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
                val peerAddress = device.address
                val subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                subscribedDevices[peerAddress] = subscribed
                Logger.d("BLE Peer $peerAddress ${if (subscribed) "subscribed" else "unsubscribed"} to notifications", tag = TAG)
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }
}
