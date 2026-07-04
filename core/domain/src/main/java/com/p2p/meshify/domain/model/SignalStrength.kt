package com.p2p.meshify.domain.model

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
    OFFLINE;

    companion object {
        fun fromRssi(rssi: Int): SignalStrength {
            return when {
                rssi > -50 -> STRONG
                rssi in -70..-50 -> MEDIUM
                rssi < -70 -> WEAK
                else -> OFFLINE
            }
        }
    }
}
