package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Data layer implementation of Settings Repository.
 * Extended for MD3E - Central Source of Truth for all design variables.
 */
class SettingsRepository(private val context: Context) : ISettingsRepository {

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_NETWORK_VISIBLE = booleanPreferencesKey("network_visible")
        val KEY_AVATAR_HASH = stringPreferencesKey("avatar_hash")

        // MD3E Settings Keys
        val KEY_SHAPE_STYLE = stringPreferencesKey("shape_style")
        val KEY_MOTION_PRESET = stringPreferencesKey("motion_preset")
        val KEY_MOTION_SCALE = floatPreferencesKey("motion_scale")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_CUSTOM_FONT_URI = stringPreferencesKey("custom_font_uri")
        val KEY_BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val KEY_VISUAL_DENSITY = floatPreferencesKey("visual_density")
        val KEY_SEED_COLOR = intPreferencesKey("seed_color")

        // BLE Transport Settings Keys
        val KEY_BLE_ENABLED = booleanPreferencesKey("ble_enabled")
        val KEY_TRANSPORT_MODE = stringPreferencesKey("transport_mode")

        // Onboarding Settings Keys
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // New Settings Keys
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val KEY_NOTIFICATION_VIBRATE = booleanPreferencesKey("notification_vibrate")
    }

    override val displayName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISPLAY_NAME] ?: "User_${preferences[KEY_DEVICE_ID]?.take(4) ?: "Unknown"}"
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[KEY_THEME_MODE] ?: "SYSTEM")
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_HAPTIC_FEEDBACK] ?: true
    }

    override val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLOR] ?: true
    }

    override val isNetworkVisible: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NETWORK_VISIBLE] ?: true
    }

    override val avatarHash: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_HASH]
    }

    // MD3E Settings Flows
    override val shapeStyle: Flow<ShapeStyle> = context.dataStore.data.map { preferences ->
        try {
            ShapeStyle.valueOf(preferences[KEY_SHAPE_STYLE] ?: "CIRCLE")
        } catch (e: Exception) {
            ShapeStyle.CIRCLE
        }
    }

    override val motionPreset: Flow<MotionPreset> = context.dataStore.data.map { preferences ->
        try {
            MotionPreset.valueOf(preferences[KEY_MOTION_PRESET] ?: "STANDARD")
        } catch (e: Exception) {
            MotionPreset.STANDARD
        }
    }

    override val motionScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_MOTION_SCALE] ?: 1.0f
    }

    override val fontFamilyPreset: Flow<FontFamilyPreset> = context.dataStore.data.map { preferences ->
        try {
            FontFamilyPreset.valueOf(preferences[KEY_FONT_FAMILY] ?: "ROBOTO")
        } catch (e: Exception) {
            FontFamilyPreset.ROBOTO
        }
    }

    override val customFontUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_FONT_URI]
    }

    override val bubbleStyle: Flow<BubbleStyle> = context.dataStore.data.map { preferences ->
        try {
            BubbleStyle.valueOf(preferences[KEY_BUBBLE_STYLE] ?: "ROUNDED")
        } catch (e: Exception) {
            BubbleStyle.ROUNDED
        }
    }

    override val visualDensity: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_VISUAL_DENSITY] ?: 1.0f
    }

    override val seedColor: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SEED_COLOR] ?: 0xFF006D68.toInt() // Default teal color
    }

    // BLE Transport Settings Flows
    override val bleEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLE_ENABLED] ?: false // Opt-in by default (battery saving)
    }

    override val transportMode: Flow<TransportMode> = context.dataStore.data.map { preferences ->
        try {
            TransportMode.valueOf(preferences[KEY_TRANSPORT_MODE] ?: "MULTI_PATH")
        } catch (e: Exception) {
            TransportMode.MULTI_PATH
        }
    }

    // Onboarding Flow
    override val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    // ✅ New Settings Flows
    override val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_APP_LANGUAGE] ?: "en" // Default English
    }

    override val fontSizeScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_FONT_SIZE_SCALE] ?: 1.0f
    }

    override val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    override val notificationSound: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_SOUND] ?: true
    }

    override val notificationVibrate: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_VIBRATE] ?: true
    }

    override suspend fun getDeviceId(): String {
        return try {
            val prefs = context.dataStore.data.map { it[KEY_DEVICE_ID] }.firstOrNull()
            if (prefs != null) return prefs
            val newId = UUID.randomUUID().toString()
            safeEdit { it[KEY_DEVICE_ID] = newId }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to save device ID", e)
            }
            newId
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    override suspend fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Display name must be at least 1 character" }
        require(trimmed.length <= 30) { "Display name must be 30 characters or less" }
        safeEdit { it[KEY_DISPLAY_NAME] = trimmed }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to update display name", e)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        safeEdit { it[KEY_THEME_MODE] = mode.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set theme mode", e)
        }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        safeEdit { it[KEY_HAPTIC_FEEDBACK] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set haptic feedback", e)
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        safeEdit { it[KEY_DYNAMIC_COLOR] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set dynamic color", e)
        }
    }

    override suspend fun setNetworkVisibility(visible: Boolean) {
        safeEdit { it[KEY_NETWORK_VISIBLE] = visible }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set network visibility", e)
        }
    }

    override suspend fun updateAvatarHash(hash: String?) {
        if (hash != null) {
            safeEdit { it[KEY_AVATAR_HASH] = hash }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to update avatar hash", e)
            }
        } else {
            safeEdit { it.remove(KEY_AVATAR_HASH) }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to remove avatar hash", e)
            }
        }
    }

    // MD3E Setting Mutators
    override suspend fun setShapeStyle(style: ShapeStyle) {
        safeEdit { it[KEY_SHAPE_STYLE] = style.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set shape style", e)
        }
    }

    override suspend fun setMotionPreset(preset: MotionPreset) {
        safeEdit { it[KEY_MOTION_PRESET] = preset.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set motion preset", e)
        }
    }

    override suspend fun setMotionScale(scale: Float) {
        safeEdit { it[KEY_MOTION_SCALE] = scale.coerceIn(0.5f, 2.0f) }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set motion scale", e)
        }
    }

    override suspend fun setFontFamilyPreset(family: FontFamilyPreset) {
        safeEdit { it[KEY_FONT_FAMILY] = family.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set font family", e)
        }
    }

    override suspend fun setCustomFontUri(uri: String?) {
        if (uri != null) {
            safeEdit { it[KEY_CUSTOM_FONT_URI] = uri }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to set custom font", e)
            }
        } else {
            safeEdit { it.remove(KEY_CUSTOM_FONT_URI) }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to clear custom font", e)
            }
        }
    }

    override suspend fun setBubbleStyle(style: BubbleStyle) {
        safeEdit { it[KEY_BUBBLE_STYLE] = style.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set bubble style", e)
        }
    }

    override suspend fun setVisualDensity(density: Float) {
        safeEdit { it[KEY_VISUAL_DENSITY] = density.coerceIn(0.8f, 1.5f) }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set visual density", e)
        }
    }

    override suspend fun setSeedColor(color: Int) {
        safeEdit { it[KEY_SEED_COLOR] = color }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set seed color", e)
        }
    }

    // BLE Transport Settings Mutators
    override suspend fun setBleEnabled(enabled: Boolean) {
        safeEdit { it[KEY_BLE_ENABLED] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set BLE enabled", e)
        }
    }

    override suspend fun setTransportMode(mode: TransportMode) {
        safeEdit { it[KEY_TRANSPORT_MODE] = mode.name }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set transport mode", e)
        }
    }

    // Onboarding Mutators
    override suspend fun setOnboardingCompleted() {
        safeEdit { it[KEY_ONBOARDING_COMPLETED] = true }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set onboarding completed", e)
        }
    }

    override suspend fun resetOnboardingCompleted() {
        safeEdit { it.remove(KEY_ONBOARDING_COMPLETED) }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to reset onboarding completed", e)
        }
    }

    // ✅ New Settings Mutators
    override suspend fun setAppLanguage(language: String) {
        safeEdit { it[KEY_APP_LANGUAGE] = language }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set app language", e)
        }
    }

    override suspend fun setFontSizeScale(scale: Float) {
        safeEdit { it[KEY_FONT_SIZE_SCALE] = scale.coerceIn(0.8f, 1.5f) }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set font size scale", e)
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        safeEdit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set notifications", e)
        }
    }

    override suspend fun setNotificationSound(enabled: Boolean) {
        safeEdit { it[KEY_NOTIFICATION_SOUND] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set notification sound", e)
        }
    }

    override suspend fun setNotificationVibrate(enabled: Boolean) {
        safeEdit { it[KEY_NOTIFICATION_VIBRATE] = enabled }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set notification vibrate", e)
        }
    }

    override suspend fun clearCache() {
        try {
            // Only clear avatar files that are not the current avatar
            val currentAvatarHash = context.dataStore.data.map { it[KEY_AVATAR_HASH] }.firstOrNull()
            val avatarsDir = java.io.File(context.filesDir, "avatars")
            if (avatarsDir.exists() && avatarsDir.isDirectory) {
                avatarsDir.listFiles()?.forEach { file ->
                    if (file.name != currentAvatarHash) {
                        file.delete()
                    }
                }
            }

            // Clear Coil image cache directory only
            val coilCacheDir = java.io.File(context.cacheDir, "image_manager_disk_cache")
            if (coilCacheDir.exists()) {
                coilCacheDir.deleteRecursively()
            }

            Logger.d("SettingsRepository -> Cache cleared successfully")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to clear cache", e)
            throw e
        }
    }

    override suspend fun exportBackup(): Result<String> {
        return try {
            // ✅ CODE-03: Added error handling for blocking .first() call
            // Prevents app freeze if DataStore is corrupted or locked
            val prefs = try {
                context.dataStore.data.first()
            } catch (e: Exception) {
                Logger.e("SettingsRepository -> Failed to read DataStore", e)
                return Result.failure(Exception("Failed to read preferences: ${e.message}", e))
            }
            
            val backupData = mapOf(
                // Core settings
                "display_name" to prefs[KEY_DISPLAY_NAME],
                "theme_mode" to prefs[KEY_THEME_MODE],
                "dynamic_color" to prefs[KEY_DYNAMIC_COLOR],
                "haptic_feedback" to prefs[KEY_HAPTIC_FEEDBACK],
                "network_visible" to prefs[KEY_NETWORK_VISIBLE],
                "avatar_hash" to prefs[KEY_AVATAR_HASH],
                // MD3E settings
                "shape_style" to prefs[KEY_SHAPE_STYLE],
                "motion_preset" to prefs[KEY_MOTION_PRESET],
                "motion_scale" to prefs[KEY_MOTION_SCALE]?.toString(),
                "font_family" to prefs[KEY_FONT_FAMILY],
                "custom_font_uri" to prefs[KEY_CUSTOM_FONT_URI],
                "bubble_style" to prefs[KEY_BUBBLE_STYLE],
                "visual_density" to prefs[KEY_VISUAL_DENSITY]?.toString(),
                "seed_color" to prefs[KEY_SEED_COLOR]?.toString(),
                // App settings
                "app_language" to prefs[KEY_APP_LANGUAGE],
                "font_size_scale" to prefs[KEY_FONT_SIZE_SCALE]?.toString(),
                "notifications_enabled" to prefs[KEY_NOTIFICATIONS_ENABLED],
                "notification_sound" to prefs[KEY_NOTIFICATION_SOUND],
                "notification_vibrate" to prefs[KEY_NOTIFICATION_VIBRATE],
                // BLE settings
                "ble_enabled" to prefs[KEY_BLE_ENABLED],
                "transport_mode" to prefs[KEY_TRANSPORT_MODE],
                // Metadata
                "export_timestamp" to System.currentTimeMillis().toString()
            )
            // filterValues removes all nulls, so mapValues safely receives non-null values
            .filterValues { it != null }.mapValues { it.value.toString() }
            val json = Json.encodeToString(backupData)
            Logger.d("SettingsRepository -> Backup exported successfully")
            Result.success(json)
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to export backup", e)
            Result.failure(e)
        }
    }

    override suspend fun importBackup(backupJson: String): Result<Unit> {
        return try {
            val backupData = Json.decodeFromString<Map<String, String>>(backupJson)
            val editResult = safeEdit { prefs ->
                // Core settings
                backupData["display_name"]?.let { prefs[KEY_DISPLAY_NAME] = it }
                backupData["theme_mode"]?.let { prefs[KEY_THEME_MODE] = it }
                backupData["dynamic_color"]?.let { prefs[KEY_DYNAMIC_COLOR] = it.toBoolean() }
                backupData["haptic_feedback"]?.let { prefs[KEY_HAPTIC_FEEDBACK] = it.toBoolean() }
                backupData["network_visible"]?.let { prefs[KEY_NETWORK_VISIBLE] = it.toBoolean() }
                backupData["avatar_hash"]?.let { prefs[KEY_AVATAR_HASH] = it }
                // MD3E settings
                backupData["shape_style"]?.let { prefs[KEY_SHAPE_STYLE] = it }
                backupData["motion_preset"]?.let { prefs[KEY_MOTION_PRESET] = it }
                backupData["motion_scale"]?.let { prefs[KEY_MOTION_SCALE] = it.toFloat() }
                backupData["font_family"]?.let { prefs[KEY_FONT_FAMILY] = it }
                backupData["custom_font_uri"]?.let { prefs[KEY_CUSTOM_FONT_URI] = it }
                backupData["bubble_style"]?.let { prefs[KEY_BUBBLE_STYLE] = it }
                backupData["visual_density"]?.let { prefs[KEY_VISUAL_DENSITY] = it.toFloat() }
                backupData["seed_color"]?.let { prefs[KEY_SEED_COLOR] = it.toInt() }
                // App settings
                backupData["app_language"]?.let { prefs[KEY_APP_LANGUAGE] = it }
                backupData["font_size_scale"]?.let { prefs[KEY_FONT_SIZE_SCALE] = it.toFloat() }
                backupData["notifications_enabled"]?.let { prefs[KEY_NOTIFICATIONS_ENABLED] = it.toBoolean() }
                backupData["notification_sound"]?.let { prefs[KEY_NOTIFICATION_SOUND] = it.toBoolean() }
                backupData["notification_vibrate"]?.let { prefs[KEY_NOTIFICATION_VIBRATE] = it.toBoolean() }
                // BLE settings
                backupData["ble_enabled"]?.let { prefs[KEY_BLE_ENABLED] = it.toBoolean() }
                backupData["transport_mode"]?.let { prefs[KEY_TRANSPORT_MODE] = it }
            }

            if (editResult.isFailure) {
                return Result.failure(editResult.exceptionOrNull() ?: Exception("Failed to import backup"))
            }

            Logger.d("SettingsRepository -> Backup imported successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to import backup", e)
            Result.failure(e)
        }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Safely edits preferences with error handling.
     * BUG FIX #5: Now returns Result<Unit> to allow callers to handle errors properly.
     */
    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): Result<Unit> {
        return try {
            context.dataStore.edit { block(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Write Failed", e)
            Result.failure(e)
        }
    }
}
