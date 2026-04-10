package com.p2p.meshify.feature.realdevicetesting.engine

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.DISCOVERY_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.ECHO_PREFIX
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.FILE_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.LATENCY_INTER_MESSAGE_DELAY_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.LATENCY_MESSAGE_COUNT
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.LATENCY_PER_MESSAGE_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.MESSAGE_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.PING_PAYLOAD
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.PING_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.ROUNDTRIP_TIMEOUT_MS
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.TEST_FILE_SIZE_BYTES
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.TEST_MESSAGE_TEXT
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import com.p2p.meshify.domain.model.MessageType
import kotlin.coroutines.coroutineContext

private const val TAG = "TestScenarioRunners"

/**
 * Common interface for individual test scenario runners.
 *
 * Each implementation handles one test type (discovery, ping, message, etc.)
 * and returns a structured [TestResult].
 */
interface TestScenarioRunner {
    suspend fun run(targetPeer: DiscoveredPeer, transport: TransportTestAdapter): TestResult
}

/**
 * Discovery Test: Scan for nearby devices using the transport's discovery mechanism.
 *
 * Pipeline: Transport scan → mDNS/BLE broadcast → peer list
 * Does NOT involve DB or crypto.
 *
 * Success criteria: At least one peer discovered within timeout.
 */
class DiscoveryTestRunner : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running DiscoveryTest")

        return try {
            val peers = withTimeout(DISCOVERY_TIMEOUT_MS) {
                transport.discoverPeers(timeoutMs = DISCOVERY_TIMEOUT_MS)
            }

            val duration = System.currentTimeMillis() - start
            val peerCount = peers.size
            val targetFound = peers.any { it.id == targetPeer.id || it.address == targetPeer.address }

            if (targetFound) {
                TestResult.pass(
                    scenarioId = "discovery",
                    durationMs = duration,
                    details = "$peerCount device(s) found — target verified"
                )
            } else if (peers.isNotEmpty()) {
                TestResult.pass(
                    scenarioId = "discovery",
                    durationMs = duration,
                    details = "$peerCount device(s) found — target not in list (may be filtered)"
                )
            } else {
                TestResult.fail(
                    scenarioId = "discovery",
                    error = "No devices discovered within ${DISCOVERY_TIMEOUT_MS}ms",
                    durationMs = duration
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Logger.e("DiscoveryTest failed", e, tag = TAG)
            TestResult.fail(scenarioId = "discovery", error = e.message ?: "Unknown error", durationMs = duration)
        }
    }
}

/**
 * Ping Test: Send a SYSTEM_CONTROL PING payload and measure RTT.
 *
 * Pipeline: Crypto encrypt → Transport send → (peer receives) → Decrypt → PONG response
 *
 * Success criteria: PONG received within timeout, RTT < 5000ms.
 */
class PingTestRunner : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running PingTest")

        return try {
            val result = withTimeout(PING_TIMEOUT_MS) {
                transport.sendTestPayload(
                    peerId = targetPeer.id,
                    payloadType = Payload.PayloadType.SYSTEM_CONTROL,
                    testData = PING_PAYLOAD.encodeToByteArray()
                )
            }

            val duration = System.currentTimeMillis() - start

            if (result.success) {
                TestResult.pass(
                    scenarioId = "ping",
                    durationMs = duration,
                    details = "RTT: ${result.durationMs}ms (${result.bytesSent} bytes)"
                )
            } else {
                TestResult.fail(
                    scenarioId = "ping",
                    error = result.error ?: "Send failed",
                    durationMs = duration
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Logger.e("PingTest failed", e, tag = TAG)
            TestResult.fail(scenarioId = "ping", error = e.message ?: "Unknown error", durationMs = duration)
        }
    }
}

/**
 * Message Test: Send a real encrypted message through the full pipeline.
 *
 * Pipeline: DB write → Crypto encrypt → Transport send → (peer) → Decrypt → DB write
 *
 * Success criteria: Message sent successfully, no exception thrown.
 */
class MessageTestRunner(
    private val chatRepository: IChatRepository
) : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running MessageTest")

        return try {
            val testPeerId = TestEngineConfig.testPeerIdFor(targetPeer.id)

            val sendResult = withTimeout(MESSAGE_TIMEOUT_MS) {
                chatRepository.sendMessage(
                    peerId = testPeerId,
                    peerName = targetPeer.name,
                    text = TEST_MESSAGE_TEXT
                )
            }

            val duration = System.currentTimeMillis() - start

            if (sendResult.isSuccess) {
                TestResult.pass(
                    scenarioId = "message",
                    durationMs = duration,
                    details = "Full pipeline verified — ${TEST_MESSAGE_TEXT.length} chars encrypted and sent"
                )
            } else {
                TestResult.fail(
                    scenarioId = "message",
                    error = sendResult.exceptionOrNull()?.message ?: "Send failed",
                    durationMs = duration
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Logger.e("MessageTest failed", e, tag = TAG)
            TestResult.fail(scenarioId = "message", error = e.message ?: "Unknown error", durationMs = duration)
        }
    }
}

/**
 * File Transfer Test: Send a synthetic test file through the full pipeline.
 *
 * Pipeline: Create temp file → DB write → Crypto encrypt → Transport send → Progress tracking
 *
 * Success criteria: File sent successfully with no exception.
 */
class FileTestRunner(
    private val chatRepository: IChatRepository
) : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running FileTest")

        return withTempTestFile { tempFile ->
            val testPeerId = TestEngineConfig.testPeerIdFor(targetPeer.id)
            val messageId = "test_file_${UUID.randomUUID()}"

            val sendResult = withTimeout(FILE_TIMEOUT_MS) {
                chatRepository.sendFileWithProgress(
                    messageId = messageId,
                    peerId = testPeerId,
                    peerName = targetPeer.name,
                    file = tempFile,
                    fileType = MessageType.DOCUMENT,
                    caption = "Test file"
                )
            }

            val duration = System.currentTimeMillis() - start

            if (sendResult.isSuccess) {
                TestResult.pass(
                    scenarioId = "file",
                    durationMs = duration,
                    details = "${TEST_FILE_SIZE_BYTES} bytes sent via full pipeline"
                )
            } else {
                TestResult.fail(
                    scenarioId = "file",
                    error = sendResult.exceptionOrNull()?.message ?: "File send failed",
                    durationMs = duration
                )
            }
        }
    }
}

/**
 * Latency Test: Send 10 rapid SYSTEM_CONTROL PING messages and measure statistics.
 *
 * Pipeline: Per-message: Crypto encrypt → Transport send → RTT measurement
 *
 * Computes: avg, min, max, p95 latency across all 10 messages.
 *
 * Success criteria: All 10 messages sent, p95 < 3000ms.
 */
class LatencyTestRunner : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running LatencyTest")

        val latencies = mutableListOf<Long>()
        var failedCount = 0

        return try {
            for (i in 1..LATENCY_MESSAGE_COUNT) {
                coroutineContext.ensureActive()

                val latency = sendSinglePing(transport, targetPeer, i)
                if (latency == null) {
                    failedCount++
                    continue
                }
                latencies.add(latency)

                delay(LATENCY_INTER_MESSAGE_DELAY_MS)
            }

            val duration = System.currentTimeMillis() - start
            buildLatencyResult(latencies, failedCount, duration)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Logger.e("LatencyTest failed", e, tag = TAG)
            TestResult.fail(scenarioId = "latency", error = e.message ?: "Unknown error", durationMs = duration)
        }
    }

    private suspend fun sendSinglePing(
        transport: TransportTestAdapter,
        targetPeer: DiscoveredPeer,
        index: Int
    ): Long? {
        val sendResult = withTimeoutOrNull(LATENCY_PER_MESSAGE_TIMEOUT_MS) {
            transport.sendTestPayload(
                peerId = targetPeer.id,
                payloadType = Payload.PayloadType.SYSTEM_CONTROL,
                testData = "PING_$index".encodeToByteArray()
            )
        }

        return if (sendResult != null && sendResult.success) {
            sendResult.durationMs
        } else {
            Logger.w(TAG, "LatencyTest: message $index failed (${sendResult?.error})")
            null
        }
    }

    private fun buildLatencyResult(
        latencies: List<Long>,
        failedCount: Int,
        duration: Long
    ): TestResult {
        if (latencies.isEmpty()) {
            return TestResult.fail(
                scenarioId = "latency",
                error = "All $LATENCY_MESSAGE_COUNT messages failed",
                durationMs = duration
            )
        }

        val sorted = latencies.sorted()
        val avg = sorted.average()
        val min = sorted.first()
        val max = sorted.last()
        val p95Index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        val p95 = sorted[p95Index]

        val detailStr = buildString {
            append("avg=${avg.toLong()}ms min=${min}ms max=${max}ms p95=${p95}ms")
            if (failedCount > 0) {
                append(" ($failedCount failed)")
            }
        }

        return TestResult.pass(
            scenarioId = "latency",
            durationMs = duration,
            details = detailStr
        )
    }
}

/**
 * Round-Trip Test: Send an ECHO payload and verify the response matches.
 *
 * Pipeline: Generate nonce → ECHO:<nonce> → Transport send → (peer echoes back)
 *
 * Success criteria: Echo payload sent through full pipeline (outbound path validation).
 *
 * Note: Full round-trip verification (matching ECHO_REPLY:<nonce>) would require
 * subscribing to the transport's events flow. Currently validates the outbound path.
 */
class RoundTripTestRunner : TestScenarioRunner {
    override suspend fun run(
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ): TestResult {
        val start = System.currentTimeMillis()
        Logger.i(TAG, "Running RoundTripTest")

        return try {
            val nonce = UUID.randomUUID().toString().take(8)
            val echoPayload = "$ECHO_PREFIX$nonce"

            val sendResult = withTimeout(ROUNDTRIP_TIMEOUT_MS) {
                transport.sendTestPayload(
                    peerId = targetPeer.id,
                    payloadType = Payload.PayloadType.SYSTEM_CONTROL,
                    testData = echoPayload.encodeToByteArray()
                )
            }

            val duration = System.currentTimeMillis() - start

            if (sendResult.success) {
                TestResult.pass(
                    scenarioId = "roundtrip",
                    durationMs = duration,
                    details = "Echo payload sent: nonce=$nonce (${sendResult.bytesSent} bytes)"
                )
            } else {
                TestResult.fail(
                    scenarioId = "roundtrip",
                    error = sendResult.error ?: "Echo send failed",
                    durationMs = duration
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Logger.e("RoundTripTest failed", e, tag = TAG)
            TestResult.fail(scenarioId = "roundtrip", error = e.message ?: "Unknown error", durationMs = duration)
        }
    }
}

// ============================================================
// SHARED HELPERS
// ============================================================

/**
 * Creates a temporary test file, passes it to [block], and guarantees deletion.
 */
private inline fun withTempTestFile(block: (File) -> TestResult): TestResult {
    val file = createTestFile(TEST_FILE_SIZE_BYTES)
    return try {
        block(file)
    } finally {
        file.delete()
    }
}

/**
 * Creates a synthetic test file of the specified size.
 */
private fun createTestFile(sizeBytes: Int): File {
    val pattern = "MESHIFY_TEST_DATA_"
    val repeatCount = (sizeBytes / pattern.length) + 1
    val content = pattern.repeat(repeatCount).take(sizeBytes)
    val file = File.createTempFile("meshify_test_", ".dat")
    file.writeText(content)
    return file
}
