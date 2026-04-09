package com.p2p.meshify.feature.realdevicetesting.model

/**
 * Holds the result of a single test execution.
 *
 * @property scenarioId The ID of the [TestScenario] this result belongs to.
 * @property status The outcome of the test.
 * @property durationMs Time taken to complete the test in milliseconds (null if not yet run).
 * @property details Human-readable detail about the result (e.g., "12ms RTT", "3 devices found").
 * @property error Exception message if the test failed (null on success).
 */
data class TestResult(
    val scenarioId: String,
    val status: TestStatus = TestStatus.PENDING,
    val durationMs: Long? = null,
    val details: String = "",
    val error: String? = null
) {
    companion object {
        fun pending(scenarioId: String) = TestResult(scenarioId = scenarioId)

        fun running(scenarioId: String) = TestResult(
            scenarioId = scenarioId,
            status = TestStatus.RUNNING
        )

        fun pass(scenarioId: String, durationMs: Long, details: String = "") = TestResult(
            scenarioId = scenarioId,
            status = TestStatus.PASSED,
            durationMs = durationMs,
            details = details
        )

        fun fail(scenarioId: String, error: String, durationMs: Long? = null) = TestResult(
            scenarioId = scenarioId,
            status = TestStatus.FAILED,
            durationMs = durationMs,
            error = error
        )

        fun timeout(scenarioId: String, durationMs: Long) = TestResult(
            scenarioId = scenarioId,
            status = TestStatus.TIMEOUT,
            durationMs = durationMs,
            error = "Test timed out after ${durationMs}ms"
        )
    }
}

/** Possible outcomes of a test execution. */
enum class TestStatus {
    /** Not yet executed. */
    PENDING,

    /** Currently executing. */
    RUNNING,

    /** Completed successfully. */
    PASSED,

    /** Completed with errors. */
    FAILED,

    /** Exceeded the time limit without completing. */
    TIMEOUT,
}
