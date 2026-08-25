package com.p2p.meshify.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.SeedColorPickerGrid
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsDivider
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsItem
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsSection
import com.p2p.meshify.core.ui.designsystem.components.DxSwitchSettingItem
import com.p2p.meshify.core.ui.designsystem.foundation.DxShape
import com.p2p.meshify.core.ui.designsystem.foundation.DxSpacing
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.PremiumHaptics
import com.p2p.meshify.domain.repository.ThemeMode

@Composable
fun IdentitySection(
    state: SettingsUiState,
    onEditName: () -> Unit
) {
    DxSettingsSection(title = stringResource(R.string.settings_section_identity)) {
        DxSettingsItem(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.setting_display_name),
            subtitle = state.displayName,
            onClick = onEditName,
            showChevron = true
        )
    }
}

@Composable
fun AppearanceSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    haptics: PremiumHaptics,
    onOpenThemeSheet: () -> Unit
) {
    val seedColor = remember(state.seedColor) { Color(state.seedColor) }

    DxSettingsSection(title = stringResource(R.string.settings_section_appearance)) {
        DxSettingsItem(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.settings_theme_mode),
            subtitle = when (state.themeMode) {
                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            },
            onClick = onOpenThemeSheet,
            showChevron = true
        )

        DxSettingsDivider()

        DxSwitchSettingItem(
            icon = Icons.Filled.ColorLens,
            title = stringResource(R.string.settings_dynamic_colors),
            subtitle = stringResource(R.string.settings_dynamic_colors_desc),
            checked = state.dynamicColorEnabled,
            onCheckedChange = { viewModel.setDynamicColor(it) }
        )

        if (!state.dynamicColorEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = DxShape.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DxSpacing.Lg, vertical = DxSpacing.Sm)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DxSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(DxSpacing.Sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_label_accent_color),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SeedColorPickerGrid(
                        selectedColor = seedColor,
                        onColorSelected = { color ->
                            haptics.perform(HapticPattern.Pop)
                            viewModel.setSeedColor(color)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacySection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    haptics: PremiumHaptics
) {
    DxSettingsSection(title = stringResource(R.string.settings_section_privacy)) {
        DxSwitchSettingItem(
            icon = Icons.Filled.Visibility,
            title = stringResource(R.string.settings_visibility),
            subtitle = stringResource(R.string.settings_visibility_desc),
            checked = state.isNetworkVisible,
            onCheckedChange = { viewModel.setNetworkVisibility(it) }
        )
    }
}

@Composable
fun NetworkSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    haptics: PremiumHaptics,
    onOpenBleSheet: () -> Unit
) {
    DxSettingsSection(title = stringResource(R.string.settings_section_network)) {
        DxSwitchSettingItem(
            icon = Icons.Filled.Bluetooth,
            title = stringResource(R.string.setting_bluetooth),
            subtitle = stringResource(R.string.setting_bluetooth_desc),
            checked = state.bleEnabled,
            onCheckedChange = { viewModel.setBleEnabled(it) }
        )

        DxSettingsDivider()

        DxSettingsItem(
            icon = Icons.AutoMirrored.Filled.BluetoothSearching,
            title = stringResource(R.string.setting_bluetooth_status_title),
            subtitle = if (state.bleEnabled) stringResource(R.string.setting_bluetooth_status_active)
                else stringResource(R.string.setting_bluetooth_status_inactive),
            onClick = onOpenBleSheet,
            showChevron = true
        )
    }
}

@Composable
fun AppSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    haptics: PremiumHaptics,
    onOpenLanguage: () -> Unit,
    onOpenFontSize: () -> Unit,
    onClearCache: () -> Unit
) {
    DxSettingsSection(title = stringResource(R.string.settings_group_app)) {
        DxSettingsItem(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.setting_language),
            subtitle = if (state.appLanguage == "ar") stringResource(R.string.settings_language_arabic)
                else stringResource(R.string.settings_language_english),
            onClick = onOpenLanguage,
            showChevron = true
        )

        DxSettingsDivider()

        DxSettingsItem(
            icon = Icons.Filled.TextFields,
            title = stringResource(R.string.setting_font_size),
            subtitle = "${(state.fontSizeScale * 100).toInt()}%",
            onClick = onOpenFontSize,
            showChevron = true
        )

        DxSettingsDivider()

        DxSwitchSettingItem(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.setting_notifications),
            subtitle = if (state.notificationsEnabled) stringResource(R.string.settings_status_enabled)
                else stringResource(R.string.settings_status_disabled),
            checked = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
        )

        DxSettingsDivider()

        DxSwitchSettingItem(
            icon = Icons.Filled.Vibration,
            title = stringResource(R.string.setting_haptic_feedback),
            subtitle = stringResource(R.string.setting_haptic_feedback_desc),
            checked = state.hapticFeedbackEnabled,
            onCheckedChange = { viewModel.setHapticFeedback(it) }
        )

        DxSettingsDivider()

        DxSwitchSettingItem(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.setting_notification_sound),
            subtitle = stringResource(R.string.setting_notification_sound_desc),
            checked = state.notificationSound,
            enabled = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationSound(it) }
        )

        DxSettingsDivider()

        DxSwitchSettingItem(
            icon = Icons.Filled.Vibration,
            title = stringResource(R.string.setting_vibration),
            subtitle = stringResource(R.string.setting_vibration_desc),
            checked = state.notificationVibrate,
            enabled = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationVibrate(it) }
        )

        DxSettingsDivider()

        DxSettingsItem(
            icon = Icons.Filled.DeleteSweep,
            title = stringResource(R.string.setting_clear_cache),
            subtitle = stringResource(R.string.setting_clear_cache_desc),
            onClick = onClearCache,
            showChevron = true
        )
    }
}

@Composable
fun AboutSection(
    appVersion: String,
    haptics: PremiumHaptics,
    onDeveloperModeClick: () -> Unit,
    onOpenGithub: () -> Unit
) {
    val versionTapCount = remember { mutableIntStateOf(0) }
    val lastTapTime = remember { mutableLongStateOf(0L) }

    DxSettingsSection(title = stringResource(R.string.settings_section_info)) {
        DxSettingsItem(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.setting_app_version),
            subtitle = appVersion,
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastTapTime.longValue > 2000) {
                    versionTapCount.intValue = 1
                } else {
                    versionTapCount.intValue++
                }
                lastTapTime.longValue = now
                haptics.perform(HapticPattern.Tick)

                if (versionTapCount.intValue >= 7) {
                    versionTapCount.intValue = 0
                    haptics.perform(HapticPattern.Success)
                    onDeveloperModeClick()
                }
            },
            showChevron = true
        )

        DxSettingsDivider()

        DxSettingsItem(
            icon = Icons.Filled.Code,
            title = stringResource(R.string.settings_label_github_repo),
            subtitle = stringResource(R.string.settings_label_github_desc),
            onClick = onOpenGithub,
            showChevron = true
        )
    }
}
