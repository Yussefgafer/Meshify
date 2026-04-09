package com.p2p.meshify.core.common.preflight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Result of a permission check.
 * @property granted Whether the permission is granted
 * @property permission The permission that was checked
 * @property required Whether this permission is required for core functionality
 */
data class PermissionResult(
    val granted: Boolean,
    val permission: String,
    val required: Boolean
)

/**
 * Checks runtime permissions required for local network testing.
 *
 * Unlike MainActivity's permission flow (which requests all app permissions at startup),
 * this checker verifies specific permissions needed for real-device testing without
 * triggering runtime permission dialogs. It's a read-only check.
 *
 * Required permissions for testing:
 * - ACCESS_WIFI_STATE: Read WiFi state for RSSI
 * - NEARBY_WIFI_DEVICES (API 33+): Discover nearby devices on WiFi
 * - ACCESS_FINE_LOCATION (API < 31): Legacy WiFi discovery
 */
class PermissionChecker(private val context: Context) {

    /**
     * Check all permissions required for real-device testing.
     * Does NOT request permissions — only reports current state.
     *
     * @return List of PermissionResult for each required permission
     */
    fun checkTestPermissions(): List<PermissionResult> {
        val results = mutableListOf<PermissionResult>()

        // Always required: WiFi state
        results.add(
            checkPermission(
                Manifest.permission.ACCESS_WIFI_STATE,
                required = true
            )
        )

        results.add(
            checkPermission(
                Manifest.permission.ACCESS_NETWORK_STATE,
                required = true
            )
        )

        // API 33+: NEARBY_WIFI_DEVICES replaces location for WiFi discovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results.add(
                checkPermission(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    required = true
                )
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-Android 12: location required for WiFi discovery
            results.add(
                checkPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    required = true
                )
            )
        }

        return results
    }

    /**
     * Check if ALL required permissions are granted.
     * @return true if testing can proceed, false if permissions are missing
     */
    fun areAllPermissionsGranted(): Boolean {
        return checkTestPermissions().all { it.granted }
    }

    /**
     * Get a summary of permission status.
     * @return Human-readable summary string
     */
    fun getPermissionSummary(): String {
        val results = checkTestPermissions()
        val granted = results.count { it.granted }
        val total = results.size
        val missing = results.filterNot { it.granted }.map { it.permission.substringAfterLast('.') }

        return if (granted == total) {
            "All $total permissions granted"
        } else {
            "Missing: ${missing.joinToString(", ")}"
        }
    }

    private fun checkPermission(permission: String, required: Boolean): PermissionResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionResult(
            granted = granted,
            permission = permission,
            required = required
        )
    }
}
