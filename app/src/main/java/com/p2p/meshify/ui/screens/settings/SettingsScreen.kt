package com.p2p.meshify.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.p2p.meshify.R
import com.p2p.meshify.domain.model.*
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.ColorPresetAmber
import com.p2p.meshify.ui.theme.ColorPresetBlue
import com.p2p.meshify.ui.theme.ColorPresetGreen
import com.p2p.meshify.ui.theme.ColorPresetNeutral
import com.p2p.meshify.ui.theme.ColorPresetPink
import com.p2p.meshify.ui.theme.ColorPresetPurple
import com.p2p.meshify.ui.theme.ColorPresetRed
import com.p2p.meshify.ui.theme.ColorPresetTeal
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MD3EFontFamilies
import com.p2p.meshify.ui.theme.MotionDurations

/**
 * MD3E Redesigned Settings Screen.
 * Updated with LastChat-style grouping and haptics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
    val networkVisible by viewModel.isNetworkVisible.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val appVersion = viewModel.appVersion

    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    val scrollState = rememberScrollState()
    val settingsRepo = viewModel.settingsRepository

    // Font file picker launcher
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setCustomFontUri(it.toString())
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_settings_title),
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // MD3E Expressive Header
            ExpressivePulseHeader(
                size = 100.dp,
                shapes = MD3EShapes.AllShapes
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            Spacer(modifier = Modifier.height(32.dp))

            // SECTION: IDENTITY
            SettingsGroup(title = stringResource(R.string.settings_section_identity)) {
                Box(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.setting_display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                }
                SettingGroupItem(
                    title = stringResource(R.string.btn_save_changes),
                    icon = { Icon(Icons.Default.Save, null) },
                    position = ItemPosition.LAST,
                    settingsRepository = settingsRepo,
                    onClick = { viewModel.updateDisplayName(nameInput) }
                )
            }

            // SECTION: APPEARANCE
            SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
                SettingGroupItem(
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    icon = { Icon(Icons.Default.Palette, null) },
                    position = ItemPosition.FIRST,
                    settingsRepository = settingsRepo,
                    onClick = {
                        val nextMode = when(themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        viewModel.setThemeMode(nextMode)
                    }
                )
                
                SettingGroupItem(
                    title = stringResource(R.string.settings_dynamic_colors),
                    subtitle = stringResource(R.string.settings_dynamic_colors_desc),
                    icon = { Icon(Icons.Default.ColorLens, null) },
                    trailing = {
                        Switch(checked = dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                    },
                    position = if (dynamicColor) ItemPosition.MIDDLE else ItemPosition.MIDDLE,
                    settingsRepository = settingsRepo
                )

                if (!dynamicColor) {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
                        ColorPicker(
                            selectedColor = Color(viewModel.seedColor.collectAsState().value),
                            onColorSelected = viewModel::setSeedColor
                        )
                    }
                }

                SettingGroupItem(
                    title = stringResource(R.string.settings_custom_font),
                    subtitle = stringResource(R.string.settings_custom_font_desc),
                    icon = { Icon(Icons.Default.FontDownload, null) },
                    position = ItemPosition.LAST,
                    settingsRepository = settingsRepo,
                    onClick = { fontPickerLauncher.launch("font/*") }
                )
            }

            // SECTION: NETWORK
            SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
                SettingGroupItem(
                    title = stringResource(R.string.settings_visibility),
                    subtitle = stringResource(R.string.settings_visibility_desc),
                    icon = { Icon(Icons.Default.Visibility, null) },
                    trailing = {
                        Switch(checked = networkVisible, onCheckedChange = viewModel::setNetworkVisibility)
                    },
                    position = ItemPosition.ONLY,
                    settingsRepository = settingsRepo
                )
            }

            // SECTION: INFO
            SettingsGroup(title = stringResource(R.string.settings_section_info)) {
                SettingGroupItem(
                    title = stringResource(R.string.setting_device_id),
                    subtitle = deviceId,
                    icon = { Icon(Icons.Default.Fingerprint, null) },
                    position = ItemPosition.FIRST,
                    settingsRepository = settingsRepo
                )
                SettingGroupItem(
                    title = stringResource(R.string.setting_app_version),
                    subtitle = appVersion,
                    icon = { Icon(Icons.Default.Info, null) },
                    position = ItemPosition.LAST,
                    settingsRepository = settingsRepo
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * MD3E Color Picker - Circular color selection.
 */
@Composable
fun ColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colors = listOf(
        ColorPresetTeal, ColorPresetPurple, ColorPresetGreen, ColorPresetRed,
        ColorPresetAmber, ColorPresetBlue, ColorPresetPink, ColorPresetNeutral
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else color.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onColorSelected(color)
                    }
            )
        }
    }
}
