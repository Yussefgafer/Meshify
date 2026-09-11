package com.p2p.meshify.feature.realdevicetesting.engine

import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestEngineTest {

    private lateinit var chatRepository: IChatRepository
    private lateinit var database: MeshifyDatabase
    private lateinit var logger: TestResultLogger
    private lateinit var dataCleaner: TestDataCleaner
    private lateinit var engine: TestEngine
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        chatRepository = mockk(relaxed = true)
        database = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        dataCleaner = mockk(relaxed = true)
        coEvery { dataCleaner.cleanup(any()) } returns Result.success(Unit)

        engine = TestEngine(chatRepository, database, logger, dataCleaner)
    }

    @Test
    fun `runTests executes runners sequentially and emits results`() = runTest(dispatcher) {
        val transport = fakeTransport()
        val peer = DiscoveredPeer(id = "peer", name = "Device", address = "1.2.3.4", transportType = TransportType.LAN)
        val scenarios = listOf(fakeScenario("discovery"), fakeScenario("message"))

        val results = mutableListOf<TestResult>()
        engine.runTests(scenarios, peer, transport) { results.add(it) }

        assertEquals(2, results.size)
        assertEquals("discovery", results[0].scenarioId)
        assertEquals("message", results[1].scenarioId)
    }

    @Test
    fun `runTests cleans up after message and file scenarios`() = runTest(dispatcher) {
        val transport = fakeTransport()
        val peer = DiscoveredPeer(id = "peer", name = "Device", address = "1.2.3.4", transportType = TransportType.LAN)
        val scenarios = listOf(fakeScenario("message"), fakeScenario("file"))

        engine.runTests(scenarios, peer, transport) {}

        coVerify(exactly = 2) { dataCleaner.cleanup("peer") }
    }

    @Test
    fun `runTests fails unknown scenario without throwing`() = runTest(dispatcher) {
        val transport = fakeTransport()
        val peer = DiscoveredPeer(id = "peer", name = "Device", address = "1.2.3.4", transportType = TransportType.LAN)
        val scenarios = listOf(fakeScenario("unknown"))

        val results = mutableListOf<TestResult>()
        engine.runTests(scenarios, peer, transport) { results.add(it) }

        assertEquals(1, results.size)
        assertEquals(TestStatus.FAILED, results.first().status)
        assertTrue(results.first().error.orEmpty().contains("Unknown test scenario"))
    }

    private fun fakeTransport(): TransportTestAdapter = mockk(relaxed = true) {
        every { transportType } returns TransportType.LAN
    }

    private fun fakeScenario(id: String): TestScenario = TestScenario(id = id, titleRes = 0, subtitleRes = 0, icon = id)
}
