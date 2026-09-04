package com.p2p.meshify.core.common.preflight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [WifiPermissionChecker].
 *
 * Two paths are exercised:
 * - [WifiPermissionChecker.checkTestPermissions] — branch on SDK level
 *   (NEARBY_WIFI_DEVICES on API 33+, ACCESS_FINE_LOCATION on API < 31, nothing
 *   extra on API 31..32).
 * - [WifiPermissionChecker.areAllPermissionsGranted] /
 *   [WifiPermissionChecker.getPermissionSummary] — driven through a mockk
 *   Context that returns PERMISSION_GRANTED for granted perms and
 *   PERMISSION_DENIED otherwise.
 *
 * Why mockk and not Robolectric's permission shadow?
 * `ContextCompat.checkSelfPermission` (core-1.18.0+) on API 23+ calls the
 * **3-arg** `Context.checkPermission(perm, pid, uid)`. Robolectric 4.16.1 only
 * shadows the 2-arg `PackageManager.checkPermission(String, String)`, so
 * seeding `ShadowApplicationPackageManager.packageInfos` does NOT affect the
 * path `WifiPermissionChecker` actually uses. Mocking the 3-arg method on
 * Context gives a deterministic, version-independent way to express "these
 * perms are granted" without an emulator.
 *
 * The pure-shape assertions (permission list, required flag) still use the
 * real Robolectric `ApplicationProvider` context because they don't depend on
 * the permission grant state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WifiPermissionCheckerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Builds a mockk Context whose `checkPermission(perm, pid, uid)` returns
     * PERMISSION_GRANTED for any perm listed in [grantedPerms] and
     * PERMISSION_DENIED otherwise. This is the method
     * `ContextCompat.checkSelfPermission` invokes on API 23+.
     */
    private fun contextGranting(vararg grantedPerms: String): Context {
        val granted = grantedPerms.toSet()
        val mock = mockk<Context>()
        every { mock.checkPermission(any<String>(), any<Int>(), any<Int>()) } answers {
            val perm = firstArg<String>()
            if (perm in granted) PackageManager.PERMISSION_GRANTED
            else PackageManager.PERMISSION_DENIED
        }
        return mock
    }

    // ---- API 33+ branch: NEARBY_WIFI_DEVICES ----

    @Test
    fun `checkTestPermissions on API 33 includes NEARBY_WIFI_DEVICES`() {
        // Config(sdk = 33) on this class — sanity check.
        assertEquals(Build.VERSION_CODES.TIRAMISU, Build.VERSION.SDK_INT)

        val checker = WifiPermissionChecker(context)
        val results = checker.checkTestPermissions()
        val permissions = results.map { it.permission }

        assertTrue(
            "ACCESS_WIFI_STATE must always be required",
            permissions.contains(Manifest.permission.ACCESS_WIFI_STATE)
        )
        assertTrue(
            "ACCESS_NETWORK_STATE must always be required",
            permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE)
        )
        assertTrue(
            "API 33+ must check NEARBY_WIFI_DEVICES",
            permissions.contains(Manifest.permission.NEARBY_WIFI_DEVICES)
        )
        // And every result should be marked required=true.
        assertTrue("all results must be marked required", results.all { it.required })
    }

    @Test
    fun `areAllPermissionsGranted returns false when nothing is granted`() {
        // Mocked context grants nothing.
        val checker = WifiPermissionChecker(contextGranting())
        assertFalse(checker.areAllPermissionsGranted())
    }

    @Test
    fun `areAllPermissionsGranted returns true once every required perm is granted`() {
        val ctx = contextGranting(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
        val checker = WifiPermissionChecker(ctx)
        assertTrue(checker.areAllPermissionsGranted())
    }

    @Test
    fun `getPermissionSummary — all granted vs missing path`() {
        // Nothing granted → summary lists the missing permissions' short names.
        val missing = WifiPermissionChecker(contextGranting()).getPermissionSummary()
        assertTrue(
            "expected 'Missing:' prefix, got: $missing",
            missing.startsWith("Missing:")
        )
        assertTrue(missing.contains("ACCESS_WIFI_STATE"))
        assertTrue(missing.contains("ACCESS_NETWORK_STATE"))
        assertTrue(missing.contains("NEARBY_WIFI_DEVICES"))

        // Grant everything → summary flips to "All N permissions granted".
        val ok = WifiPermissionChecker(
            contextGranting(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        ).getPermissionSummary()
        assertEquals("All 3 permissions granted", ok)
    }

    // ---- PermissionResult shape ----

    @Test
    fun `PermissionResult — required flag is propagated`() {
        val checker = WifiPermissionChecker(contextGranting())
        val wifiStateResult = checker.checkTestPermissions()
            .first { it.permission == Manifest.permission.ACCESS_WIFI_STATE }
        assertNotNull(wifiStateResult)
        assertTrue(wifiStateResult.required)
    }

    @Test
    fun `PermissionResult — granted reflects current Context state`() {
        // Before grant: ACCESS_NETWORK_STATE is denied.
        val deniedCtx = contextGranting()
        val deniedResult = WifiPermissionChecker(deniedCtx).checkTestPermissions()
            .first { it.permission == Manifest.permission.ACCESS_NETWORK_STATE }
        assertFalse(deniedResult.granted)

        // After grant: re-querying reflects the new state.
        val grantedCtx = contextGranting(Manifest.permission.ACCESS_NETWORK_STATE)
        val grantedResult = WifiPermissionChecker(grantedCtx).checkTestPermissions()
            .first { it.permission == Manifest.permission.ACCESS_NETWORK_STATE }
        assertTrue(grantedResult.granted)
    }
}

/**
 * API 31..32: no NEARBY_WIFI_DEVICES (introduced in 33) and no
 * ACCESS_FINE_LOCATION (legacy WiFi discovery path is gated to API < 31).
 * Only the always-required ACCESS_*_STATE permissions are checked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [32])
class WifiPermissionCheckerTestApi32 {

    @Test
    fun `API 32 — checkTestPermissions does not include NEARBY_WIFI_DEVICES or LOCATION`() {
        assertEquals(Build.VERSION_CODES.S_V2, Build.VERSION.SDK_INT)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val permissions = WifiPermissionChecker(ctx).checkTestPermissions().map { it.permission }
        assertTrue(permissions.contains(Manifest.permission.ACCESS_WIFI_STATE))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE))
        assertFalse(
            "API < 33 must not include NEARBY_WIFI_DEVICES",
            permissions.contains(Manifest.permission.NEARBY_WIFI_DEVICES)
        )
        assertFalse(
            "API 31..32 must not include ACCESS_FINE_LOCATION",
            permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }
}

/**
 * API < 31: NEARBY_WIFI_DEVICES does not exist; legacy WiFi discovery uses
 * ACCESS_FINE_LOCATION, so the checker must include it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class WifiPermissionCheckerTestApi30 {

    @Test
    fun `API 30 — checkTestPermissions includes ACCESS_FINE_LOCATION`() {
        assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val permissions = WifiPermissionChecker(ctx).checkTestPermissions().map { it.permission }
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(
            "API < 33 must not include NEARBY_WIFI_DEVICES",
            permissions.contains(Manifest.permission.NEARBY_WIFI_DEVICES)
        )
    }
}
