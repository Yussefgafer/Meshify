package com.p2p.meshify.feature.realdevicetesting.engine

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private const val TAG = "TestEngine"

/**
 * Callback invoked after each test result is computed.
 */
typealias OnTestResultUpdate = (TestResult) -> Unit

/**
 * Core test engine that orchestrates all 6 test types against a real peer device.
 *
 * The engine delegates to individual [TestScenarioRunner] implementations:
 * - **Discovery**: [DiscoveryTestRunner] — transport scan
 * - **Ping**: [PingTestRunner] — RTT measurement
 * - **Message**: [MessageTestRunner] — full DB → Crypto → Network pipeline
 * - **File**: [FileTestRunner] — file transfer with progress
 * - **Latency**: [LatencyTestRunner] — statistical analysis (avg/min/max/p95)
 * - **RoundTrip**: [RoundTripTestRunner] — echo payload send
 *
 * Thread safety: All methods are suspend functions. The engine itself is stateless.
 *
 * Cancellation: Each test checks `coroutineContext.isActive` before execution.
 * A cancelled run leaves partial results in the callback but no leaked resources.
 *
 * @param chatRepository Repository for message/file operations.
 * @param database Room database for test data cleanup.
 * @param logger Plain-text log writer for result recording.
 * @param dataCleaner Cleans up test data from Room DB after each test.
 */
class TestEngine(
    private val chatRepository: IChatRepository,
    private val database: MeshifyDatabase,
    private val logger: TestResultLogger,
    private val dataCleaner: TestDataCleaner
) {
    private val runners = mapOf(
        "discovery" to DiscoveryTestRunner(),
        "ping" to PingTestRunner(),
        "message" to MessageTestRunner(chatRepository),
        "file" to FileTestRunner(chatRepository),
        "latency" to LatencyTestRunner(),
        "roundtrip" to RoundTripTestRunner()
    )

    /**
     * Runs the selected test scenarios against the target peer.
     *
     * Tests execute sequentially in the order they appear in [scenarios].
     * Each test result is emitted via [onResultUpdate] immediately upon completion.
     *
     * @param scenarios The test scenarios to execute.
     * @param targetPeer The peer device to test against.
     * @param transport The transport adapter to use for network operations.
     * @param onResultUpdate Callback invoked after each test completes.
     */
    suspend fun runTests(
        scenarios: List<TestScenario>,
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter,
        onResultUpdate: OnTestResultUpdate
    ) {
        val sessionStart = System.currentTimeMillis()
        val results = mutableListOf<TestResult>()

        logger.startSession(targetPeer, transport.transportType.name)
        Logger.i(TAG, "Starting test session: ${scenarios.size} tests, target=${targetPeer.name}")

        for (scenario in scenarios) {
            coroutineContext.ensureActive()

            val runner = runners[scenario.id]
            val result = if (runner != null) {
                runner.run(targetPeer, transport)
            } else {
                TestResult.fail(scenario.id, "Unknown test scenario: ${scenario.id}")
            }

            results.add(result)
            logger.appendResult(result)
            onResultUpdate(result)

            if (scenario.id in listOf("message", "file", "latency", "roundtrip")) {
                dataCleaner.cleanup(targetPeer.id).onFailure { e ->
                    Logger.e("Failed to cleanup after ${scenario.id}", e, tag = TAG)
                }
            }
        }

        val totalDuration = System.currentTimeMillis() - sessionStart
        logger.appendSummary(results, totalDuration)
        Logger.i(TAG, "Test session complete: ${totalDuration}ms")
    }
}
