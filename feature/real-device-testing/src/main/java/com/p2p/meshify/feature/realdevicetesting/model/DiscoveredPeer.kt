package com.p2p.meshify.feature.realdevicetesting.model

import com.p2p.meshify.domain.model.TransportType

/**
 * Represents a peer device discovered during the testing phase.
 * Extends the basic discovery info with test-specific state.
 *
 * @property id The peer's unique identifier.
 * @property name The peer's display name.
 * @property address Network address (IP for LAN, MAC for BLE).
 * @property transportType How this peer was discovered (LAN / BLE).
 * @property rssi Signal strength in dBm (null if unavailable).
 * @property sessionStatus Whether an ECDH session is established with this peer.
 */
data class DiscoveredPeer(
    val id: String,
    val name: String,
    val address: String,
    val transportType: TransportType,
    val rssi: Int? = null,
    val sessionStatus: SessionStatus = SessionStatus.UNKNOWN
) {
    /** Signal quality level — UI layer maps this to localized strings. */
    val signalLevel: SignalLevel
        get() = rssi?.let {
            when {
                it >= -50 -> SignalLevel.EXCELLENT
                it >= -60 -> SignalLevel.GOOD
                it >= -70 -> SignalLevel.FAIR
                else -> SignalLevel.WEAK
            }
        } ?: SignalLevel.UNKNOWN
}

/** Signal quality levels for UI localization. */
enum class SignalLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK,
    UNKNOWN,
}

/** ECDH session state with a discovered peer. */
enum class SessionStatus {
    /** No session attempt yet. */
    UNKNOWN,

    /** Handshake in progress. */
    ESTABLISHING,

    /** Session key established successfully. */
    ESTABLISHED,

    /** Handshake failed or TOFU violation. */
    FAILED,
}
