package com.p2p.meshify.core.network.ble

import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    @Volatile
    private var isScanning = false

    // Stable discovery flow — a single SharedFlow created once; the public surface
    // (discoveryFlow) is never reassigned, so a collector that subscribed at construction
    // keeps receiving after the scanner restarts (a Channel-based flow silently died here).
    private val _discoveryFlow = MutableSharedFlow<BleDiscoveredDevice>(extraBufferCapacity = 64)
    val discoveryFlow: Flow<BleDiscoveredDevice> = _discoveryFlow.asSharedFlow()

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

        // Discovery flow is a stable SharedFlow created once; nothing to recreate here.

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
    @SuppressLint("MissingPermission")
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
    // Last advertised device name per MAC — kept past debounce so the transport can
    // surface it when a synthetic ble_* id gets re-keyed to the real mesh UUID.
    private val namesByAddress = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Last advertised RSSI per MAC — mirrors namesByAddress so the re-keyed discovery
    // event can carry the signal strength the user already saw.
    private val rssiByAddress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val debouncingIntervalMs = 10_000L // Only report each device once per 10 seconds

    /**
     * Handle a single scan result.
     */
    private fun handleScanResult(result: ScanResult) {
        // Debounce: skip if we saw this device recently
        val address = result.device.address.uppercase()
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
        namesByAddress[address] = deviceName
        val rssi = result.rssi
        rssiByAddress[address] = rssi

        Logger.d("BLE Discovered: $peerId ($deviceName) RSSI: $rssi", tag = TAG)

        _discoveryFlow.tryEmit(
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
        seenDevices.clear()
        namesByAddress.clear()
        rssiByAddress.clear()
    }

    /**
     * Timestamp (System.currentTimeMillis) of the last scan result for [address],
     * or null when the address has never been seen in this scan session.
     * Used by BleTransportImpl to detect scan expiry — peers that disappear
     * from advertising without firing an explicit lose callback.
     */
    fun getLastSeenFor(address: String): Long? = seenDevices[address]

    /**
     * Returns the most recently advertised device name for [address], or null if we
     * never observed that MAC or it was cleared by [cleanup]. Used by the transport
     * when a synthetic ble_* id is re-keyed to the real mesh UUID so the real
     * discovery event can carry the name the user sees.
     */
    fun getLastNameFor(address: String): String? = namesByAddress[address]

    /**
     * Returns the most recently advertised RSSI for [address], or null if we never
     * observed that MAC or it was cleared by [cleanup]. Mirrors [getLastNameFor] so the
     * re-keyed discovery event preserves the signal strength the user already saw.
     */
    fun getLastRssiFor(address: String): Int? = rssiByAddress[address]
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
