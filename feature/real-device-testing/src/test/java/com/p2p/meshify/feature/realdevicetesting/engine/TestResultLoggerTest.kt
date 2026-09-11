package com.p2p.meshify.feature.realdevicetesting.engine

import android.content.Context
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestResultLoggerTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `session lifecycle builds log text`() = runTest(dispatcher) {
        val context = fakeContext()
        val logger = TestResultLogger(context)
        val peer = DiscoveredPeer(id = "p", name = "D", address = "a", transportType = TransportType.LAN)

        logger.startSession(peer, "lan")
        logger.appendResult(TestResult.pass("discovery", 10, "ok"))
        logger.appendSummary(listOf(TestResult.pass("discovery", 10, "ok")), 10)

        val text = logger.getLogText()
        assertTrue(text.contains("Target: D (a)"))
        assertTrue(text.contains("Transport: LAN"))
        assertTrue(text.contains("DiscoveryTest: PASSED"))
        assertTrue(text.contains("=== Summary ==="))
    }

    @Test
    fun `appendResult before startSession auto-generates minimal header`() = runTest(dispatcher) {
        val context = fakeContext()
        val logger = TestResultLogger(context)

        logger.appendResult(TestResult.fail("ping", "err"))

        val text = logger.getLogText()
        assertTrue(text.contains("PingTest: FAILED"))
        assertTrue(text.contains("Error: err"))
    }

    @Test
    fun `exportLogFile fails when no entries`() = runTest(dispatcher) {
        val context = fakeContext()
        val logger = TestResultLogger(context)

        val result = logger.exportLogFile()

        assertTrue(result.isFailure)
    }

    @Test
    fun `clear resets state`() = runTest(dispatcher) {
        val context = fakeContext()
        val logger = TestResultLogger(context)

        logger.startSession(DiscoveredPeer(id = "p", name = "D", address = "a", transportType = TransportType.LAN), "lan")
        logger.appendResult(TestResult.pass("x", 1))
        logger.clear()

        assertEquals("", logger.getLogText())
    }

    private fun fakeContext(): Context = mockk(relaxed = true)
}
