package com.p2p.meshify.feature.realdevicetesting.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestScenarioFactoryTest {

    @Test
    fun `createDefaults returns all six built-in scenarios`() {
        val defaults = TestScenarioFactory.createDefaults()

        assertEquals(6, defaults.size)
        val ids = defaults.map { it.id }
        assertTrue(ids.contains("discovery"))
        assertTrue(ids.contains("ping"))
        assertTrue(ids.contains("message"))
        assertTrue(ids.contains("file"))
        assertTrue(ids.contains("latency"))
        assertTrue(ids.contains("roundtrip"))
    }

    @Test
    fun `all default scenarios are enabled by default`() {
        val defaults = TestScenarioFactory.createDefaults()

        assertTrue(defaults.all { it.enabled })
    }
}
