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
    const val BLE_MTU_SIZE: Int = 512
    const val BLE_ADVERTISING_INTERVAL_MS: Int = 100
    const val BLE_SCAN_INTERVAL_MS: Long = 10_000L
    const val BLE_MAX_CONNECTIONS: Int = 7
    const val BLE_CHUNK_HEADER_SIZE: Int = 12
    const val BLE_REASSEMBLY_TIMEOUT_MS: Long = 5_000L

    // Connection Management
    const val SOCKET_TIMEOUT_MS = 15_000
    const val DISCOVERY_SCAN_INTERVAL_MS = 30_000L

    // Buffer & Payload Limits
    const val MAX_PAYLOAD_SIZE_BYTES = 10 * 1024 * 1024 // 10MB limit for safety
    const val DEFAULT_BUFFER_SIZE = 8192
}
