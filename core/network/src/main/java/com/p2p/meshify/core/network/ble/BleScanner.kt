package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private const val TAG = "BleScanner"

/**
 * BLE Scanner for discovering Meshify peers via advertising.
 * 
 * Scans for devices advertising the Meshify service UUID
 * and emits discovery events with peer details.
 */
class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) {
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var isScanning = false

    // Channel for discovered devices
    private val _discoveryChannel = Channel<BleDiscoveredDevice>(Channel.BUFFERED)
    val discoveryFlow: Flow<BleDiscoveredDevice> = _discoveryChannel.receiveAsFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorName = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                else -> "UNKNOWN($errorCode)"
            }
            Logger.e("BLE Scan failed: $errorName", tag = TAG)
            isScanning = false
        }
    }

    /**
     * Start scanning for Meshify peers.
     */
    fun startScanning() {
        if (isScanning) {
            Logger.d("BLE Already scanning, skipping", tag = TAG)
            return
        }

        if (scanner == null) {
            Logger.e("BLE Scanner not available", tag = TAG)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Logger.w("Bluetooth is disabled or adapter unavailable, cannot scan", tag = TAG)
            return
        }

        val serviceUuid = UUID.fromString(AppConfig.BLE_SERVICE_UUID)
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Immediate reporting for faster discovery
            .build()

        try {
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Logger.d("BLE Scanning started", tag = TAG)
        } catch (e: SecurityException) {
            Logger.e("BLE Scanning: SecurityException - missing BLUETOOTH_SCAN permission", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Scanning: Unexpected error: ${e.message}", tag = TAG)
        }
    }

    /**
     * Stop scanning.
     */
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
            Logger.d("BLE Scanning stopped", tag = TAG)
        } catch (e: Exception) {
            Logger.e("BLE Failed to stop scanning: ${e.message}", tag = TAG)
        }
    }

    /**
     * Check if currently scanning.
     */
    fun isCurrentlyScanning(): Boolean = isScanning

    // Track discovered devices for debouncing
    private val seenDevices = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val debouncingIntervalMs = 10_000L // Only report each device once per 10 seconds

    /**
     * Handle a single scan result.
     */
    private fun handleScanResult(result: ScanResult) {
        // Debounce: skip if we saw this device recently
        val address = result.device.address
        val now = System.currentTimeMillis()
        val lastSeen = seenDevices[address]
        if (lastSeen != null && now - lastSeen < debouncingIntervalMs) {
            return
        }
        seenDevices[address] = now

        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(UUID.fromString(AppConfig.BLE_SERVICE_UUID)))
        if (serviceData == null) {
            return
        }

        val peerId = extractPeerId(serviceData)
        if (peerId == null) {
            Logger.w("Failed to extract peerId from scan result", tag = TAG)
            return
        }

        val deviceName = result.scanRecord?.deviceName ?: "Unknown"
        val rssi = result.rssi

        Logger.d("BLE Discovered: $peerId ($deviceName) RSSI: $rssi", tag = TAG)

        _discoveryChannel.trySend(
            BleDiscoveredDevice(
                peerId = peerId,
                deviceName = deviceName,
                device = result.device,
                rssi = rssi
            )
        )
    }

    /**
     * Extract peerId from advertising service data.
     * Supports both full UUID (UTF-8 encoded) and 8-byte compressed formats.
     * For compressed format, we return the raw hex without prefix - the full
     * peerId resolution happens during GATT connection via identity exchange.
     */
    private fun extractPeerId(serviceData: ByteArray): String? {
        return if (serviceData.size >= 16) {
            // Full UUID as UTF-8 string
            try {
                String(serviceData, java.nio.charset.StandardCharsets.UTF_8)
                    .trim()
                    .takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Logger.e("Failed to decode peerId from advertising data", tag = TAG)
                null
            }
        } else if (serviceData.size == 8) {
            // Compressed UUID (MSB only) — return as hex for matching
            val msb = serviceData.toLong()
            // We store this temporarily; the real peerId comes from the GATT connection
            // where the full identity is exchanged
            "ble_${java.lang.Long.toHexString(msb).padStart(16, '0')}"
        } else {
            Logger.w("Unexpected service data size: ${serviceData.size}", tag = TAG)
            null
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        stopScanning()
        _discoveryChannel.close()
    }
}

/**
 * Represents a discovered BLE device with Meshify service.
 */
data class BleDiscoveredDevice(
    val peerId: String,
    val deviceName: String,
    val device: android.bluetooth.BluetoothDevice,
    val rssi: Int
)

/**
 * Convert ByteArray to Long (little-endian).
 */
private fun ByteArray.toLong(): Long {
    var result = 0L
    for (i in indices) {
        result = result or ((this[i].toLong() and 0xFFL) shl (i * 8))
    }
    return result
}
