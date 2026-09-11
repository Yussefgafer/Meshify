package com.p2p.meshify.feature.realdevicetesting.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TestEngineConfigTest {

    @Test
    fun `testPeerIdFor prefixes device id consistently`() {
        assertEquals("test_target_abc", TestEngineConfig.testPeerIdFor("abc"))
        assertEquals("test_target_123-xyz", TestEngineConfig.testPeerIdFor("123-xyz"))
    }
}
