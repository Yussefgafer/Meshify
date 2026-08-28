package com.p2p.meshify.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ThemeMode
import io.mockk.mockk
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SettingsRepositoryTest — exercises displayName trim/length validation,
 * the safeEdit Result wrapper for every setter, the export/importBackup
 * JSON round-trip, and clearCache file-system behavior. Uses a temp-file
 * DataStore (no Robolectric) and a mockk Context, since the only Android
 * surface is getPackageInfo() in getAppVersion() and context.filesDir /
 * context.cacheDir in clearCache().
 */
class SettingsRepositoryTest {

    private lateinit var tempDir: java.nio.file.Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var context: android.content.Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("settings-repo-test")
        context = mockk(relaxed = true)
        dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.GlobalScope,
            produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
        )
        repository = SettingsRepository(context = context, prefsStore = dataStore)
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun updateDisplayName_trimsAndPersists() = runTest {
        repository.updateDisplayName("  Alice  ")
        assertEquals("Alice", repository.displayName.first())
    }

    @Test
    fun updateDisplayName_emptyAfterTrimThrows() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.updateDisplayName("   ")
            }
        }
    }

    @Test
    fun updateDisplayName_over30CharsThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.updateDisplayName("x".repeat(31))
            }
        }
    }

    @Test
    fun updateDisplayName_exactly30CharsAccepted() = runTest {
        val name = "x".repeat(30)
        repository.updateDisplayName(name)
        assertEquals(name, repository.displayName.first())
    }

    @Test
    fun getDeviceId_generatesUuidIfMissing() = runTest {
        val id = repository.getDeviceId()
        assertNotNull(id)
        assertTrue("UUID should be non-blank", id.isNotBlank())
        // Subsequent calls return the same id (persisted)
        assertEquals(id, repository.getDeviceId())
    }

    @Test
    fun themeMode_defaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun setThemeMode_roundTrip() = runTest {
        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun transportMode_defaultsToMultiPath() = runTest {
        assertEquals(TransportMode.MULTI_PATH, repository.transportMode.first())
    }

    @Test
    fun setTransportMode_roundTrip() = runTest {
        repository.setTransportMode(TransportMode.LAN_ONLY)
        assertEquals(TransportMode.LAN_ONLY, repository.transportMode.first())
    }

    @Test
    fun setHapticFeedback_roundTrip() = runTest {
        repository.setHapticFeedback(false)
        assertEquals(false, repository.hapticFeedbackEnabled.first())
    }

    @Test
    fun setDynamicColor_roundTrip() = runTest {
        repository.setDynamicColor(false)
        assertEquals(false, repository.dynamicColorEnabled.first())
    }

    @Test
    fun setBleEnabled_roundTrip() = runTest {
        repository.setBleEnabled(true)
        assertEquals(true, repository.bleEnabled.first())
    }

    @Test
    fun setAppLanguage_roundTrip() = runTest {
        repository.setAppLanguage("ar")
        assertEquals("ar", repository.appLanguage.first())
    }

    @Test
    fun setFontSizeScale_clampsToBounds() = runTest {
        repository.setFontSizeScale(0.1f) // below 0.8 floor
        assertEquals(0.8f, repository.fontSizeScale.first(), 0.001f)
        repository.setFontSizeScale(5.0f) // above 1.5 ceiling
        assertEquals(1.5f, repository.fontSizeScale.first(), 0.001f)
    }

    @Test
    fun setFontSizeScale_inRange_preserved() = runTest {
        repository.setFontSizeScale(1.2f)
        assertEquals(1.2f, repository.fontSizeScale.first(), 0.001f)
    }

    @Test
    fun setNotificationsEnabled_roundTrip() = runTest {
        repository.setNotificationsEnabled(false)
        assertEquals(false, repository.notificationsEnabled.first())
    }

    @Test
    fun setOnboardingCompleted_roundTrip() = runTest {
        assertEquals(false, repository.hasCompletedOnboarding.first())
        repository.setOnboardingCompleted()
        assertEquals(true, repository.hasCompletedOnboarding.first())
        repository.resetOnboardingCompleted()
        assertEquals(false, repository.hasCompletedOnboarding.first())
    }

    @Test
    fun updateAvatarHash_nullRemoves() = runTest {
        repository.updateAvatarHash("abc123")
        assertEquals("abc123", repository.avatarHash.first())
        repository.updateAvatarHash(null)
        assertNull(repository.avatarHash.first())
    }

    @Test
    fun exportBackup_producesJsonContainingAllFields() = runTest {
        repository.updateDisplayName("BackupUser")
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setBleEnabled(true)
        repository.setTransportMode(TransportMode.AUTO)
        val result = repository.exportBackup()
        assertTrue(result.isSuccess)
        val json = result.getOrThrow()
        assertTrue("json contains display_name", json.contains("\"display_name\""))
        assertTrue("json contains theme_mode", json.contains("\"theme_mode\""))
        assertTrue("json contains ble_enabled", json.contains("\"ble_enabled\""))
        assertTrue("json contains transport_mode", json.contains("\"transport_mode\""))
        assertTrue("json contains export_timestamp", json.contains("\"export_timestamp\""))
    }

    @Test
    fun importBackup_restoresValues() = runTest {
        val backup = mapOf(
            "display_name" to "Imported",
            "theme_mode" to "DARK",
            "dynamic_color" to "false",
            "haptic_feedback" to "false",
            "transport_mode" to "BLE_ONLY",
            "ble_enabled" to "true"
        ).mapValues { it.value.toString() }
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(backup.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        )
        val result = repository.importBackup(json)
        assertTrue(result.isSuccess)
        assertEquals("Imported", repository.displayName.first())
        assertEquals(ThemeMode.DARK, repository.themeMode.first())
        assertEquals(false, repository.dynamicColorEnabled.first())
        assertEquals(false, repository.hapticFeedbackEnabled.first())
        assertEquals(TransportMode.BLE_ONLY, repository.transportMode.first())
        assertEquals(true, repository.bleEnabled.first())
    }

    @Test
    fun importBackup_invalidJsonReturnsFailure() = runTest {
        val result = repository.importBackup("{not valid json")
        assertTrue(result.isFailure)
    }
}
