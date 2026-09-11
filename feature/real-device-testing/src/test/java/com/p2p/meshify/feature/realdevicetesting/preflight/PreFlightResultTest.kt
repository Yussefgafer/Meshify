package com.p2p.meshify.feature.realdevicetesting.preflight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreFlightResultTest {

    @Test
    fun `allPassed true when permissions pass, connectivity passes, security is null`() {
        val result = PreFlightResult(
            permissionResults = listOf(ok("Wifi"), ok("Location")),
            connectivityResult = ok("Connectivity"),
            securityResult = null,
            totalDurationMs = 10
        )

        assertTrue(result.allPassed)
        assertTrue(result.failedChecks.isEmpty())
    }

    @Test
    fun `allPassed false when any permission fails`() {
        val result = PreFlightResult(
            permissionResults = listOf(ok("Wifi"), fail("Location", "Denied")),
            connectivityResult = ok("Connectivity"),
            securityResult = null,
            totalDurationMs = 10
        )

        assertFalse(result.allPassed)
        assertEquals(1, result.failedChecks.size)
    }

    @Test
    fun `allPassed false when connectivity fails`() {
        val result = PreFlightResult(
            permissionResults = listOf(ok("Wifi")),
            connectivityResult = fail("Connectivity", "No WiFi"),
            securityResult = null,
            totalDurationMs = 10
        )

        assertFalse(result.allPassed)
        assertEquals(1, result.failedChecks.size)
    }

    @Test
    fun `allPassed false when security result fails`() {
        val result = PreFlightResult(
            permissionResults = listOf(ok("Wifi")),
            connectivityResult = ok("Connectivity"),
            securityResult = fail("Security", "ECDH failed"),
            totalDurationMs = 10
        )

        assertFalse(result.allPassed)
        assertEquals(1, result.failedChecks.size)
    }

    @Test
    fun `failedChecks aggregates failures across all categories`() {
        val result = PreFlightResult(
            permissionResults = listOf(ok("Wifi"), fail("Location", "Denied")),
            connectivityResult = fail("Connectivity", "No WiFi"),
            securityResult = null,
            totalDurationMs = 10
        )

        assertEquals(2, result.failedChecks.size)
        val names = result.failedChecks.map { it.name }
        assertTrue(names.contains("Location"))
        assertTrue(names.contains("Connectivity"))
    }

    private fun ok(name: String): CheckResult = CheckResult(name, CheckStatus.PASS, "OK")
    private fun fail(name: String, detail: String): CheckResult = CheckResult(name, CheckStatus.FAIL, detail)
}
