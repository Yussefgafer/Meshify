package com.p2p.meshify.feature.realdevicetesting.preflight

/**
 * Status of a single pre-flight check.
 */
enum class CheckStatus {
    /** Check passed */
    PASS,
    /** Check failed — testing cannot proceed */
    FAIL,
    /** Check skipped — not applicable or not required */
    SKIP
}

/**
 * Result of a single pre-flight check.
 * @property name Human-readable name of the check
 * @property status PASS, FAIL, or SKIP
 * @property detail Additional detail message (e.g., error reason or metric)
 */
data class CheckResult(
    val name: String,
    val status: CheckStatus,
    val detail: String
)

/**
 * Aggregated result of all pre-flight checks.
 * @property permissionResults Results of permission checks
 * @property connectivityResult Result of connectivity checks
 * @property securityResult Result of security checks (null after Phase 3 - encryption removed)
 * @property allPassed Whether ALL checks passed (testing can proceed)
 * @property failedChecks List of checks that failed
 */
data class PreFlightResult(
    val permissionResults: List<CheckResult>,
    val connectivityResult: CheckResult,
    val securityResult: CheckResult?,
    val totalDurationMs: Long
) {
    /**
     * Whether all checks passed and testing can proceed.
     */
    val allPassed: Boolean
        get() = permissionResults.all { it.status == CheckStatus.PASS } &&
            connectivityResult.status == CheckStatus.PASS &&
            (securityResult == null || securityResult.status == CheckStatus.PASS)

    /**
     * List of all failed checks with their reasons.
     */
    val failedChecks: List<CheckResult>
        get() = (permissionResults + listOfNotNull(connectivityResult, securityResult))
            .filter { it.status == CheckStatus.FAIL }
}
