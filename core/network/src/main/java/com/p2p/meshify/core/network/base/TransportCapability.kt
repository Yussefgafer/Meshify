package com.p2p.meshify.core.network.base

/**
 * Transport capabilities for feature detection and smart selection.
 * Used by TransportManager to choose the best transport for a given use case.
 */
enum class TransportCapability(
    val description: String
) {
    /** Supports large file transfers (>100MB) */
    FILE_TRANSFER("Supports large file transfers"),

    /** Supports real-time streaming */
    STREAMING("Supports real-time audio/video streaming"),

    /** Low latency (<50ms) - suitable for text messages */
    LOW_LATENCY("Low latency communication (<50ms)"),

    /** Long range (>100m) */
    LONG_RANGE("Long range communication (>100m)"),

    /** High bandwidth (>100Mbps) */
    HIGH_BANDWIDTH("High bandwidth (>100Mbps)"),

    /** Works offline (no internet required) */
    OFFLINE("Works without internet connection"),

    /** Low power consumption - suitable for battery-powered devices */
    LOW_POWER("Low power consumption"),

    /** Supports mesh networking (multi-hop) */
    MESH_NETWORKING("Supports multi-hop mesh routing"),

    /** Supports NAT traversal (works across different networks) */
    NAT_TRAVERSAL("Can traverse NAT/firewalls")
}
