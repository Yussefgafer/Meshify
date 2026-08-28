package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SignalStrengthTest {

    @Test
    fun fromRssi_strongAboveMinus50() {
        assertEquals(SignalStrength.STRONG, SignalStrength.fromRssi(-49))
    }

    @Test
    fun fromRssi_mediumBetweenMinus70AndMinus50() {
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-50)) // exclusive STRONG boundary
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-60))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-70)) // inclusive WEAK boundary
    }

    @Test
    fun fromRssi_weakBelowMinus70() {
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-71))
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-100))
    }

    @Test
    fun fromRssi_nonPhysicalRssiIsOffline() {
        // A non-physical RSSI (>= 0) signals "no usable reading" and maps to OFFLINE,
        // so the OFFLINE branch is reachable for disconnected / unmeasured peers.
        assertEquals(SignalStrength.OFFLINE, SignalStrength.fromRssi(0))
        assertEquals(SignalStrength.OFFLINE, SignalStrength.fromRssi(Int.MAX_VALUE))
    }
}
