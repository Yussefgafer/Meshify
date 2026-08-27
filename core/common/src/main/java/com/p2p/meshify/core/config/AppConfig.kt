package com.p2p.meshify.core.config

/**
 * Centralized configuration for the Meshify protocol and application.
 * Hardcoding is strictly forbidden; all magic numbers live here.
 */
object AppConfig {
    // LAN Transport (mDNS / NSD)
    const val SERVICE_TYPE = "_meshify._tcp"
    const val DEFAULT_PORT = 8888

    // BLE Transport
    const val BLE_SERVICE_UUID: String = "00001234-0000-1000-8000-00805f9b34fb"
    const val BLE_RX_CHAR_UUID: String = "00001235-0000-1000-8000-00805f9b34fb"
    const val BLE_TX_CHAR_UUID: String = "00001236-0000-1000-8000-00805f9b34fb"
    const val BLE_CCCD_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
    // Requested MTU for GATT negotiation only — the actual negotiated MTU may be lower
    // and must always be read from BleGattClient.getNegotiatedMtu() before sizing chunks.
    const val BLE_MTU_SIZE: Int = 512
    const val BLE_DEFAULT_MTU: Int = 23 // Minimum BLE MTU per spec, used until negotiation completes
    const val BLE_ATT_OVERHEAD_BYTES: Int = 3 // ATT header: usable payload per packet = MTU - 3
    const val BLE_MAX_CONNECTIONS: Int = 7
    const val BLE_READY_TIMEOUT_MS: Long = 5_000L
    // Sliding-window gap between consecutive chunks: a transfer is dropped only if this long
    // elapses with no new chunk arriving. Generous enough to survive BLE congestion/retransmits
    // without stranding a transfer on a single delayed chunk.
    const val BLE_REASSEMBLY_TIMEOUT_MS: Long = 30_000L
    const val BLE_SEND_TIMEOUT_MS: Long = 15_000L

    // Connection Management
    const val SOCKET_TIMEOUT_MS = 15_000
    const val DISCOVERY_SCAN_INTERVAL_MS = 60_000L // Increased from 30s to 60s to reduce frequent restarts

    // Buffer & Payload Limits
    const val MAX_PAYLOAD_SIZE_BYTES = 10 * 1024 * 1024 // 10MB limit for safety
    const val DEFAULT_BUFFER_SIZE = 32768 // Increased from 8KB to 32KB for better throughput
}
