package com.p2p.meshify.feature.realdevicetesting.preflight

import android.content.Context
import com.p2p.meshify.core.common.preflight.ConnectivityChecker
import com.p2p.meshify.core.common.preflight.PermissionChecker
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates all pre-flight checks before real-device testing.
 *
 * After Phase 3 security simplification, encryption checks are removed.
 * Runs ALL checks regardless of individual outcomes:
 * 1. PermissionChecker — verifies required permissions are granted
 * 2. ConnectivityChecker — verifies WiFi connected with valid IP
 */
class PreFlightChecker(
    context: Context,
    testPort: Int = 8888
) {
    private val permissionChecker = PermissionChecker(context)
    private val connectivityChecker = ConnectivityChecker(context, testPort)

    /**
     * Run all pre-flight checks.
     *
     * @return PreFlightResult with all check results
     */
    suspend fun runAllChecks(): PreFlightResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        Logger.i("Starting pre-flight checks...", tag = "PreFlightChecker")

        // Check 1: Permissions
        val permissionResults = runPermissionChecks()

        // Check 2: Connectivity
        val connectivityResult = runConnectivityCheck()

        val totalDuration = System.currentTimeMillis() - startTime

        val result = PreFlightResult(
            permissionResults = permissionResults,
            connectivityResult = connectivityResult,
            securityResult = null,
            totalDurationMs = totalDuration
        )

        if (result.allPassed) {
            Logger.i(
                "All pre-flight checks PASSED (${totalDuration}ms) — " +
                    "testing can proceed",
                tag = "PreFlightChecker"
            )
        } else {
            val failed = result.failedChecks
            Logger.w(
                "Pre-flight checks FAILED: ${failed.size} check(s) failed. " +
                    failed.joinToString("; ") { "${it.name}: ${it.detail}" },
                tag = "PreFlightChecker"
            )
        }

        result
    }

    private fun runPermissionChecks(): List<CheckResult> {
        val results = permissionChecker.checkTestPermissions()

        return results.map { permResult ->
            val permissionName = permResult.permission.substringAfterLast('.')
            CheckResult(
                name = "Permission: $permissionName",
                status = if (permResult.granted) CheckStatus.PASS else CheckStatus.FAIL,
                detail = if (permResult.granted) "Granted" else "Denied"
            )
        }
    }

    private fun runConnectivityCheck(): CheckResult {
        return try {
            val connResult = connectivityChecker.checkConnectivity()

            if (connResult.allPassed) {
                CheckResult(
                    name = "Connectivity",
                    status = CheckStatus.PASS,
                    detail = "WiFi connected, IP=${connResult.ipAddress}"
                )
            } else {
                CheckResult(
                    name = "Connectivity",
                    status = CheckStatus.FAIL,
                    detail = connResult.issues.joinToString("; ")
                )
            }
        } catch (e: Exception) {
            CheckResult(
                name = "Connectivity",
                status = CheckStatus.FAIL,
                detail = "Exception: ${e.message}"
            )
        }
    }
}
