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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        // Collect all settings flows in a single combine() to avoid 21 separate collection jobs.
        // Using listOf<Flow<*>> to force the combine overload that accepts mixed types.
        viewModelScope.launch {
            combine(
                listOf<Flow<*>>(
                    settingsRepository.displayName,
                    settingsRepository.themeMode,
                    settingsRepository.dynamicColorEnabled,
                    settingsRepository.hapticFeedbackEnabled,
                    settingsRepository.isNetworkVisible,
                    settingsRepository.avatarHash,
                    settingsRepository.motionPreset,
                    settingsRepository.motionScale,
                    settingsRepository.fontFamilyPreset,
                    settingsRepository.customFontUri,
                    settingsRepository.bubbleStyle,
                    settingsRepository.visualDensity,
                    settingsRepository.seedColor,
                    settingsRepository.appLanguage,
                    settingsRepository.fontSizeScale,
                    settingsRepository.notificationsEnabled,
                    settingsRepository.notificationSound,
                    settingsRepository.notificationVibrate,
                    settingsRepository.bleEnabled,
                    settingsRepository.transportMode,
                    settingsRepository.shapeStyle
                )
            ) { array: Array<*> ->
                SettingsUiState(
                    displayName = array[0] as String,
                    themeMode = array[1] as ThemeMode,
                    dynamicColorEnabled = array[2] as Boolean,
                    hapticFeedbackEnabled = array[3] as Boolean,
                    isNetworkVisible = array[4] as Boolean,
                    avatarHash = array[5] as String?,
                    motionPreset = array[6] as MotionPreset,
                    motionScale = array[7] as Float,
                    fontFamilyPreset = array[8] as FontFamilyPreset,
                    customFontUri = array[9] as String?,
                    bubbleStyle = array[10] as BubbleStyle,
                    visualDensity = array[11] as Float,
                    seedColor = array[12] as Int,
                    appLanguage = array[13] as String,
                    fontSizeScale = array[14] as Float,
                    notificationsEnabled = array[15] as Boolean,
                    notificationSound = array[16] as Boolean,
                    notificationVibrate = array[17] as Boolean,
                    bleEnabled = array[18] as Boolean,
                    transportMode = array[19] as TransportMode,
                    shapeStyle = array[20] as ShapeStyle
                )
            }.collect { state ->
                _settingsUiState.value = state
            }
        }

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
