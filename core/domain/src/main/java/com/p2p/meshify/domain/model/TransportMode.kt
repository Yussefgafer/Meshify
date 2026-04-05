package com.p2p.meshify.domain.model

/**
 * Transport mode for message routing.
 * Determines how messages are sent to peers.
 */
enum class TransportMode(val description: String) {
    /** Send via LAN + BLE simultaneously (most reliable) */
    MULTI_PATH("LAN + Bluetooth simultaneously"),

    /** Send via LAN only (current default behavior) */
    LAN_ONLY("Wi-Fi / Ethernet only"),

    /** Send via BLE only (short-range) */
    BLE_ONLY("Short-range Bluetooth only"),

    /** System picks best available transport automatically */
    AUTO("System picks best available")
}
