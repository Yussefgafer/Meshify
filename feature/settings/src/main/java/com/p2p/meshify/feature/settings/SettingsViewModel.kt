package com.p2p.meshify.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val isNetworkVisible: Boolean = true,
    val avatarHash: String? = null,
    val deviceId: String = "",
    val deviceIdLoaded: Boolean = false,
    val seedColor: Int = 0xFF006D68.toInt(),
    val appLanguage: String = "en",
    val fontSizeScale: Float = 1.0f,
    val notificationsEnabled: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibrate: Boolean = true,
    val bleEnabled: Boolean = false,
    val transportMode: TransportMode = TransportMode.AUTO,
    val displayNameError: String? = null
)

class SettingsViewModel @Inject constructor(
    val settingsRepository: ISettingsRepository
) : ViewModel() {

    private val _settingsUiState = MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState

    init {
        val repo = settingsRepository
        repo.displayName.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(displayName = value) }.launchIn(viewModelScope)
        repo.themeMode.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(themeMode = value) }.launchIn(viewModelScope)
        repo.dynamicColorEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(dynamicColorEnabled = value) }.launchIn(viewModelScope)
        repo.hapticFeedbackEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(hapticFeedbackEnabled = value) }.launchIn(viewModelScope)
        repo.isNetworkVisible.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(isNetworkVisible = value) }.launchIn(viewModelScope)
        repo.avatarHash.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(avatarHash = value) }.launchIn(viewModelScope)
        repo.seedColor.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(seedColor = value) }.launchIn(viewModelScope)
        repo.appLanguage.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(appLanguage = value) }.launchIn(viewModelScope)
        repo.fontSizeScale.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(fontSizeScale = value) }.launchIn(viewModelScope)
        repo.notificationsEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationsEnabled = value) }.launchIn(viewModelScope)
        repo.notificationSound.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationSound = value) }.launchIn(viewModelScope)
        repo.notificationVibrate.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationVibrate = value) }.launchIn(viewModelScope)
        repo.bleEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(bleEnabled = value) }.launchIn(viewModelScope)
        repo.transportMode.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(transportMode = value) }.launchIn(viewModelScope)

        viewModelScope.launch {
            val deviceId = repo.getDeviceId()
            _settingsUiState.value = _settingsUiState.value.copy(deviceId = deviceId, deviceIdLoaded = true)
            _deviceId.value = deviceId
        }
    }

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId

    val appVersion: String = settingsRepository.getAppVersion()

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updateDisplayName(name)
                _settingsUiState.value = _settingsUiState.value.copy(displayNameError = null)
            } catch (e: IllegalArgumentException) {
                _settingsUiState.value = _settingsUiState.value.copy(displayNameError = e.message)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setNetworkVisibility(visible: Boolean) {
        viewModelScope.launch { settingsRepository.setNetworkVisibility(visible) }
    }

    fun updateAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bytes = FileUtils.getBytesFromUri(context, uri)
            if (bytes != null) {
                val hash = FileUtils.calculateHash(bytes)
                val savedPath = FileUtils.saveBytesToInternalStorage(context, hash, bytes, "avatars")
                if (savedPath != null) {
                    settingsRepository.updateAvatarHash(hash)
                }
            }
        }
    }

    fun setSeedColor(color: Color) {
        viewModelScope.launch {
            val colorInt = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
            settingsRepository.setSeedColor(colorInt)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setAppLanguage(language) }
    }

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setFontSizeScale(scale) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setNotificationSound(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationSound(enabled) }
    }

    fun setNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationVibrate(enabled) }
    }

    fun setBleEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBleEnabled(enabled) }
    }

    fun setTransportMode(mode: TransportMode) {
        viewModelScope.launch { settingsRepository.setTransportMode(mode) }
    }

    fun clearCache(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                settingsRepository.clearCache()
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun exportBackup(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(settingsRepository.exportBackup())
        }
    }

    fun importBackup(backupJson: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(settingsRepository.importBackup(backupJson))
        }
    }
}
