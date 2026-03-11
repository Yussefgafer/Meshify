package com.p2p.meshify.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
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
    val settingsRepository: ISettingsRepository // FIXED: Made public for UI access
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

    // MD3E Settings Flows
    // NOTE: shapeStyle removed - FAB now uses animated morphing between shapes
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

    // MD3E Setting Mutators
    // NOTE: setShapeStyle removed - FAB now uses animated morphing between shapes
    
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
}
