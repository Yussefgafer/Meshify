package com.p2p.meshify.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for application settings with Type-Safe Enums.
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
}
