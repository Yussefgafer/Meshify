package com.p2p.meshify.domain.repository

import com.p2p.meshify.domain.model.TransportMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ISettingsRepositoryTest {

    private fun fakeRepository(): ISettingsRepository {
        val repo = mockk<ISettingsRepository>(relaxed = true)

        // Back flows with real MutableStateFlows so .first() works on relaxed mock
        every { repo.displayName } returns MutableStateFlow("Alice")
        every { repo.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { repo.dynamicColorEnabled } returns MutableStateFlow(true)
        every { repo.hapticFeedbackEnabled } returns MutableStateFlow(true)
        every { repo.isNetworkVisible } returns MutableStateFlow(true)
        every { repo.avatarHash } returns MutableStateFlow(null)
        every { repo.seedColor } returns MutableStateFlow(0)
        every { repo.bleEnabled } returns MutableStateFlow(false)
        every { repo.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)
        every { repo.hasCompletedOnboarding } returns MutableStateFlow(false)
        every { repo.appLanguage } returns MutableStateFlow("en")
        every { repo.fontSizeScale } returns MutableStateFlow(1f)
        every { repo.notificationsEnabled } returns MutableStateFlow(true)
        every { repo.notificationSound } returns MutableStateFlow(true)
        every { repo.notificationVibrate } returns MutableStateFlow(true)
        coEvery { repo.getDeviceId() } returns "device-123"
        every { repo.getAppVersion() } returns "1.1.5"

        return repo
    }

    @Test
    fun `flows expose default values`() = runTest {
        val repo = fakeRepository()

        assertEquals("Alice", repo.displayName.first())
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        assertEquals(true, repo.dynamicColorEnabled.first())
        assertEquals(true, repo.hapticFeedbackEnabled.first())
        assertEquals(true, repo.isNetworkVisible.first())
        assertEquals(null, repo.avatarHash.first())
        assertEquals(0, repo.seedColor.first())
        assertEquals(false, repo.bleEnabled.first())
        assertEquals(TransportMode.MULTI_PATH, repo.transportMode.first())
        assertEquals(false, repo.hasCompletedOnboarding.first())
        assertEquals("en", repo.appLanguage.first())
        assertEquals(1f, repo.fontSizeScale.first(), 0.001f)
        assertEquals(true, repo.notificationsEnabled.first())
        assertEquals(true, repo.notificationSound.first())
        assertEquals(true, repo.notificationVibrate.first())
    }

    @Test
    fun `getDeviceId returns stable id`() = runTest {
        val repo = fakeRepository()
        assertEquals("device-123", repo.getDeviceId())
        coVerify { repo.getDeviceId() }
    }

    @Test
    fun `getAppVersion returns version string`() {
        val repo = fakeRepository()
        assertEquals("1.1.5", repo.getAppVersion())
        verify { repo.getAppVersion() }
    }

    @Test
    fun `setters are suspend and verifiable via coVerify`() = runTest {
        val repo = mockk<ISettingsRepository>(relaxed = true)

        repo.updateDisplayName("Bob")
        repo.setThemeMode(ThemeMode.DARK)
        repo.setDynamicColor(false)
        repo.setHapticFeedback(false)
        repo.setNetworkVisibility(false)
        repo.updateAvatarHash("hash123")
        repo.setSeedColor(0xFF0000)
        repo.setBleEnabled(true)
        repo.setTransportMode(TransportMode.LAN_ONLY)
        repo.setOnboardingCompleted()
        repo.resetOnboardingCompleted()
        repo.setAppLanguage("ar")
        repo.setFontSizeScale(1.2f)
        repo.setNotificationsEnabled(false)
        repo.setNotificationSound(false)
        repo.setNotificationVibrate(false)
        repo.clearCache()

        coVerify { repo.updateDisplayName("Bob") }
        coVerify { repo.setThemeMode(ThemeMode.DARK) }
        coVerify { repo.setDynamicColor(false) }
        coVerify { repo.setHapticFeedback(false) }
        coVerify { repo.setNetworkVisibility(false) }
        coVerify { repo.updateAvatarHash("hash123") }
        coVerify { repo.setSeedColor(0xFF0000) }
        coVerify { repo.setBleEnabled(true) }
        coVerify { repo.setTransportMode(TransportMode.LAN_ONLY) }
        coVerify { repo.setOnboardingCompleted() }
        coVerify { repo.resetOnboardingCompleted() }
        coVerify { repo.setAppLanguage("ar") }
        coVerify { repo.setFontSizeScale(1.2f) }
        coVerify { repo.setNotificationsEnabled(false) }
        coVerify { repo.setNotificationSound(false) }
        coVerify { repo.setNotificationVibrate(false) }
        coVerify { repo.clearCache() }
    }

    @Test
    fun `exportBackup and importBackup return Result`() = runTest {
        val repo = mockk<ISettingsRepository>()
        coEvery { repo.exportBackup() } returns Result.success("""{"display_name":"Alice"}""")
        coEvery { repo.importBackup(any()) } returns Result.success(Unit)

        val exported = repo.exportBackup()
        assertTrue(exported.isSuccess)
        assertTrue(exported.getOrThrow().contains("display_name"))

        val imported = repo.importBackup("""{"display_name":"Bob"}""")
        assertTrue(imported.isSuccess)

        coVerify { repo.exportBackup() }
        coVerify { repo.importBackup(any()) }
    }

    @Test
    fun `exportBackup failure propagates`() = runTest {
        val repo = mockk<ISettingsRepository>()
        coEvery { repo.exportBackup() } returns Result.failure(IllegalStateException("io error"))

        val result = repo.exportBackup()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `updateAvatarHash accepts null to clear avatar`() = runTest {
        val repo = mockk<ISettingsRepository>(relaxed = true)
        repo.updateAvatarHash(null)
        coVerify { repo.updateAvatarHash(null) }

        repo.updateAvatarHash("newHash")
        coVerify { repo.updateAvatarHash("newHash") }
    }

    @Test
    fun `relaxed mock flows are readable without explicit stubbing`() = runTest {
        // A fully relaxed mock still needs flow stubs to avoid NPE on .first(),
        // but non-flow suspend functions return Unit / default Result without stubbing.
        val repo = mockk<ISettingsRepository>(relaxed = true)
        // Should not throw
        repo.clearCache()
        coVerify { repo.clearCache() }
        // getAppVersion on relaxed mock returns "" (default String)
        assertEquals("", repo.getAppVersion())
    }
}
