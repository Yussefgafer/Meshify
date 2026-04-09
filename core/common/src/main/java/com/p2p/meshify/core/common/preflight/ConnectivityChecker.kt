package com.p2p.meshify.core.common.preflight

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.p2p.meshify.core.util.Logger
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Result of connectivity checks.
 * @property wifiEnabled Whether WiFi is enabled
 * @property wifiConnected Whether device is connected to a WiFi network
 * @property hasIpAddress Whether device has a valid IPv4 address on WiFi interface
 * @property ipAddress The device's current IPv4 address (or null)
 * @property canReachLocalPort Whether a test socket to localhost:port succeeds
 * @property issues List of issues found (empty if all checks pass)
 */
data class ConnectivityResult(
    val wifiEnabled: Boolean,
    val wifiConnected: Boolean,
    val hasIpAddress: Boolean,
    val ipAddress: String?,
    val canReachLocalPort: Boolean,
    val issues: List<String> = emptyList()
) {
    /**
     * Whether all connectivity checks passed.
     */
    val allPassed: Boolean
        get() = wifiEnabled && wifiConnected && hasIpAddress && canReachLocalPort

    /**
     * Whether device is on the same subnet as typical home routers (192.168.x.x or 10.x.x.x).
     */
    val isOnLocalSubnet: Boolean
        get() = ipAddress?.let { ip ->
            ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")
        } ?: false
}

/**
 * Checks network connectivity for real-device testing.
 *
 * Goes beyond WifiStateChecker (which only checks isWifiEnabled) to verify:
 * 1. WiFi is enabled
 * 2. Device is actually connected to a WiFi access point
 * 3. Device has a valid IPv4 address assigned
 * 4. Local socket connectivity works (test connection to localhost:port)
 *
 * This ensures the LAN transport can function before running tests.
 */
class ConnectivityChecker(
    private val context: Context,
    private val testPort: Int = 8888
) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 2000
    }

    /**
     * Run all connectivity checks.
     * @return ConnectivityResult with all check results
     */
    fun checkConnectivity(): ConnectivityResult {
        val issues = mutableListOf<String>()

        // Check 1: WiFi enabled
        val wifiEnabled = wifiManager.isWifiEnabled
        if (!wifiEnabled) {
            issues.add("WiFi is disabled")
        }

        // Check 2: Connected to WiFi network
        val wifiConnected = checkWifiConnected()
        if (!wifiConnected) {
            issues.add("Not connected to WiFi network")
        }

        // Check 3: Has valid IP address
        val ipAddress = getWifiIpAddress()
        val hasIpAddress = ipAddress != null
        if (!hasIpAddress) {
            issues.add("No IPv4 address assigned")
        }

        // Check 4: Can reach local port
        val canReachLocalPort = if (hasIpAddress) {
            testLocalPortReachability()
        } else {
            false
        }
        if (!canReachLocalPort) {
            issues.add("Cannot reach localhost:$testPort")
        }

        Logger.i(
            "Connectivity check: wifiEnabled=$wifiEnabled, " +
                "wifiConnected=$wifiConnected, " +
                "ip=$ipAddress, " +
                "portReachable=$canReachLocalPort",
            tag = "ConnectivityChecker"
        )

        return ConnectivityResult(
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            hasIpAddress = hasIpAddress,
            ipAddress = ipAddress,
            canReachLocalPort = canReachLocalPort,
            issues = issues
        )
    }

    /**
     * Check if device is connected to a WiFi network with internet capability.
     * Uses NetworkCapabilities to verify active connection, not just WiFi enabled.
     */
    private fun checkWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get the device's current IPv4 address on the WiFi interface.
     * Iterates network interfaces to find the first non-loopback IPv4 address.
     */
    private fun getWifiIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip loopback, down, or virtual interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                findFirstValidIpv4OnInterface(networkInterface)?.let { ip ->
                    Logger.d("Found IP address: $ip on ${networkInterface.name}", tag = "ConnectivityChecker")
                    return ip
                }
            }
            null
        } catch (e: Exception) {
            Logger.e("Failed to get WiFi IP address: ${e.message}", tag = "ConnectivityChecker")
            null
        }
    }

    /**
     * Find the first valid non-loopback IPv4 address on a network interface.
     * Extracted to reduce nesting in getWifiIpAddress().
     */
    private fun findFirstValidIpv4OnInterface(networkInterface: NetworkInterface): String? {
        val addresses = networkInterface.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (address !is Inet4Address || address.isLoopbackAddress) continue

            val hostAddress = address.hostAddress
            if (!hostAddress.isNullOrEmpty()) {
                return hostAddress
            }
        }
        return null
    }

    /**
     * Test if a socket can connect to localhost:testPort.
     * This verifies that the local network stack is functional.
     * Does NOT require the server to be running — only checks socket creation.
     */
    private fun testLocalPortReachability(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.connect(java.net.InetSocketAddress("127.0.0.1", testPort), SOCKET_TIMEOUT_MS)
            }
            true
        } catch (e: java.net.ConnectException) {
            // Connection refused = server not running, but socket stack works
            Logger.d("Port $testPort not listening (expected if server not started)", tag = "ConnectivityChecker")
            true
        } catch (e: Exception) {
            Logger.e("Port $testPort unreachable: ${e.message}", tag = "ConnectivityChecker")
            false
        }
    }
}
