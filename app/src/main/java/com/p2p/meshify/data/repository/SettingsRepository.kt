package com.p2p.meshify.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Data layer implementation of Settings Repository.
 * Validates data and handles framework dependencies.
 */
class SettingsRepository(private val context: Context) : ISettingsRepository {

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_NETWORK_VISIBLE = booleanPreferencesKey("network_visible")
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

    override val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLOR] ?: true
    }

    override val isNetworkVisible: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NETWORK_VISIBLE] ?: true
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

    override suspend fun setDynamicColor(enabled: Boolean) {
        safeEdit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setNetworkVisibility(visible: Boolean) {
        safeEdit { it[KEY_NETWORK_VISIBLE] = visible }
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
