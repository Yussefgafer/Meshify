package com.p2p.meshify.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.SeedColorPickerGrid
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.PremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.repository.ThemeMode

@Composable
fun IdentitySection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    haptics: PremiumHaptics,
    onEditName: () -> Unit
) {
    val context = LocalContext.current
    val deviceTitle = stringResource(R.string.setting_device_id)
    val deviceSuffix = stringResource(R.string.settings_label_device_id_suffix)

    SettingsSection(
        title = stringResource(R.string.settings_section_identity),
        icon = {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SettingsItem(
            title = stringResource(R.string.setting_display_name),
            subtitle = state.displayName,
            leadingIcon = {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onEditName
        )

        SettingsItem(
            title = deviceTitle,
            subtitle = state.deviceId.take(8).uppercase() + deviceSuffix,
            leadingIcon = {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(deviceTitle, state.deviceId)
                clipboard.setPrimaryClip(clip)
                haptics.perform(HapticPattern.Success)
            }
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

    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        icon = {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SettingsItem(
            title = stringResource(R.string.settings_theme_mode),
            subtitle = when (state.themeMode) {
                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenThemeSheet
        )

        SwitchSettingItem(
            title = stringResource(R.string.settings_dynamic_colors),
            subtitle = stringResource(R.string.settings_dynamic_colors_desc),
            checked = state.dynamicColorEnabled,
            onCheckedChange = { viewModel.setDynamicColor(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        if (!state.dynamicColorEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeshifyDesignSystem.Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
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
    SettingsSection(
        title = stringResource(R.string.settings_section_privacy),
        icon = {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_visibility),
            subtitle = stringResource(R.string.settings_visibility_desc),
            checked = state.isNetworkVisible,
            onCheckedChange = { viewModel.setNetworkVisibility(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    SettingsSection(
        title = stringResource(R.string.settings_section_network),
        icon = {
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.setting_bluetooth),
            subtitle = stringResource(R.string.setting_bluetooth_desc),
            checked = state.bleEnabled,
            onCheckedChange = { viewModel.setBleEnabled(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        SettingsItem(
            title = stringResource(R.string.setting_bluetooth_status_title),
            subtitle = if (state.bleEnabled) stringResource(R.string.setting_bluetooth_status_active) else stringResource(R.string.setting_bluetooth_status_inactive),
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenBleSheet
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
    onClearCache: () -> Unit,
    onOpenBackup: () -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_group_app),
        icon = {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SettingsItem(
            title = stringResource(R.string.setting_language),
            subtitle = if (state.appLanguage == "ar") stringResource(R.string.settings_language_arabic) else stringResource(R.string.settings_language_english),
            leadingIcon = {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenLanguage
        )

        SettingsItem(
            title = stringResource(R.string.setting_font_size),
            subtitle = "${(state.fontSizeScale * 100).toInt()}%",
            leadingIcon = {
                Icon(
                    Icons.Filled.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenFontSize
        )

        SwitchSettingItem(
            title = stringResource(R.string.setting_notifications),
            subtitle = if (state.notificationsEnabled) stringResource(R.string.settings_status_enabled) else stringResource(R.string.settings_status_disabled),
            checked = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationsEnabled(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        SwitchSettingItem(
            title = stringResource(R.string.setting_haptic_feedback),
            subtitle = stringResource(R.string.setting_haptic_feedback_desc),
            checked = state.hapticFeedbackEnabled,
            onCheckedChange = { viewModel.setHapticFeedback(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        SwitchSettingItem(
            title = stringResource(R.string.setting_notification_sound),
            subtitle = stringResource(R.string.setting_notification_sound_desc),
            checked = state.notificationSound,
            enabled = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationSound(it) },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        SwitchSettingItem(
            title = stringResource(R.string.setting_vibration),
            subtitle = stringResource(R.string.setting_vibration_desc),
            checked = state.notificationVibrate,
            enabled = state.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationVibrate(it) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        SettingsItem(
            title = stringResource(R.string.setting_clear_cache),
            subtitle = stringResource(R.string.setting_clear_cache_desc),
            leadingIcon = {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onClearCache
        )

        SettingsItem(
            title = stringResource(R.string.settings_backup_title),
            subtitle = stringResource(R.string.settings_backup_desc),
            leadingIcon = {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenBackup
        )
    }
}

@Composable
fun AboutSection(
    appVersion: String,
    haptics: PremiumHaptics,
    onDeveloperModeClick: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenCredits: () -> Unit
) {
    val versionTapCount = remember { mutableIntStateOf(0) }
    val lastTapTime = remember { mutableLongStateOf(0L) }

    SettingsSection(
        title = stringResource(R.string.settings_section_info),
        icon = {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SettingsItem(
            title = stringResource(R.string.setting_app_version),
            subtitle = appVersion,
            leadingIcon = {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
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
            }
        )

        SettingsItem(
            title = stringResource(R.string.settings_label_github_repo),
            subtitle = stringResource(R.string.settings_label_github_desc),
            leadingIcon = {
                Icon(
                    Icons.Filled.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenGithub
        )

        SettingsItem(
            title = stringResource(R.string.settings_label_credits),
            subtitle = stringResource(R.string.settings_label_credits_desc),
            leadingIcon = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onOpenCredits
        )
    }
}
