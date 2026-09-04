package com.p2p.meshify.domain.model

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
    OFFLINE;

    companion object {
        fun fromRssi(rssi: Int): SignalStrength {
            return when {
                rssi >= 0 -> OFFLINE // non-physical / no usable signal reading
                rssi > -50 -> STRONG
                rssi >= -70 -> MEDIUM
                else -> WEAK
            }
        }
    }
}
