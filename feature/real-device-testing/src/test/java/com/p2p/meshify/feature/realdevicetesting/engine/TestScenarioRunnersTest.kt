package com.p2p.meshify.feature.realdevicetesting.engine

import android.util.Log
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.feature.realdevicetesting.adapter.TestSendResult
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestScenarioRunnersTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `DiscoveryTestRunner passes when target found`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        val transport = fakeTransport(peers = listOf(peer), success = true)

        val result = DiscoveryTestRunner().run(peer, transport)

        assertEquals("discovery", result.scenarioId)
        assertEquals(TestStatus.PASSED, result.status)
        assertTrue(result.details!!.contains("target verified"))
    }

    @Test
    fun `DiscoveryTestRunner fails when timeout returns empty list`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        val transport = fakeTransport(peers = emptyList(), success = true)

        val result = DiscoveryTestRunner().run(peer, transport)

        assertEquals(TestStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("No devices discovered"))
    }

    @Test
    fun `PingTestRunner passes on successful send`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        val transport = fakeTransport(success = true)

        val result = PingTestRunner().run(peer, transport)

        assertEquals(TestStatus.PASSED, result.status)
        assertTrue(result.details!!.contains("RTT:"))
    }

    @Test
    fun `MessageTestRunner delegates to chatRepository`() = runTest(dispatcher) {
        val repo = mockk<com.p2p.meshify.domain.repository.IChatRepository>(relaxed = true)
        coEvery { repo.sendMessage(any(), any(), any<String>()) } returns Result.success(Unit)
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        val transport = fakeTransport(success = true)
        val runner = MessageTestRunner(repo)

        val result = runner.run(peer, transport)

        assertEquals(TestStatus.PASSED, result.status)
        coVerify(exactly = 1) { repo.sendMessage("test_target_p1", "Device", any<String>()) }
    }

    @Test
    fun `LatencyTestRunner aggregates partial failures`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        var call = 0
        val base = fakeTransport(success = true)
        val transport = object : TransportTestAdapter by base {
            override suspend fun sendTestPayload(peerId: String, payloadType: Payload.PayloadType, testData: ByteArray) = when (call++) {
                0 -> TestSendResult.success(10, 4)
                1 -> TestSendResult.failure("timeout", 10, 0)
                else -> TestSendResult.success(10, 4)
            }
        }

        val result = LatencyTestRunner().run(peer, transport)

        assertEquals(TestStatus.PASSED, result.status)
        assertTrue(result.details!!.contains("1 failed"))
    }

    @Test
    fun `RoundTripTestRunner reports send outcome`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p1", name = "Device", address = "a", transportType = TransportType.LAN)
        val transport = fakeTransport(success = false, error = "send failed")

        val result = RoundTripTestRunner().run(peer, transport)

        assertEquals(TestStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("send failed"))
    }

    private fun fakeTransport(
        peers: List<DiscoveredPeer> = emptyList(),
        success: Boolean,
        error: String? = null
    ): TransportTestAdapter = mockk(relaxed = true) {
        every { transportType } returns TransportType.LAN
        coEvery { this@mockk.discoverPeers(any()) } returns peers
        coEvery { this@mockk.sendTestPayload(any(), any(), any()) } returns TestSendResult(success, 10, 4, error)
    }
}
