package com.p2p.meshify.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Validated Theme Modes.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Domain interface for user preferences and identity.
 */
interface ISettingsRepository {
    val displayName: Flow<String>
    val themeMode: Flow<ThemeMode>
    val dynamicColorEnabled: Flow<Boolean>
    val isNetworkVisible: Flow<Boolean>

    suspend fun getDeviceId(): String
    suspend fun updateDisplayName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setNetworkVisibility(visible: Boolean)
    fun getAppVersion(): String
}
