package com.p2p.meshify.core.common.preflight

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ConnectivityChecker].
 *
 * Two layers are covered:
 * - Pure logic on [ConnectivityResult] (`allPassed`, `isOnLocalSubnet`) — no
 *   Android interaction, can be asserted directly with synthetic values.
 * - Live [ConnectivityChecker.checkConnectivity] under Robolectric — verifies
 *   that the method runs to completion on a shadowed device (no
 *   `Context.getSystemService` NPE), and that a missing WiFi/network produces
 *   the documented `issues` list.
 *
 * Note: the live check uses Robolectric defaults (WiFi off, no active network)
 * which exercises the "all checks fail" path. The IPv4 / socket branches run
 * but produce no IP, so `canReachLocalPort` is `false` — matching real-device
 * behavior on a freshly-booted emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ConnectivityCheckerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ---- ConnectivityResult — pure logic ----

    @Test
    fun `allPassed — true only when every boolean field is true`() {
        val ok = ConnectivityResult(
            wifiEnabled = true,
            wifiConnected = true,
            hasIpAddress = true,
            ipAddress = "192.168.1.42",
            canReachLocalPort = true
        )
        assertTrue(ok.allPassed)

        // Each field flip must drop allPassed to false.
        assertFalse(ok.copy(wifiEnabled = false).allPassed)
        assertFalse(ok.copy(wifiConnected = false).allPassed)
        assertFalse(ok.copy(hasIpAddress = false).allPassed)
        assertFalse(ok.copy(canReachLocalPort = false).allPassed)
    }

    @Test
    fun `allPassed — false even when canReachLocalPort is the only false`() {
        val r = ConnectivityResult(
            wifiEnabled = true,
            wifiConnected = true,
            hasIpAddress = true,
            ipAddress = "192.168.0.10",
            canReachLocalPort = false
        )
        assertFalse(r.allPassed)
    }

    @Test
    fun `isOnLocalSubnet — common private subnets match`() {
        listOf(
            "192.168.0.1",
            "192.168.255.254",
            "10.0.0.1",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.254"
        ).forEach { ip ->
            assertTrue("expected $ip to be a local-subnet IP", ConnectivityResult(
                wifiEnabled = true,
                wifiConnected = true,
                hasIpAddress = true,
                ipAddress = ip,
                canReachLocalPort = true
            ).isOnLocalSubnet)
        }
    }

    @Test
    fun `isOnLocalSubnet — public IPs and null do not match`() {
        listOf("8.8.8.8", "1.1.1.1", "169.254.0.1" /* link-local */).forEach { ip ->
            assertFalse(
                "expected $ip to NOT be a local-subnet IP",
                ConnectivityResult(
                    wifiEnabled = true,
                    wifiConnected = true,
                    hasIpAddress = true,
                    ipAddress = ip,
                    canReachLocalPort = false
                ).isOnLocalSubnet
            )
        }

        // null IP -> false.
        assertFalse(ConnectivityResult(
            wifiEnabled = true,
            wifiConnected = true,
            hasIpAddress = false,
            ipAddress = null,
            canReachLocalPort = false
        ).isOnLocalSubnet)
    }

    @Test
    fun `issues — defaults to empty when not provided`() {
        val r = ConnectivityResult(
            wifiEnabled = true,
            wifiConnected = true,
            hasIpAddress = true,
            ipAddress = "192.168.1.1",
            canReachLocalPort = true
        )
        assertTrue(r.issues.isEmpty())
    }

    // ---- Live ConnectivityChecker.checkConnectivity (Robolectric) ----

    /**
     * Smoke-test that [ConnectivityChecker.checkConnectivity] runs to completion
     * on a shadowed device (no `Context.getSystemService` NPE).
     *
     * Why we only pin [ConnectivityResult.wifiConnected] and the issue list:
     *
     * - `wifiConnected` requires an active `Network` with `TRANSPORT_WIFI` +
     *   `NET_CAPABILITY_INTERNET` — neither exists in a fresh Robolectric
     *   sandbox, so this is reliably `false`.
     * - `hasIpAddress` / `ipAddress` are NOT pinned because Robolectric 4.16
     *   does NOT shadow `NetworkInterface.getNetworkInterfaces()` on the host
     *   JVM — on a real Linux dev box, host interfaces (e.g. `eth0`/`wlan0`)
     *   are visible to the underlying JVM and `findFirstValidIpv4OnInterface`
     *   returns the first non-loopback IPv4. That value is environment-dependent.
     * - `canReachLocalPort` similarly depends on `hasIpAddress`: when an IP is
     *   found, the check tries `Socket.connect(127.0.0.1, testPort)` and
     *   treats `ConnectException` (port closed) as **success** ("socket stack
     *   works"). So on a dev box that exposes any host interface, this is
     *   `true`. We don't pin it.
     */
    @Test
    fun `checkConnectivity — runs under Robolectric and populates result`() {
        val checker = ConnectivityChecker(context, testPort = 1) // unused port
        // Must not throw on a shadowed device (no real ConnectivityManager/WifiManager).
        val result = checker.checkConnectivity()

        // Without a real active network in Robolectric's sandbox, allPassed is
        // necessarily false — wifiConnected is reliably false.
        assertFalse(
            "no real TRANSPORT_WIFI network in Robolectric sandbox — allPassed must be false",
            result.allPassed
        )
        assertEquals(false, result.wifiConnected)

        // The issues list must capture the WiFi-not-connected failure and (if
        // no host interface exposed an IP) the no-IP failure.
        assertTrue(
            "expected non-empty issues when off-network, got ${result.issues}",
            result.issues.isNotEmpty()
        )
        assertTrue(
            "expected 'Not connected to WiFi' in issues, got ${result.issues}",
            result.issues.any { it.contains("WiFi", ignoreCase = true) }
        )
    }
}
