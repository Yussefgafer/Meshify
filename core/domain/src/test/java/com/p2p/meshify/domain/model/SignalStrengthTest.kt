package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SignalStrength enum.
 */
class SignalStrengthTest {

    @Test
    fun `fromRssi returns STRONG for rssi greater than -50`() {
        assertEquals(SignalStrength.STRONG, SignalStrength.fromRssi(-40))
        assertEquals(SignalStrength.STRONG, SignalStrength.fromRssi(-45))
        assertEquals(SignalStrength.STRONG, SignalStrength.fromRssi(-49))
    }

    @Test
    fun `fromRssi returns MEDIUM for rssi between -70 and -50`() {
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-50))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-55))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-60))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-65))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.fromRssi(-70))
    }

    @Test
    fun `fromRssi returns WEAK for rssi less than -70`() {
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-71))
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-75))
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-80))
        assertEquals(SignalStrength.WEAK, SignalStrength.fromRssi(-90))
    }

    @Test
    fun `SignalStrength enum values are correct`() {
        assertEquals(4, SignalStrength.values().size)
        assertEquals(SignalStrength.STRONG, SignalStrength.valueOf("STRONG"))
        assertEquals(SignalStrength.MEDIUM, SignalStrength.valueOf("MEDIUM"))
        assertEquals(SignalStrength.WEAK, SignalStrength.valueOf("WEAK"))
        assertEquals(SignalStrength.OFFLINE, SignalStrength.valueOf("OFFLINE"))
    }
}
