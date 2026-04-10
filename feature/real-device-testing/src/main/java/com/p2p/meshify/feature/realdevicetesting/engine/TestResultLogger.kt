package com.p2p.meshify.feature.realdevicetesting.engine

import android.content.Context
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import com.p2p.meshify.domain.model.TransportType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "TestResultLogger"
private const val LOG_DIR_NAME = "test_logs"
private const val LOG_FILE_PREFIX = "meshify_test_"
private const val LOG_FILE_EXTENSION = ".log"

/**
 * Plain-text log writer for test results.
 *
 * Writes human-readable test logs to the app's cache directory.
 * The log file can be exported via Share Intent for debugging.
 *
 * Log format:
 * ```
 * === Meshify Real-Device Test Log ===
 * Date: 2026-04-10 14:32:01
 * Target: DeviceXYZ (lan)
 * ====================================
 *
 * [14:32:01] DiscoveryTest: PASSED (2.3s) — 3 devices found
 * [14:32:05] PingTest: PASSED (0.8s) — RTT: 12ms
 * [14:32:10] MessageTest: FAILED (4.2s) — Encryption failed
 * ...
 *
 * === Summary ===
 * Total: 6 | Passed: 4 | Failed: 1 | Timeout: 1
 * Total Duration: 45.2s
 * ```
 *
 * Thread safety: This class is NOT thread-safe. It is designed for sequential
 * use by the Test Engine, which runs tests one at a time. If concurrent access
 * is needed, wrap all public methods with a Mutex.
 */
class TestResultLogger(
    private val context: Context
) {
    private val logEntries = mutableListOf<String>()
    private var headerWritten = false
    private var targetPeerInfo = ""
    private var transportInfo = ""
    private var sessionStartTime = 0L

    /**
     * Starts a new test session with header information.
     *
     * @param targetPeer The target device being tested.
     * @param transportName The transport type name (e.g., "lan", "ble").
     */
    fun startSession(targetPeer: DiscoveredPeer, transportName: String) {
        sessionStartTime = System.currentTimeMillis()
        targetPeerInfo = "${targetPeer.name} (${targetPeer.address})"
        transportInfo = transportName.uppercase()

        val timestamp = formatTimestamp(sessionStartTime)
        logEntries.clear()
        logEntries.add("=== Meshify Real-Device Test Log ===")
        logEntries.add("Date: $timestamp")
        logEntries.add("Target: $targetPeerInfo")
        logEntries.add("Transport: $transportInfo")
        logEntries.add("====================================")
        logEntries.add("")
        headerWritten = true

        Logger.i(TAG, "Test session started for $targetPeerInfo via $transportInfo")
    }

    /**
     * Appends a single test result to the log.
     *
     * Format: `[HH:MM:SS] TestName: STATUS (duration) — details`
     *
     * @param result The test result to log.
     */
    fun appendResult(result: TestResult) {
        if (!headerWritten) {
            Logger.w(TAG, "appendResult called before startSession — header auto-generated")
            // Create a minimal header if startSession wasn't called
            startSession(
                DiscoveredPeer(id = "unknown", name = "Unknown", address = "unknown", transportType = TransportType.LAN),
                "unknown"
            )
        }

        val timestamp = formatTimestamp(System.currentTimeMillis())
        val testName = result.scenarioId.replaceFirstChar { it.uppercase() } + "Test"
        val statusLabel = when (result.status) {
            TestStatus.PASSED -> "PASSED"
            TestStatus.FAILED -> "FAILED"
            TestStatus.TIMEOUT -> "TIMEOUT"
            TestStatus.RUNNING -> "RUNNING"
            TestStatus.PENDING -> "PENDING"
        }

        val durationStr = result.durationMs?.let { " (${formatDuration(it)})" } ?: ""
        val detailStr = buildString {
            if (result.details.isNotBlank()) {
                append(" — ${result.details}")
            }
            if (result.error != null) {
                if (isNotEmpty()) append(" | ")
                append("Error: ${result.error}")
            }
        }

        val line = "[$timestamp] $testName: $statusLabel$durationStr$detailStr"
        logEntries.add(line)

        Logger.i(TAG, "Logged: $line")
    }

    /**
     * Appends a summary line and finalizes the log.
     *
     * @param results All test results for the session.
     * @param totalDurationMs Total time for the entire test session.
     */
    fun appendSummary(results: List<TestResult>, totalDurationMs: Long) {
        val passed = results.count { it.status == TestStatus.PASSED }
        val failed = results.count { it.status == TestStatus.FAILED }
        val timeout = results.count { it.status == TestStatus.TIMEOUT }
        val pending = results.count { it.status == TestStatus.PENDING }
        val total = results.size

        logEntries.add("")
        logEntries.add("=== Summary ===")
        logEntries.add("Total: $total | Passed: $passed | Failed: $failed | Timeout: $timeout | Pending: $pending")
        logEntries.add("Total Duration: ${formatDuration(totalDurationMs)}")
        logEntries.add("====================================")

        Logger.i(TAG, "Summary: $total tests, $passed passed, $failed failed, $timeout timeout")
    }

    /**
     * Exports the current log to a file in the app's cache directory.
     *
     * The file is named `meshify_test_<timestamp>.log` and can be shared
     * via Android's Share Intent.
     *
     * @return Result containing the exported File, or failure with error details.
     */
    fun exportLogFile(): Result<File> = try {
        if (logEntries.isEmpty()) {
            return Result.failure(IllegalStateException("No log entries to export"))
        }

        val logDir = File(context.cacheDir, LOG_DIR_NAME)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val fileName = "${LOG_FILE_PREFIX}${timestamp}${LOG_FILE_EXTENSION}"
        val logFile = File(logDir, fileName)

        logFile.writeText(logEntries.joinToString("\n"))
        Logger.i(TAG, "Log exported to ${logFile.absolutePath} (${logFile.length()} bytes)")
        Result.success(logFile)
    } catch (e: Exception) {
        Logger.e("Failed to export log file", e, tag = TAG)
        Result.failure(e)
    }

    /**
     * Returns the full log content as a string (for in-app display).
     */
    fun getLogText(): String = logEntries.joinToString("\n")

    /**
     * Clears the in-memory log buffer. Does NOT delete exported files.
     */
    fun clear() {
        logEntries.clear()
        headerWritten = false
        targetPeerInfo = ""
        transportInfo = ""
    }

    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(epochMs))
    }

    private fun formatDuration(durationMs: Long): String {
        return when {
            durationMs < 1_000 -> "${durationMs}ms"
            durationMs < 60_000 -> String.format(Locale.US, "%.1fs", durationMs / 1000.0)
            else -> {
                val minutes = durationMs / 60_000
                val seconds = (durationMs % 60_000) / 1000
                "${minutes}m ${seconds}s"
            }
        }
    }
}
