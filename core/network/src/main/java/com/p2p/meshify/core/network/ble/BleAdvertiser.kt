package com.p2p.meshify.core.network.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "BleAdvertiser"

/**
 * BLE Advertising Manager.
 * 
 * Advertises this device as a Meshify peer using BLE.
 * Encodes peerId in the advertising data for discovery.
 * 
 * Advertising Data Format (max 31 bytes for BLE):
 * [16B: Service UUID] [variable: compressed peerId]
 */
class BleAdvertiser(
    private val peerId: String,
    private val deviceName: String,
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) {
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private var isAdvertising = false
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Logger.d("BLE Advertising started successfully", tag = TAG)
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            val errorName = when (errorCode) {
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                else -> "UNKNOWN($errorCode)"
            }
            Logger.e("BLE Advertising failed: $errorName", tag = TAG)
            isAdvertising = false
        }
    }

    /**
     * Start BLE advertising.
     */
    fun startAdvertising() {
        if (isAdvertising) {
            Logger.d("BLE Already advertising, skipping", tag = TAG)
            return
        }

        if (advertiser == null) {
            Logger.e("BLE Advertiser not available", tag = TAG)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Logger.w("Bluetooth is disabled or adapter unavailable, cannot advertise", tag = TAG)
            return
        }

        val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
        val peerIdBytes = compressPeerId(peerId)

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), peerIdBytes)
            .setIncludeDeviceName(false) // Save space for peerId
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser.startAdvertising(advertiseSettings, advertiseData, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            Logger.e("BLE Advertising: SecurityException - missing BLUETOOTH_ADVERTISE permission", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Advertising: Unexpected error: ${e.message}", tag = TAG)
        }
    }

    /**
     * Stop BLE advertising.
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }

        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Logger.d("BLE Advertising stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop advertising: ${e.message}", tag = TAG)
        }
    }

    /**
     * Check if currently advertising.
     */
    fun isCurrentlyAdvertising(): Boolean = isAdvertising

    /**
     * Compress peerId to fit in BLE advertising data (max 31 bytes total, ~15 bytes for service data).
     * Truncates UUID to 8 bytes if needed.
     */
    private fun compressPeerId(peerId: String): ByteArray {
        val maxDataSize = 15 // Remaining bytes after service UUID (16 bytes + overhead)
        
        return try {
            val uuid = UUID.fromString(peerId)
            val buffer = ByteBuffer.allocate(8)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.array()
        } catch (e: Exception) {
            // If peerId is not a UUID, truncate it
            val bytes = peerId.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size <= maxDataSize) bytes else bytes.copyOf(maxDataSize)
        }
    }
}
