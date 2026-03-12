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
    fun `getMorphDuration returns correct values for each strength`() {
        assertEquals(500, SignalStrength.STRONG.getMorphDuration())
        assertEquals(900, SignalStrength.MEDIUM.getMorphDuration())
        assertEquals(1500, SignalStrength.WEAK.getMorphDuration())
        assertEquals(0, SignalStrength.OFFLINE.getMorphDuration())
    }

    @Test
    fun `getShapePair returns correct shapes for STRONG`() {
        val shapes = SignalStrength.STRONG.getShapePair()
        
        assertEquals(2, shapes.size)
        // STRONG should return two different complex shapes
        assertNotEquals(shapes[0], shapes[1])
    }

    @Test
    fun `getShapePair returns correct shapes for MEDIUM`() {
        val shapes = SignalStrength.MEDIUM.getShapePair()
        
        assertEquals(2, shapes.size)
        // MEDIUM should return two different shapes
        assertNotEquals(shapes[0], shapes[1])
    }

    @Test
    fun `getShapePair returns circle for OFFLINE`() {
        val shapes = SignalStrength.OFFLINE.getShapePair()
        
        assertEquals(2, shapes.size)
        // Both should be circles for OFFLINE - check if they're equal (circles are identical)
        assertEquals(shapes[0], shapes[1])
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
