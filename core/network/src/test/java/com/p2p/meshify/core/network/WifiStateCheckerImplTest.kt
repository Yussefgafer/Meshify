package com.p2p.meshify.core.network

import android.content.Context
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiStateCheckerImplTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun mockContext(wifiEnabled: Boolean): Context {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        every { wifiManager.isWifiEnabled } returns wifiEnabled
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns appContext
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        return context
    }

    @Test
    fun isWifiEnabled_whenEnabled_returnsTrue() {
        val checker = WifiStateCheckerImpl(mockContext(wifiEnabled = true))
        assertTrue(checker.isWifiEnabled)
    }

    @Test
    fun isWifiEnabled_whenDisabled_returnsFalse() {
        val checker = WifiStateCheckerImpl(mockContext(wifiEnabled = false))
        assertFalse(checker.isWifiEnabled)
    }

    @Test
    fun isWifiEnabled_checksWifiManagerEachTime() {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        every { wifiManager.isWifiEnabled } returnsMany listOf(true, false, true)
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns appContext

        val checker = WifiStateCheckerImpl(context)
        assertTrue(checker.isWifiEnabled)
        assertFalse(checker.isWifiEnabled)
        assertTrue(checker.isWifiEnabled)
    }
}
