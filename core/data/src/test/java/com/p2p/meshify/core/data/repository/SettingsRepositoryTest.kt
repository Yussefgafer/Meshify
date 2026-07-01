package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/**
 * Unit tests for SettingsRepository.
 *
 * Uses a unique DataStore file per test to ensure isolation.
 * Robolectric required for Android Context + DataStore.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository
    private lateinit var testFile: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uniqueName = "settings_test_${UUID.randomUUID()}"
        testFile = File(context.filesDir, "datastore/$uniqueName.preferences_pb")
        testFile.parentFile?.mkdirs()
        val testDataStore = PreferenceDataStoreFactory.create {
            testFile
        }
        repository = SettingsRepository(context, testDataStore)
    }

    @After
    fun teardown() {
        testFile.delete()
    }

    // ============================================================================================
    // appLanguage TESTS
    // ============================================================================================

    @Test
    fun `appLanguage defaults to en`() = runTest {
        val lang = repository.appLanguage.first()
        assertEquals("en", lang)
    }

    @Test
    fun `appLanguage emits updated value after set`() = runTest {
        repository.setAppLanguage("ar")
        val lang = repository.appLanguage.first()
        assertEquals("ar", lang)
    }

    @Test
    fun `appLanguage handles multiple updates`() = runTest {
        repository.setAppLanguage("fr")
        assertEquals("fr", repository.appLanguage.first())

        repository.setAppLanguage("de")
        assertEquals("de", repository.appLanguage.first())
    }

    // ============================================================================================
    // themeMode TESTS
    // ============================================================================================

    @Test
    fun `themeMode defaults to SYSTEM`() = runTest {
        val mode = repository.themeMode.first()
        assertEquals(ThemeMode.SYSTEM, mode)
    }

    @Test
    fun `themeMode updates correctly`() = runTest {
        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.themeMode.first())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    // ============================================================================================
    // displayName TESTS
    // ============================================================================================

    @Test
    fun `displayName defaults to User_ fallback when not set`() = runTest {
        val name = repository.displayName.first()
        assertTrue(name.startsWith("User_"))
    }

    @Test
    fun `displayName returns set value`() = runTest {
        repository.updateDisplayName("Alice")
        val name = repository.displayName.first()
        assertEquals("Alice", name)
    }

    @Test
    fun `updateDisplayName throws on empty name`() = runTest {
        try {
            repository.updateDisplayName("")
            assertFalse("Expected exception for empty name", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 1 character") == true)
        }
    }

    @Test
    fun `updateDisplayName throws on long name`() = runTest {
        try {
            repository.updateDisplayName("A".repeat(31))
            assertFalse("Expected exception for long name", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("30 characters") == true)
        }
    }

    @Test
    fun `updateDisplayName trims whitespace`() = runTest {
        repository.updateDisplayName("  Bob  ")
        val name = repository.displayName.first()
        assertEquals("Bob", name)
    }

    // ============================================================================================
    // getDeviceId() TESTS
    // ============================================================================================

    @Test
    fun `getDeviceId generates consistent UUID`() = runTest {
        val firstId = repository.getDeviceId()
        val secondId = repository.getDeviceId()

        // Same session should return the same ID
        assertEquals(firstId, secondId)
        assertTrue(firstId.isNotBlank())
    }

    @Test
    fun `getDeviceId returns valid UUID format`() = runTest {
        val deviceId = repository.getDeviceId()
        // UUIDs have the format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(deviceId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    // ============================================================================================
    // dynamicColor TESTS
    // ============================================================================================

    @Test
    fun `dynamicColor defaults to true`() = runTest {
        assertTrue(repository.dynamicColorEnabled.first())
    }

    @Test
    fun `dynamicColor updates correctly`() = runTest {
        repository.setDynamicColor(false)
        assertFalse(repository.dynamicColorEnabled.first())

        repository.setDynamicColor(true)
        assertTrue(repository.dynamicColorEnabled.first())
    }

    // ============================================================================================
    // hapticFeedback TESTS
    // ============================================================================================

    @Test
    fun `hapticFeedback defaults to true`() = runTest {
        assertTrue(repository.hapticFeedbackEnabled.first())
    }

    @Test
    fun `hapticFeedback updates correctly`() = runTest {
        repository.setHapticFeedback(false)
        assertFalse(repository.hapticFeedbackEnabled.first())
    }

    // ============================================================================================
    // isNetworkVisible TESTS
    // ============================================================================================

    @Test
    fun `isNetworkVisible defaults to true`() = runTest {
        assertTrue(repository.isNetworkVisible.first())
    }

    @Test
    fun `isNetworkVisible updates correctly`() = runTest {
        repository.setNetworkVisibility(false)
        assertFalse(repository.isNetworkVisible.first())
    }

    // ============================================================================================
    // avatarHash TESTS
    // ============================================================================================

    @Test
    fun `avatarHash defaults to null`() = runTest {
        val hash = repository.avatarHash.first()
        assertNull(hash)
    }

    @Test
    fun `avatarHash updates correctly`() = runTest {
        repository.updateAvatarHash("abc123")
        assertEquals("abc123", repository.avatarHash.first())

        repository.updateAvatarHash(null)
        assertNull(repository.avatarHash.first())
    }

    // ============================================================================================
    // MD3E Settings TESTS
    // ============================================================================================

    @Test
    fun `shapeStyle defaults to CIRCLE`() = runTest {
        assertEquals(ShapeStyle.CIRCLE, repository.shapeStyle.first())
    }

    @Test
    fun `shapeStyle updates correctly`() = runTest {
        repository.setShapeStyle(ShapeStyle.BLOB)
        assertEquals(ShapeStyle.BLOB, repository.shapeStyle.first())
    }

    @Test
    fun `motionPreset defaults to STANDARD`() = runTest {
        assertEquals(MotionPreset.STANDARD, repository.motionPreset.first())
    }

    @Test
    fun `motionPreset updates correctly`() = runTest {
        repository.setMotionPreset(MotionPreset.GENTLE)
        assertEquals(MotionPreset.GENTLE, repository.motionPreset.first())
    }

    @Test
    fun `motionScale defaults to 1_0f`() = runTest {
        assertEquals(1.0f, repository.motionScale.first(), 0.001f)
    }

    @Test
    fun `motionScale clamps to valid range`() = runTest {
        repository.setMotionScale(3.0f)
        assertEquals(2.0f, repository.motionScale.first(), 0.001f)

        repository.setMotionScale(0.1f)
        assertEquals(0.5f, repository.motionScale.first(), 0.001f)
    }

    @Test
    fun `fontFamilyPreset defaults to ROBOTO`() = runTest {
        assertEquals(FontFamilyPreset.ROBOTO, repository.fontFamilyPreset.first())
    }

    @Test
    fun `fontFamilyPreset updates correctly`() = runTest {
        repository.setFontFamilyPreset(FontFamilyPreset.POPPINS)
        assertEquals(FontFamilyPreset.POPPINS, repository.fontFamilyPreset.first())
    }

    @Test
    fun `customFontUri defaults to null`() = runTest {
        assertNull(repository.customFontUri.first())
    }

    @Test
    fun `customFontUri updates correctly`() = runTest {
        repository.setCustomFontUri("content://fonts/custom.ttf")
        assertEquals("content://fonts/custom.ttf", repository.customFontUri.first())

        repository.setCustomFontUri(null)
        assertNull(repository.customFontUri.first())
    }

    @Test
    fun `bubbleStyle defaults to ROUNDED`() = runTest {
        assertEquals(BubbleStyle.ROUNDED, repository.bubbleStyle.first())
    }

    @Test
    fun `bubbleStyle updates correctly`() = runTest {
        repository.setBubbleStyle(BubbleStyle.TAILED)
        assertEquals(BubbleStyle.TAILED, repository.bubbleStyle.first())
    }

    @Test
    fun `visualDensity defaults to 1_0f`() = runTest {
        assertEquals(1.0f, repository.visualDensity.first(), 0.001f)
    }

    @Test
    fun `visualDensity clamps to valid range`() = runTest {
        repository.setVisualDensity(2.0f)
        assertEquals(1.5f, repository.visualDensity.first(), 0.001f)

        repository.setVisualDensity(0.5f)
        assertEquals(0.8f, repository.visualDensity.first(), 0.001f)
    }

    @Test
    fun `seedColor defaults to teal`() = runTest {
        assertEquals(0xFF006D68.toInt(), repository.seedColor.first())
    }

    @Test
    fun `seedColor updates correctly`() = runTest {
        repository.setSeedColor(0xFFFF0000.toInt())
        assertEquals(0xFFFF0000.toInt(), repository.seedColor.first())
    }

    // ============================================================================================
    // BLE / Transport TESTS
    // ============================================================================================

    @Test
    fun `bleEnabled defaults to false`() = runTest {
        assertFalse(repository.bleEnabled.first())
    }

    @Test
    fun `bleEnabled updates correctly`() = runTest {
        repository.setBleEnabled(true)
        assertTrue(repository.bleEnabled.first())
    }

    @Test
    fun `transportMode defaults to MULTI_PATH`() = runTest {
        assertEquals(TransportMode.MULTI_PATH, repository.transportMode.first())
    }

    @Test
    fun `transportMode updates correctly`() = runTest {
        repository.setTransportMode(TransportMode.LAN_ONLY)
        assertEquals(TransportMode.LAN_ONLY, repository.transportMode.first())
    }

    // ============================================================================================
    // Onboarding TESTS
    // ============================================================================================

    @Test
    fun `onboarding defaults to not completed`() = runTest {
        assertFalse(repository.hasCompletedOnboarding.first())
    }

    @Test
    fun `setOnboardingCompleted stores true`() = runTest {
        repository.setOnboardingCompleted()
        assertTrue(repository.hasCompletedOnboarding.first())
    }

    @Test
    fun `resetOnboardingCompleted reverts to false`() = runTest {
        repository.setOnboardingCompleted()
        assertTrue(repository.hasCompletedOnboarding.first())

        repository.resetOnboardingCompleted()
        assertFalse(repository.hasCompletedOnboarding.first())
    }

    // ============================================================================================
    // Notification Settings TESTS
    // ============================================================================================

    @Test
    fun `notificationsEnabled defaults to true`() = runTest {
        assertTrue(repository.notificationsEnabled.first())
    }

    @Test
    fun `notificationsEnabled updates correctly`() = runTest {
        repository.setNotificationsEnabled(false)
        assertFalse(repository.notificationsEnabled.first())
    }

    @Test
    fun `notificationSound defaults to true`() = runTest {
        assertTrue(repository.notificationSound.first())
    }

    @Test
    fun `notificationSound updates correctly`() = runTest {
        repository.setNotificationSound(false)
        assertFalse(repository.notificationSound.first())
    }

    @Test
    fun `notificationVibrate defaults to true`() = runTest {
        assertTrue(repository.notificationVibrate.first())
    }

    @Test
    fun `notificationVibrate updates correctly`() = runTest {
        repository.setNotificationVibrate(false)
        assertFalse(repository.notificationVibrate.first())
    }

    // ============================================================================================
    // fontSizeScale TESTS
    // ============================================================================================

    @Test
    fun `fontSizeScale defaults to 1_0f`() = runTest {
        assertEquals(1.0f, repository.fontSizeScale.first(), 0.001f)
    }

    @Test
    fun `fontSizeScale clamps to valid range`() = runTest {
        repository.setFontSizeScale(2.0f)
        assertEquals(1.5f, repository.fontSizeScale.first(), 0.001f)

        repository.setFontSizeScale(0.5f)
        assertEquals(0.8f, repository.fontSizeScale.first(), 0.001f)
    }

    // ============================================================================================
    // getAppVersion() TESTS
    // ============================================================================================

    @Test
    fun `getAppVersion returns valid version string`() {
        val version = repository.getAppVersion()
        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }

    // ============================================================================================
    // exportBackup() / importBackup() TESTS
    // ============================================================================================

    @Test
    fun `exportBackup returns JSON with settings`() = runTest {
        // Given: set some values
        repository.updateDisplayName("ExportUser")
        repository.setAppLanguage("fr")
        repository.setThemeMode(ThemeMode.DARK)

        // When
        val result = repository.exportBackup()

        // Then
        assertTrue(result.isSuccess)
        val json = result.getOrNull()
        assertNotNull(json)
        assertTrue(json!!.contains("display_name"))
        assertTrue(json.contains("theme_mode"))
        assertTrue(json.contains("app_language"))
        assertTrue(json.contains("export_timestamp"))
    }

    @Test
    fun `importBackup restores settings`() = runTest {
        // Given: export settings
        repository.updateDisplayName("OriginalName")
        repository.setAppLanguage("en")

        val exportResult = repository.exportBackup()
        assertTrue(exportResult.isSuccess)
        val backupJson = exportResult.getOrNull()!!

        // Change settings
        repository.updateDisplayName("ChangedName")
        repository.setAppLanguage("de")

        // When: import original backup
        val importResult = repository.importBackup(backupJson)

        // Then: settings restored
        assertTrue(importResult.isSuccess)
        assertEquals("OriginalName", repository.displayName.first())
        assertEquals("en", repository.appLanguage.first())
    }

    @Test
    fun `importBackup handles invalid JSON`() = runTest {
        val result = repository.importBackup("not valid json")
        assertTrue(result.isFailure)
    }

    @Test
    fun `importBackup handles empty JSON object`() = runTest {
        val result = repository.importBackup("{}")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `exportBackup on fresh settings includes default values`() = runTest {
        val result = repository.exportBackup()
        assertTrue(result.isSuccess)
        val json = result.getOrNull()!!
        // Fresh settings should still have export_timestamp
        assertTrue(json.contains("export_timestamp"))
    }

    // ============================================================================================
    // clearCache() TESTS
    // ============================================================================================
    // clearCache deals with file system operations (avatar dirs, coil cache).
    // It should not throw even if directories don't exist.

    @Test
    fun `clearCache does not throw when directories missing`() = runTest {
        // Should complete without exception even if cache dirs don't exist
        repository.clearCache()
        // No assertion needed - just verify no exception
    }
}
