package com.p2p.meshify.core.config

/**
 * Centralized configuration for the Meshify protocol and application.
 * Hardcoding is strictly forbidden; all magic numbers live here.
 */
object AppConfig {
    // LAN Transport (mDNS / NSD)
    const val SERVICE_TYPE = "_meshify._tcp"
    const val DEFAULT_PORT = 8888

    // Connection Management
    const val SOCKET_TIMEOUT_MS = 15_000
    const val DISCOVERY_SCAN_INTERVAL_MS = 30_000L

    // Buffer & Payload Limits
    const val MAX_PAYLOAD_SIZE_BYTES = 10 * 1024 * 1024 // 10MB limit for safety
    const val DEFAULT_BUFFER_SIZE = 8192
}
