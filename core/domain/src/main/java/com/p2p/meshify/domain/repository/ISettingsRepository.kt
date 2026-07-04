package com.p2p.meshify.domain.repository

import com.p2p.meshify.domain.model.TransportMode
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

interface ISettingsRepository {
    val displayName: Flow<String>
    val themeMode: Flow<ThemeMode>
    val dynamicColorEnabled: Flow<Boolean>
    val hapticFeedbackEnabled: Flow<Boolean>
    val isNetworkVisible: Flow<Boolean>
    val avatarHash: Flow<String?>
    val seedColor: Flow<Int>

    val bleEnabled: Flow<Boolean>
    val transportMode: Flow<TransportMode>

    val hasCompletedOnboarding: Flow<Boolean>

    val appLanguage: Flow<String>
    val fontSizeScale: Flow<Float>
    val notificationsEnabled: Flow<Boolean>
    val notificationSound: Flow<Boolean>
    val notificationVibrate: Flow<Boolean>

    suspend fun getDeviceId(): String
    suspend fun updateDisplayName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setNetworkVisibility(visible: Boolean)
    suspend fun updateAvatarHash(hash: String?)
    suspend fun setSeedColor(color: Int)

    suspend fun setBleEnabled(enabled: Boolean)
    suspend fun setTransportMode(mode: TransportMode)

    suspend fun setOnboardingCompleted()
    suspend fun resetOnboardingCompleted()

    suspend fun setAppLanguage(language: String)
    suspend fun setFontSizeScale(scale: Float)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotificationSound(enabled: Boolean)
    suspend fun setNotificationVibrate(enabled: Boolean)
    suspend fun clearCache()
    suspend fun exportBackup(): Result<String>
    fun getAppVersion(): String
}
