package com.p2p.meshify.core.common.preflight

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ConnectivityChecker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityCheckerTest {

    private val testPort = 9999

    // ============================================================================
    // SECTION 1: ConnectvityResult DATA CLASS — allPassed
    // ============================================================================

    @Test
    fun `allPassed returns true when all conditions are met`() {
        val result = makePassingResult()
        assertTrue(result.allPassed)
    }

    @Test
    fun `allPassed returns false when wifiEnabled is false`() {
        assertFalse(makePassingResult().copy(wifiEnabled = false).allPassed)
    }

    @Test
    fun `allPassed returns false when wifiConnected is false`() {
        assertFalse(makePassingResult().copy(wifiConnected = false).allPassed)
    }

    @Test
    fun `allPassed returns false when hasIpAddress is false`() {
        assertFalse(makePassingResult().copy(hasIpAddress = false).allPassed)
    }

    @Test
    fun `allPassed returns false when canReachLocalPort is false`() {
        assertFalse(makePassingResult().copy(canReachLocalPort = false).allPassed)
    }

    // ============================================================================
    // SECTION 2: ConnectivityResult DATA CLASS — isOnLocalSubnet
    // ============================================================================

    @Test
    fun `isOnLocalSubnet returns true for 192 168 x x`() {
        assertTrue(makeResult(ip = "192.168.1.5").isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet returns true for 10 x x x`() {
        assertTrue(makeResult(ip = "10.0.0.42").isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet returns true for 172 x x x`() {
        assertTrue(makeResult(ip = "172.16.0.1").isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet returns false for public IP`() {
        assertFalse(makeResult(ip = "8.8.8.8").isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet returns false when ipAddress is null`() {
        val result = ConnectivityResult(
            wifiEnabled = false, wifiConnected = false,
            hasIpAddress = false, ipAddress = null, canReachLocalPort = false
        )
        assertFalse(result.isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet handles edge case IPs`() {
        assertTrue(makeResult(ip = "192.168.0.1").isOnLocalSubnet)
        assertTrue(makeResult(ip = "192.168.255.255").isOnLocalSubnet)
        assertTrue(makeResult(ip = "10.0.0.0").isOnLocalSubnet)
        assertTrue(makeResult(ip = "10.255.255.255").isOnLocalSubnet)
        assertTrue(makeResult(ip = "172.0.0.1").isOnLocalSubnet)
        assertTrue(makeResult(ip = "172.255.255.255").isOnLocalSubnet)
        assertFalse(makeResult(ip = "173.0.0.1").isOnLocalSubnet)
        assertFalse(makeResult(ip = "11.0.0.1").isOnLocalSubnet)
        assertFalse(makeResult(ip = "1.2.3.4").isOnLocalSubnet)
    }

    @Test
    fun `isOnLocalSubnet with empty string ip`() {
        val result = makeResult(ip = "")
        assertFalse(result.isOnLocalSubnet)
    }

    // ============================================================================
    // SECTION 3: ConnectivityResult DATA CLASS — properties
    // ============================================================================

    @Test
    fun `ConnectivityResult stores all properties correctly`() {
        val issues = listOf("WiFi is disabled", "Not connected")
        val result = ConnectivityResult(
            wifiEnabled = false, wifiConnected = false,
            hasIpAddress = true, ipAddress = "192.168.1.5",
            canReachLocalPort = true, issues = issues
        )
        assertEquals(false, result.wifiEnabled)
        assertEquals(false, result.wifiConnected)
        assertEquals(true, result.hasIpAddress)
        assertEquals("192.168.1.5", result.ipAddress)
        assertEquals(true, result.canReachLocalPort)
        assertEquals(issues, result.issues)
    }

    @Test
    fun `ConnectivityResult default issues is empty`() {
        val result = makePassingResult()
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `ConnectivityResult copy preserves unmodified fields`() {
        val original = makePassingResult()
        val modified = original.copy(wifiEnabled = false)
        assertFalse(modified.wifiEnabled)
        assertTrue(modified.wifiConnected)
        assertEquals("192.168.1.5", modified.ipAddress)
        assertTrue(original.wifiEnabled)
    }

    // ============================================================================
    // SECTION 4: PRODUCTION CODE — checkConnectivity with mocked services
    // ============================================================================

    @Test
    fun `checkConnectivity returns issues when WiFi disabled`() {
        // Given
        val checker = createChecker(wifiEnabled = false, hasActiveNetwork = true)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse(result.allPassed)
        assertFalse(result.wifiEnabled)
        assertTrue(result.issues.any { it.contains("disabled") })
    }

    @Test
    fun `checkConnectivity returns issues when no active network`() {
        // Given
        val checker = createChecker(wifiEnabled = true, hasActiveNetwork = false)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse(result.allPassed)
        assertFalse(result.wifiConnected)
        assertTrue(result.issues.any { it.contains("Not connected") })
    }

    @Test
    fun `checkConnectivity with active network lacking WiFi transport`() {
        // Given
        val checker = createChecker(wifiEnabled = true, hasActiveNetwork = true, hasWifiTransport = false)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse(result.allPassed)
        assertFalse(result.wifiConnected)
    }

    @Test
    fun `checkConnectivity with active network lacking internet capability`() {
        // Given
        val checker = createChecker(wifiEnabled = true, hasActiveNetwork = true, hasInternetCapability = false)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse(result.allPassed)
        assertFalse(result.wifiConnected)
    }

    @Test
    fun `checkConnectivity with all conditions met`() {
        // Given
        val checker = createChecker(wifiEnabled = true, hasActiveNetwork = true)

        // When
        val result = checker.checkConnectivity()

        // Then
        // Note: hasIpAddress depends on the test environment (real NetworkInterface)
        // Only test the checks we can control
        assertTrue("WiFi should be enabled", result.wifiEnabled)
        assertTrue("WiFi should be connected", result.wifiConnected)
        assertTrue("Local port should be reachable (ConnectException handled)", result.canReachLocalPort)
    }

    @Test
    fun `checkConnectivity with all failures produces multiple issues`() {
        // Given
        val checker = createChecker(wifiEnabled = false, hasActiveNetwork = false)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse(result.allPassed)
        assertTrue("Should have issues for each failure", result.issues.size >= 2)
        assertTrue(result.issues.any { it.contains("WiFi is disabled") })
        assertTrue(result.issues.any { it.contains("Not connected") })
    }

    @Test
    fun `checkConnectivity uses specified test port`() {
        // Given
        val customPort = 12345
        val context = createMockContext(wifiEnabled = true, hasActiveNetwork = true)
        val checker = ConnectivityChecker(context, testPort = customPort)

        // When
        val result = checker.checkConnectivity()

        // Then - should not crash, port gets connection refused which returns true
        assertTrue(result.canReachLocalPort)
    }

    @Test
    fun `checkConnectivity with null NetworkCapabilities doesn't crash`() {
        // Given
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()

        every { context.applicationContext } returns context
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { wifiManager.isWifiEnabled } returns true
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        val checker = ConnectivityChecker(context, testPort = testPort)

        // When
        val result = checker.checkConnectivity()

        // Then
        assertFalse("Should fail when capabilities are null", result.wifiConnected)
        assertFalse(result.allPassed)
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private fun createChecker(
        wifiEnabled: Boolean,
        hasActiveNetwork: Boolean,
        hasWifiTransport: Boolean = true,
        hasInternetCapability: Boolean = true
    ): ConnectivityChecker {
        val context = createMockContext(wifiEnabled, hasActiveNetwork, hasWifiTransport, hasInternetCapability)
        return ConnectivityChecker(context, testPort = testPort)
    }

    private fun createMockContext(
        wifiEnabled: Boolean,
        hasActiveNetwork: Boolean,
        hasWifiTransport: Boolean = true,
        hasInternetCapability: Boolean = true
    ): Context {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        val connectivityManager = mockk<ConnectivityManager>()

        every { context.applicationContext } returns context
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { wifiManager.isWifiEnabled } returns wifiEnabled

        if (hasActiveNetwork) {
            val network = mockk<Network>()
            val capabilities = mockk<NetworkCapabilities>()

            every { connectivityManager.activeNetwork } returns network
            every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
            every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns hasWifiTransport
            every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternetCapability
        } else {
            every { connectivityManager.activeNetwork } returns null
        }

        return context
    }

    private fun makeResult(ip: String): ConnectivityResult {
        return ConnectivityResult(
            wifiEnabled = true, wifiConnected = true,
            hasIpAddress = true, ipAddress = ip, canReachLocalPort = true
        )
    }

    private fun makePassingResult(): ConnectivityResult {
        return ConnectivityResult(
            wifiEnabled = true, wifiConnected = true,
            hasIpAddress = true, ipAddress = "192.168.1.5", canReachLocalPort = true
        )
    }
}
