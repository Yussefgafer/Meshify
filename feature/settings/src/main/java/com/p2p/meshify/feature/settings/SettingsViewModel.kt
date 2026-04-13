package com.p2p.meshify.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
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

/**
 * Unified UI state for the Settings screen.
 * Combines all individual settings into a single data class
 * to enable single-collect StateFlow and reduce recompositions.
 */
data class SettingsUiState(
    val displayName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val isNetworkVisible: Boolean = true,
    val avatarHash: String? = null,
    val deviceId: String = "",
    val deviceIdLoaded: Boolean = false,
    val appVersion: String = "",
    val motionPreset: MotionPreset = MotionPreset.STANDARD,
    val motionScale: Float = 1.0f,
    val fontFamilyPreset: FontFamilyPreset = FontFamilyPreset.ROBOTO,
    val customFontUri: String? = null,
    val bubbleStyle: BubbleStyle = BubbleStyle.ROUNDED,
    val visualDensity: Float = 1.0f,
    val seedColor: Int = 0xFF006D68.toInt(),
    val appLanguage: String = "en",
    val fontSizeScale: Float = 1.0f,
    val notificationsEnabled: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibrate: Boolean = true,
    val bleEnabled: Boolean = false,
    val transportMode: TransportMode = TransportMode.AUTO,
    val displayNameError: String? = null,
    val shapeStyle: ShapeStyle = ShapeStyle.CIRCLE
)

/**
 * ViewModel for application settings with Type-Safe Enums.
 * Extended for MD3E - Full Control Plan.
 *
 * Uses a single [SettingsUiState] StateFlow instead of 20+ individual flows.
 * This reduces recompositions from 16+ to exactly 1 when any setting changes.
 */
class SettingsViewModel @Inject constructor(
    val settingsRepository: ISettingsRepository
) : ViewModel() {

    // Unified SettingsUiState — single StateFlow replacing 20 individual flows
    // Uses MutableStateFlow updated by individual collectors for type safety
    // Note: deviceId is loaded asynchronously; empty string is a brief placeholder
    private val _settingsUiState = MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState

    init {
        // Collect each repository flow and update the unified state
        settingsRepository.displayName.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(displayName = value) }.launchIn(viewModelScope)
        settingsRepository.themeMode.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(themeMode = value) }.launchIn(viewModelScope)
        settingsRepository.dynamicColorEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(dynamicColorEnabled = value) }.launchIn(viewModelScope)
        settingsRepository.hapticFeedbackEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(hapticFeedbackEnabled = value) }.launchIn(viewModelScope)
        settingsRepository.isNetworkVisible.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(isNetworkVisible = value) }.launchIn(viewModelScope)
        settingsRepository.avatarHash.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(avatarHash = value) }.launchIn(viewModelScope)
        settingsRepository.motionPreset.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(motionPreset = value) }.launchIn(viewModelScope)
        settingsRepository.motionScale.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(motionScale = value) }.launchIn(viewModelScope)
        settingsRepository.fontFamilyPreset.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(fontFamilyPreset = value) }.launchIn(viewModelScope)
        settingsRepository.customFontUri.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(customFontUri = value) }.launchIn(viewModelScope)
        settingsRepository.bubbleStyle.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(bubbleStyle = value) }.launchIn(viewModelScope)
        settingsRepository.visualDensity.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(visualDensity = value) }.launchIn(viewModelScope)
        settingsRepository.seedColor.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(seedColor = value) }.launchIn(viewModelScope)
        settingsRepository.appLanguage.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(appLanguage = value) }.launchIn(viewModelScope)
        settingsRepository.fontSizeScale.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(fontSizeScale = value) }.launchIn(viewModelScope)
        settingsRepository.notificationsEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationsEnabled = value) }.launchIn(viewModelScope)
        settingsRepository.notificationSound.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationSound = value) }.launchIn(viewModelScope)
        settingsRepository.notificationVibrate.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(notificationVibrate = value) }.launchIn(viewModelScope)
        settingsRepository.bleEnabled.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(bleEnabled = value) }.launchIn(viewModelScope)
        settingsRepository.transportMode.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(transportMode = value) }.launchIn(viewModelScope)
        settingsRepository.shapeStyle.onEach { value -> _settingsUiState.value = _settingsUiState.value.copy(shapeStyle = value) }.launchIn(viewModelScope)

        // Load deviceId asynchronously and update state when ready
        viewModelScope.launch {
            val deviceId = settingsRepository.getDeviceId()
            _settingsUiState.value = _settingsUiState.value.copy(deviceId = deviceId, deviceIdLoaded = true)
            _deviceId.value = deviceId
        }
    }

    // deviceId is also exposed as a separate flow for backward compatibility
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
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(enabled)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setNetworkVisibility(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNetworkVisibility(visible)
        }
    }

    /**
     * Updates the user's avatar by picking a file, hashing it, and saving it locally.
     * Content-addressable storage ensures no duplicate transfers.
     */
    fun updateAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bytes = FileUtils.getBytesFromUri(context, uri)
            if (bytes != null) {
                val hash = FileUtils.calculateHash(bytes)
                // Save to internal storage using hash as filename
                val savedPath = FileUtils.saveBytesToInternalStorage(
                    context = context,
                    fileName = hash,
                    data = bytes,
                    category = "avatars"
                )
                if (savedPath != null) {
                    settingsRepository.updateAvatarHash(hash)
                }
            }
        }
    }

    fun setMotionPreset(preset: MotionPreset) {
        viewModelScope.launch {
            settingsRepository.setMotionPreset(preset)
        }
    }

    fun setShapeStyle(style: ShapeStyle) {
        viewModelScope.launch {
            settingsRepository.setShapeStyle(style)
        }
    }

    fun setMotionScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setMotionScale(scale)
        }
    }

    fun setFontFamilyPreset(family: FontFamilyPreset) {
        viewModelScope.launch {
            settingsRepository.setFontFamilyPreset(family)
        }
    }

    fun setCustomFontUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setCustomFontUri(uri)
        }
    }

    fun setBubbleStyle(style: BubbleStyle) {
        viewModelScope.launch {
            settingsRepository.setBubbleStyle(style)
        }
    }

    fun setVisualDensity(density: Float) {
        viewModelScope.launch {
            settingsRepository.setVisualDensity(density)
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

    // New Settings Functions
    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setFontSizeScale(scale)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setNotificationSound(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationSound(enabled)
        }
    }

    fun setNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationVibrate(enabled)
        }
    }

    // BLE Transport Mutators
    fun setBleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBleEnabled(enabled)
        }
    }

    fun setTransportMode(mode: TransportMode) {
        viewModelScope.launch {
            settingsRepository.setTransportMode(mode)
        }
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
            val result = settingsRepository.exportBackup()
            onResult(result)
        }
    }

    fun importBackup(backupJson: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = settingsRepository.importBackup(backupJson)
            onResult(result)
        }
    }
}
