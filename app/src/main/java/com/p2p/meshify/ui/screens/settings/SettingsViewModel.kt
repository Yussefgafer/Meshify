package com.p2p.meshify.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
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
    private val settingsRepository: ISettingsRepository
) : ViewModel() {

    val displayName: StateFlow<String> = settingsRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val dynamicColorEnabled: StateFlow<Boolean> = settingsRepository.dynamicColorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isNetworkVisible: StateFlow<Boolean> = settingsRepository.isNetworkVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // MD3E Settings Flows
    val shapeStyle: StateFlow<ShapeStyle> = settingsRepository.shapeStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShapeStyle.CIRCLE)

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

    // MD3E Setting Mutators
    fun setShapeStyle(style: ShapeStyle) {
        viewModelScope.launch {
            settingsRepository.setShapeStyle(style)
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
}
