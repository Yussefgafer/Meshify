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
    fun fromRssi_offlineBranchIsUnreachable() {
        // The three branches (rssi > -50, rssi in -70..-50, rssi < -70) cover every Int value,
        // so `else -> OFFLINE` can never be reached. No input yields OFFLINE.
        for (rssi in listOf(Int.MIN_VALUE, -1000, -71, -70, -50, -49, 0, Int.MAX_VALUE)) {
            assertNotEquals(SignalStrength.OFFLINE, SignalStrength.fromRssi(rssi))
        }
    }
}
