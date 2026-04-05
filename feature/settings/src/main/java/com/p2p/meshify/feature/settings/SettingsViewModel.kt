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
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for application settings with Type-Safe Enums.
 * Extended for MD3E - Full Control Plan.
 */
class SettingsViewModel(
    val settingsRepository: ISettingsRepository
) : ViewModel() {

    val displayName: StateFlow<String> = settingsRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val dynamicColorEnabled: StateFlow<Boolean> = settingsRepository.dynamicColorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticFeedbackEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isNetworkVisible: StateFlow<Boolean> = settingsRepository.isNetworkVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val avatarHash: StateFlow<String?> = settingsRepository.avatarHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val motionPreset: StateFlow<MotionPreset> = settingsRepository.motionPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MotionPreset.STANDARD)

    val motionScale: StateFlow<Float> = settingsRepository.motionScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val fontFamilyPreset: StateFlow<FontFamilyPreset> = settingsRepository.fontFamilyPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontFamilyPreset.ROBOTO)

    val customFontUri: StateFlow<String?> = settingsRepository.customFontUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bubbleStyle: StateFlow<BubbleStyle> = settingsRepository.bubbleStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyle.ROUNDED)

    val visualDensity: StateFlow<Float> = settingsRepository.visualDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val seedColor: StateFlow<Int> = settingsRepository.seedColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF006D68.toInt())

    // ✅ New Settings Flows
    val appLanguage: StateFlow<String> = settingsRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val fontSizeScale: StateFlow<Float> = settingsRepository.fontSizeScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationSound: StateFlow<Boolean> = settingsRepository.notificationSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationVibrate: StateFlow<Boolean> = settingsRepository.notificationVibrate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // BLE Transport Settings
    val bleEnabled: StateFlow<Boolean> = settingsRepository.bleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val transportMode: StateFlow<TransportMode> = settingsRepository.transportMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransportMode.AUTO)

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId

    val appVersion: String = settingsRepository.getAppVersion()

    init {
        viewModelScope.launch {
            _deviceId.value = settingsRepository.getDeviceId()
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            settingsRepository.updateDisplayName(name)
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

    // ✅ New Settings Functions
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
