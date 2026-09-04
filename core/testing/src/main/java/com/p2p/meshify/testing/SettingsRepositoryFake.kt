package com.p2p.meshify.testing

import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ISettingsRepository] for tests. Backed by [MutableStateFlow]s instead of a real
 * DataStore, so it runs on plain JVM with no Android context and no Robolectric. Purely a fixture
 * for ViewModel/use-case tests — it does NOT exercise DataStore serialization or persistence.
 */
class SettingsRepositoryFake : ISettingsRepository {

    private val _displayName = MutableStateFlow("Tester")
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val _dynamicColorEnabled = MutableStateFlow(true)
    private val _hapticFeedbackEnabled = MutableStateFlow(true)
    private val _isNetworkVisible = MutableStateFlow(true)
    private val _avatarHash = MutableStateFlow<String?>(null)
    private val _seedColor = MutableStateFlow(0)
    private val _bleEnabled = MutableStateFlow(false)
    private val _transportMode = MutableStateFlow(TransportMode.MULTI_PATH)
    private val _hasCompletedOnboarding = MutableStateFlow(false)
    private val _appLanguage = MutableStateFlow("en")
    private val _fontSizeScale = MutableStateFlow(1f)
    private val _notificationsEnabled = MutableStateFlow(true)
    private val _notificationSound = MutableStateFlow(true)
    private val _notificationVibrate = MutableStateFlow(true)

    var deviceId = "00000000-0000-0000-0000-0000000000aa"
    var clearedCacheCount = 0

    override val displayName = _displayName.asStateFlow()
    override val themeMode = _themeMode.asStateFlow()
    override val dynamicColorEnabled = _dynamicColorEnabled.asStateFlow()
    override val hapticFeedbackEnabled = _hapticFeedbackEnabled.asStateFlow()
    override val isNetworkVisible = _isNetworkVisible.asStateFlow()
    override val avatarHash = _avatarHash.asStateFlow()
    override val seedColor = _seedColor.asStateFlow()
    override val bleEnabled = _bleEnabled.asStateFlow()
    override val transportMode = _transportMode.asStateFlow()
    override val hasCompletedOnboarding = _hasCompletedOnboarding.asStateFlow()
    override val appLanguage = _appLanguage.asStateFlow()
    override val fontSizeScale = _fontSizeScale.asStateFlow()
    override val notificationsEnabled = _notificationsEnabled.asStateFlow()
    override val notificationSound = _notificationSound.asStateFlow()
    override val notificationVibrate = _notificationVibrate.asStateFlow()

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun updateDisplayName(name: String) {
        _displayName.value = name
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
    }

    override suspend fun setNetworkVisibility(visible: Boolean) {
        _isNetworkVisible.value = visible
    }

    override suspend fun updateAvatarHash(hash: String?) {
        _avatarHash.value = hash
    }

    override suspend fun setSeedColor(color: Int) {
        _seedColor.value = color
    }

    override suspend fun setBleEnabled(enabled: Boolean) {
        _bleEnabled.value = enabled
    }

    override suspend fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
    }

    override suspend fun setOnboardingCompleted() {
        _hasCompletedOnboarding.value = true
    }

    override suspend fun resetOnboardingCompleted() {
        _hasCompletedOnboarding.value = false
    }

    override suspend fun setAppLanguage(language: String) {
        _appLanguage.value = language
    }

    override suspend fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    override suspend fun setNotificationSound(enabled: Boolean) {
        _notificationSound.value = enabled
    }

    override suspend fun setNotificationVibrate(enabled: Boolean) {
        _notificationVibrate.value = enabled
    }

    override suspend fun clearCache() {
        clearedCacheCount++
    }

    override suspend fun exportBackup(): Result<String> = Result.success("{}")

    override suspend fun importBackup(json: String): Result<Unit> = Result.success(Unit)

    override fun getAppVersion(): String = "1.1.4"
}
