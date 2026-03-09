package com.p2p.meshify.data.repository

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
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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

    override suspend fun getDeviceId(): String {
        return try {
            val prefs = context.dataStore.data.map { it[KEY_DEVICE_ID] }.firstOrNull()
            if (prefs != null) return prefs
            val newId = UUID.randomUUID().toString()
            safeEdit { it[KEY_DEVICE_ID] = newId }
            newId
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    override suspend fun updateDisplayName(name: String) {
        safeEdit { it[KEY_DISPLAY_NAME] = name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        safeEdit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        safeEdit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        safeEdit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setNetworkVisibility(visible: Boolean) {
        safeEdit { it[KEY_NETWORK_VISIBLE] = visible }
    }

    override suspend fun updateAvatarHash(hash: String?) {
        if (hash != null) {
            safeEdit { it[KEY_AVATAR_HASH] = hash }
        } else {
            safeEdit { it.remove(KEY_AVATAR_HASH) }
        }
    }

    // MD3E Setting Mutators
    override suspend fun setShapeStyle(style: ShapeStyle) {
        safeEdit { it[KEY_SHAPE_STYLE] = style.name }
    }

    override suspend fun setMotionPreset(preset: MotionPreset) {
        safeEdit { it[KEY_MOTION_PRESET] = preset.name }
    }

    override suspend fun setMotionScale(scale: Float) {
        safeEdit { it[KEY_MOTION_SCALE] = scale.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun setFontFamilyPreset(family: FontFamilyPreset) {
        safeEdit { it[KEY_FONT_FAMILY] = family.name }
    }

    override suspend fun setCustomFontUri(uri: String?) {
        if (uri != null) {
            safeEdit { it[KEY_CUSTOM_FONT_URI] = uri }
        } else {
            safeEdit { it.remove(KEY_CUSTOM_FONT_URI) }
        }
    }

    override suspend fun setBubbleStyle(style: BubbleStyle) {
        safeEdit { it[KEY_BUBBLE_STYLE] = style.name }
    }

    override suspend fun setVisualDensity(density: Float) {
        safeEdit { it[KEY_VISUAL_DENSITY] = density.coerceIn(0.8f, 1.5f) }
    }

    override suspend fun setSeedColor(color: Int) {
        safeEdit { it[KEY_SEED_COLOR] = color }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit { block(it) }
        } catch (e: Exception) {
            Logger.e("SettingsRepository -> Write Failed", e)
        }
    }
}
