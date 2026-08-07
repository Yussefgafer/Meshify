package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
class SettingsRepository(
    private val context: Context,
    private val prefsStore: DataStore<Preferences> = context.dataStore
) : ISettingsRepository {

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_NETWORK_VISIBLE = booleanPreferencesKey("network_visible")
        val KEY_AVATAR_HASH = stringPreferencesKey("avatar_hash")
        val KEY_SEED_COLOR = intPreferencesKey("seed_color")

        val KEY_BLE_ENABLED = booleanPreferencesKey("ble_enabled")
        val KEY_TRANSPORT_MODE = stringPreferencesKey("transport_mode")

        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val KEY_NOTIFICATION_VIBRATE = booleanPreferencesKey("notification_vibrate")

        // MD3E design configuration keys
        val KEY_SHAPE_STYLE = stringPreferencesKey("shape_style")
        val KEY_MOTION_PRESET = stringPreferencesKey("motion_preset")
        val KEY_MOTION_SCALE = floatPreferencesKey("motion_scale")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_CUSTOM_FONT_URI = stringPreferencesKey("custom_font_uri")
        val KEY_BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val KEY_VISUAL_DENSITY = floatPreferencesKey("visual_density")
    }

    override val displayName: Flow<String> = prefsStore.data.map { preferences ->
        preferences[KEY_DISPLAY_NAME] ?: "User_${preferences[KEY_DEVICE_ID]?.take(4) ?: "Unknown"}"
    }

    override val themeMode: Flow<ThemeMode> = prefsStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[KEY_THEME_MODE] ?: "SYSTEM")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read themeMode", e)
            ThemeMode.SYSTEM
        }
    }

    override val hapticFeedbackEnabled: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_HAPTIC_FEEDBACK] ?: true
    }

    override val dynamicColorEnabled: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLOR] ?: true
    }

    override val isNetworkVisible: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_NETWORK_VISIBLE] ?: true
    }

    override val avatarHash: Flow<String?> = prefsStore.data.map { preferences ->
        preferences[KEY_AVATAR_HASH]
    }

    // MD3E Settings Flows
    override val shapeStyle: Flow<ShapeStyle> = prefsStore.data.map { preferences ->
        try {
            ShapeStyle.valueOf(preferences[KEY_SHAPE_STYLE] ?: "CIRCLE")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read shapeStyle", e)
            ShapeStyle.CIRCLE
        }
    }

    override val motionPreset: Flow<MotionPreset> = prefsStore.data.map { preferences ->
        try {
            MotionPreset.valueOf(preferences[KEY_MOTION_PRESET] ?: "STANDARD")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read motionPreset", e)
            MotionPreset.STANDARD
        }
    }

    override val motionScale: Flow<Float> = prefsStore.data.map { preferences ->
        preferences[KEY_MOTION_SCALE] ?: 1.0f
    }

    override val fontFamilyPreset: Flow<FontFamilyPreset> = prefsStore.data.map { preferences ->
        try {
            FontFamilyPreset.valueOf(preferences[KEY_FONT_FAMILY] ?: "ROBOTO")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read fontFamilyPreset", e)
            FontFamilyPreset.ROBOTO
        }
    }

    override val customFontUri: Flow<String?> = prefsStore.data.map { preferences ->
        preferences[KEY_CUSTOM_FONT_URI]
    }

    override val bubbleStyle: Flow<BubbleStyle> = prefsStore.data.map { preferences ->
        try {
            BubbleStyle.valueOf(preferences[KEY_BUBBLE_STYLE] ?: "ROUNDED")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read bubbleStyle", e)
            BubbleStyle.ROUNDED
        }
    }

    override val visualDensity: Flow<Float> = prefsStore.data.map { preferences ->
        preferences[KEY_VISUAL_DENSITY] ?: 1.0f
    }

    override val seedColor: Flow<Int> = prefsStore.data.map { preferences ->
        preferences[KEY_SEED_COLOR] ?: 0xFF006D68.toInt()
    }

    override val bleEnabled: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_BLE_ENABLED] ?: false
    }

    override val transportMode: Flow<TransportMode> = prefsStore.data.map { preferences ->
        try {
            TransportMode.valueOf(preferences[KEY_TRANSPORT_MODE] ?: "MULTI_PATH")
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read transportMode", e)
            TransportMode.MULTI_PATH
        }
    }

    override val hasCompletedOnboarding: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    override val appLanguage: Flow<String> = prefsStore.data.map { preferences ->
        preferences[KEY_APP_LANGUAGE] ?: "en"
    }

    override val fontSizeScale: Flow<Float> = prefsStore.data.map { preferences ->
        preferences[KEY_FONT_SIZE_SCALE] ?: 1.0f
    }

    override val notificationsEnabled: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    override val notificationSound: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_SOUND] ?: true
    }

    override val notificationVibrate: Flow<Boolean> = prefsStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_VIBRATE] ?: true
    }

    override suspend fun getDeviceId(): String {
        return try {
            val prefs = prefsStore.data.map { it[KEY_DEVICE_ID] }.firstOrNull()
            if (prefs != null) return prefs
            val newId = UUID.randomUUID().toString()
            safeEdit { it[KEY_DEVICE_ID] = newId }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to save device ID", e)
            }
            newId
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read deviceId", e)
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

    override suspend fun setSeedColor(color: Int) {
        safeEdit { it[KEY_SEED_COLOR] = color }.onFailure { e ->
            Logger.e("SettingsRepository -> Failed to set seed color", e)
        }
    }

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
            Logger.e("SettingsRepository -> Failed to set font family preset", e)
        }
    }

    override suspend fun setCustomFontUri(uri: String?) {
        if (uri != null) {
            safeEdit { it[KEY_CUSTOM_FONT_URI] = uri }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to set custom font URI", e)
            }
        } else {
            safeEdit { it.remove(KEY_CUSTOM_FONT_URI) }.onFailure { e ->
                Logger.e("SettingsRepository -> Failed to remove custom font URI", e)
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
            val currentAvatarHash = prefsStore.data.map { it[KEY_AVATAR_HASH] }.firstOrNull()
            val avatarsDir = java.io.File(context.filesDir, "avatars")
            if (avatarsDir.exists() && avatarsDir.isDirectory) {
                avatarsDir.listFiles()?.forEach { file ->
                    if (file.name != currentAvatarHash) {
                        file.delete()
                    }
                }
            }
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
            val prefs = try {
                prefsStore.data.first()
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to read preferences: ${e.message}", e))
            }

            val backupData = mapOf(
                "display_name" to prefs[KEY_DISPLAY_NAME],
                "theme_mode" to prefs[KEY_THEME_MODE],
                "dynamic_color" to prefs[KEY_DYNAMIC_COLOR],
                "haptic_feedback" to prefs[KEY_HAPTIC_FEEDBACK],
                "network_visible" to prefs[KEY_NETWORK_VISIBLE],
                "avatar_hash" to prefs[KEY_AVATAR_HASH],
                "seed_color" to prefs[KEY_SEED_COLOR]?.toString(),
                "app_language" to prefs[KEY_APP_LANGUAGE],
                "font_size_scale" to prefs[KEY_FONT_SIZE_SCALE]?.toString(),
                "notifications_enabled" to prefs[KEY_NOTIFICATIONS_ENABLED],
                "notification_sound" to prefs[KEY_NOTIFICATION_SOUND],
                "notification_vibrate" to prefs[KEY_NOTIFICATION_VIBRATE],
                "ble_enabled" to prefs[KEY_BLE_ENABLED],
                "transport_mode" to prefs[KEY_TRANSPORT_MODE],
                "export_timestamp" to System.currentTimeMillis().toString()
            ).filterValues { it != null }.mapValues { it.value.toString() }
            val json = Json.encodeToString(backupData)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importBackup(json: String): Result<Unit> {
        return try {
            val backupData: Map<String, String> = Json.decodeFromString(json)
            prefsStore.edit { prefs ->
                backupData.forEach { (key, value) ->
                    when (key) {
                        "display_name" -> prefs[KEY_DISPLAY_NAME] = value
                        "theme_mode" -> prefs[KEY_THEME_MODE] = value
                        "dynamic_color" -> prefs[KEY_DYNAMIC_COLOR] = value.toBoolean()
                        "haptic_feedback" -> prefs[KEY_HAPTIC_FEEDBACK] = value.toBoolean()
                        "network_visible" -> prefs[KEY_NETWORK_VISIBLE] = value.toBoolean()
                        "avatar_hash" -> prefs[KEY_AVATAR_HASH] = value
                        "seed_color" -> prefs[KEY_SEED_COLOR] = value.toInt()
                        "app_language" -> prefs[KEY_APP_LANGUAGE] = value
                        "font_size_scale" -> prefs[KEY_FONT_SIZE_SCALE] = value.toFloat()
                        "notifications_enabled" -> prefs[KEY_NOTIFICATIONS_ENABLED] = value.toBoolean()
                        "notification_sound" -> prefs[KEY_NOTIFICATION_SOUND] = value.toBoolean()
                        "notification_vibrate" -> prefs[KEY_NOTIFICATION_VIBRATE] = value.toBoolean()
                        "ble_enabled" -> prefs[KEY_BLE_ENABLED] = value.toBoolean()
                        "transport_mode" -> prefs[KEY_TRANSPORT_MODE] = value
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Failed to read app version", e)
            "1.0"
        }
    }

    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): Result<Unit> {
        return try {
            prefsStore.edit { block(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Write Failed", e)
            Result.failure(e)
        }
    }
}
