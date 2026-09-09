package com.p2p.meshify.core.network.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportCapabilityTest {

    @Test
    fun allCapabilities_haveNonBlankDescriptions() {
        for (cap in TransportCapability.entries) {
            assertTrue("description empty for $cap", cap.description.isNotBlank())
        }
    }

    @Test
    fun capabilities_roundTripThroughValueOf() {
        for (cap in TransportCapability.entries) {
            assertEquals(cap, TransportCapability.valueOf(cap.name))
        }
    }

    @Test
    fun knownCapabilities_haveExpectedDescriptions() {
        assertEquals("Supports large file transfers", TransportCapability.FILE_TRANSFER.description)
        assertEquals("Works without internet connection", TransportCapability.OFFLINE.description)
        assertEquals("Low power consumption", TransportCapability.LOW_POWER.description)
    }

    @Test
    fun knownCapabilities_behaveCorrectlyInSetOperations() {
        val lanCaps = setOf(
            TransportCapability.FILE_TRANSFER,
            TransportCapability.HIGH_BANDWIDTH,
            TransportCapability.OFFLINE,
            TransportCapability.LOW_LATENCY
        )
        val bleCaps = setOf(
            TransportCapability.LOW_POWER,
            TransportCapability.OFFLINE
        )
        assertTrue(lanCaps.contains(TransportCapability.FILE_TRANSFER))
        assertFalse(bleCaps.contains(TransportCapability.FILE_TRANSFER))
        assertTrue(lanCaps.intersect(bleCaps).contains(TransportCapability.OFFLINE))
    }

    @Test
    fun capabilityIntersection_behavior() {
        val required = setOf(TransportCapability.FILE_TRANSFER)
        val lan = setOf(TransportCapability.FILE_TRANSFER, TransportCapability.OFFLINE)
        val ble = setOf(TransportCapability.LOW_POWER, TransportCapability.OFFLINE)
        assertTrue(lan.intersect(required).isNotEmpty())
        assertTrue(ble.intersect(required).isEmpty())
    }
}
