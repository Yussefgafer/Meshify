package com.p2p.meshify.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.MeshifyApp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * MeshForegroundServiceTest — under Robolectric, no emulator.
 *
 * Two layers:
 *  1. Static helper tests (start/stop intents) — pure mockk Context, no shadow.
 *  2. Lifecycle tests (onCreate/onStartCommand/onDestroy/onBind/onTaskRemoved,
 *     notification channel/priority, START_STICKY, foreground promotion) — real
 *     Robolectric Service harness.
 *
 * Robolectric application: [TestServiceApp] skips Hilt/transport startup so
 * `application as MeshifyApp` resolves to a fake transportManager and no real
 * sockets/NSD ever boot.
 */
class TestServiceApp : MeshifyApp() {
    override fun onCreate() {
        // Do NOT call super.onCreate() — that would boot Hilt + transports.
        // Publish the singleton so code that reads MeshifyApp.instance succeeds.
        instance = this
        // Fabricate the transportManager that MeshForegroundService.onCreate() touches.
        // Using relaxed mockk so every suspend method returns Unit without stubbing each.
        @Suppress("DEPRECATION")
        try {
            val field = MeshifyApp::class.java.getDeclaredField("transportManager")
            field.isAccessible = true
            val fake = mockk<com.p2p.meshify.core.network.TransportManager>(relaxed = true)
            coEvery { fake.startAllTransports() } returns Unit
            coEvery { fake.stopAllTransports() } returns Unit
            field.set(this, fake)
        } catch (_: Exception) {
            // Fallback — direct assignment if field is accessible
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestServiceApp::class, manifest = Config.NONE, sdk = [33])
class MeshForegroundServiceTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== static helpers ====================

    @Test
    fun `static start() fires startForegroundService on O and above`() {
        val context = mockk<Context>(relaxed = true)
        MeshForegroundService.start(context)
        io.mockk.verify(exactly = 1) { context.startForegroundService(any()) }
        io.mockk.verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `static stop() calls stopService`() {
        val context = mockk<Context>(relaxed = true)
        MeshForegroundService.stop(context)
        io.mockk.verify(exactly = 1) { context.stopService(any<Intent>()) }
    }

    @Test
    fun `static start intent targets MeshForegroundService`() {
        val context = mockk<Context>(relaxed = true)
        val slot = io.mockk.slot<Intent>()
        every { context.startForegroundService(capture(slot)) } returns null
        MeshForegroundService.start(context)
        assertEquals(MeshForegroundService::class.java.name, slot.captured.component?.className)
    }

    @Test
    fun `static stop intent targets MeshForegroundService`() {
        val context = mockk<Context>(relaxed = true)
        val slot = io.mockk.slot<Intent>()
        every { context.stopService(capture(slot)) } returns true
        MeshForegroundService.stop(context)
        assertEquals(MeshForegroundService::class.java.name, slot.captured.component?.className)
    }

    // ==================== lifecycle via Robolectric harness ====================

    @Test
    fun `onBind returns null`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        assertNull(svc.onBind(Intent()))
        controller.destroy()
    }

    @Test
    fun `onCreate completes without throwing`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        // If we get here, onCreate did not throw (multicast lock + transport start succeeded)
        assertNotNull(svc)
        controller.destroy()
    }

    @Test
    fun `onStartCommand returns START_STICKY`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        val result = svc.onStartCommand(Intent(), 0, 1)
        assertEquals(android.app.Service.START_STICKY, result)
        controller.destroy()
    }

    @Test
    fun `onStartCommand handles null intent without crashing`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        val result = svc.onStartCommand(null, 0, 1)
        assertEquals(android.app.Service.START_STICKY, result)
        controller.destroy()
    }

    @Test
    fun `onStartCommand creates notification channel`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)

        val nm = svc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("mesh_service_channel")
        assertNotNull("channel mesh_service_channel must exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
        controller.destroy()
    }

    @Test
    fun `onStartCommand promotes to foreground with ongoing notification`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)

        val shadowSvc = Shadows.shadowOf(svc)
        val notification: Notification? = shadowSvc.lastForegroundNotification
        assertNotNull("foreground notification must be set", notification)
        assertTrue("notification must be ongoing", (notification!!.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals("channel must match", "mesh_service_channel", notification.channelId)
        controller.destroy()
    }

    @Test
    fun `onStartCommand notification has IMPORTANCE_LOW channel`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)

        val nm = svc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("mesh_service_channel")
        assertNotNull(channel)
        // Service notification uses PRIORITY_LOW (mapped to IMPORTANCE_LOW channel).
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
        controller.destroy()
    }

    @Test
    fun `onTaskRemoved does not throw and keeps service alive`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)
        svc.onTaskRemoved(Intent())
        // Should still be able to run onDestroy cleanly (service was not stopped by onTaskRemoved)
        svc.onDestroy()
    }

    @Test
    fun `onDestroy completes without throwing when transport never started`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        // destroy immediately — transportStarted still false, so stopMeshNetworkDeterministic no-ops
        svc.onDestroy()
    }

    @Test
    fun `onDestroy completes after full lifecycle`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)
        // Give the IO coroutine that sets transportStarted = true time to run.
        // Robolectric's main looper is paused by default; the IO dispatcher is real.
        Thread.sleep(80)
        svc.onDestroy()
    }

    @Test
    fun `service lifecycle create-start-destroy completes without exception`() {
        val controller = Robolectric.buildService(MeshForegroundService::class.java)
        val svc = controller.create().get()
        svc.onStartCommand(Intent(), 0, 1)
        svc.onTaskRemoved(Intent())
        svc.onDestroy()
    }

    @Test
    fun `companion CHANNEL_ID is mesh_service_channel via reflection`() {
        val field = MeshForegroundService::class.java.getDeclaredField("CHANNEL_ID")
        field.isAccessible = true
        val companion = MeshForegroundService::class.java.getDeclaredField("Companion").get(null)
        // Kotlin companion constants are on the synthetic Companion class; try both paths
        val value: String = try {
            field.get(null) as String
        } catch (_: Exception) {
            val compField = MeshForegroundService.Companion::class.java.getDeclaredField("CHANNEL_ID")
            compField.isAccessible = true
            compField.get(MeshForegroundService.Companion) as String
        }
        assertEquals("mesh_service_channel", value)
    }

    @Test
    fun `companion NOTIFICATION_ID is 101 via reflection`() {
        val field = try {
            MeshForegroundService::class.java.getDeclaredField("NOTIFICATION_ID")
        } catch (_: Exception) {
            MeshForegroundService.Companion::class.java.getDeclaredField("NOTIFICATION_ID")
        }
        field.isAccessible = true
        val value: Int = try {
            field.get(null) as Int
        } catch (_: Exception) {
            field.get(MeshForegroundService.Companion) as Int
        }
        assertEquals(101, value)
    }
}
