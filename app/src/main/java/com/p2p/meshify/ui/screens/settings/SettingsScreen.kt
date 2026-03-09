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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.R
import com.p2p.meshify.domain.model.*
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBackClick: () -> Unit) {
    val displayName by viewModel.displayName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val networkVisible by viewModel.isNetworkVisible.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val appVersion = viewModel.appVersion
    val shapeStyle by viewModel.shapeStyle.collectAsState()
    val motionPreset by viewModel.motionPreset.collectAsState()
    val fontFamilyPreset by viewModel.fontFamilyPreset.collectAsState()
    val bubbleStyle by viewModel.bubbleStyle.collectAsState()
    val visualDensity by viewModel.visualDensity.collectAsState()
    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    val scrollState = rememberScrollState()
    val settingsRepo = viewModel.settingsRepository

    Scaffold(topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.screen_settings_title), fontWeight = FontWeight.Black) }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            ExpressivePulseHeader(size = 100.dp) { Icon(Icons.Default.Person, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(12.dp)); Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(32.dp))
            SettingsGroup(stringResource(R.string.settings_section_identity)) { Box(Modifier.padding(16.dp)) { OutlinedTextField(nameInput, { nameInput = it }, label = { Text(stringResource(R.string.setting_display_name)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), singleLine = true) }; SettingGroupItem(stringResource(R.string.btn_save_changes), icon = { Icon(Icons.Default.Save, null) }, position = ItemPosition.LAST, settingsRepository = settingsRepo, onClick = { viewModel.updateDisplayName(nameInput) }) }
            SettingsGroup(stringResource(R.string.settings_section_appearance)) {
                SettingGroupItem(stringResource(R.string.settings_theme_mode), themeMode.name, icon = { Icon(Icons.Default.Palette, null) }, position = ItemPosition.FIRST, settingsRepository = settingsRepo, onClick = { viewModel.setThemeMode(when(themeMode){ThemeMode.SYSTEM->ThemeMode.LIGHT; ThemeMode.LIGHT->ThemeMode.DARK; else->ThemeMode.SYSTEM}) })
                SettingGroupItem(stringResource(R.string.settings_dynamic_colors), stringResource(R.string.settings_dynamic_colors_desc), icon = { Icon(Icons.Default.ColorLens, null) }, trailing = { Switch(dynamicColor, viewModel::setDynamicColor) }, position = ItemPosition.MIDDLE, settingsRepository = settingsRepo)
                if (!dynamicColor) { Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) { ColorPicker(Color(viewModel.seedColor.collectAsState().value), viewModel::setSeedColor) } }
                SettingGroupItem("Haptic Feedback", "Enable tactile response", icon = { Icon(Icons.Default.TouchApp, null) }, trailing = { Switch(hapticEnabled, viewModel::setHapticFeedback) }, position = ItemPosition.LAST, settingsRepository = settingsRepo)
            }
            SettingsGroup("MD3 Expressive System") {
                SettingGroupItem(stringResource(R.string.settings_shape_style), shapeStyle.name, icon = { Icon(Icons.Default.Category, null) }, position = ItemPosition.FIRST, settingsRepository = settingsRepo, onClick = { viewModel.setShapeStyle(ShapeStyle.entries[(shapeStyle.ordinal + 1) % ShapeStyle.entries.size]) })
                SettingGroupItem(stringResource(R.string.settings_motion_system), motionPreset.name, icon = { Icon(Icons.Default.Animation, null) }, position = ItemPosition.MIDDLE, settingsRepository = settingsRepo, onClick = { viewModel.setMotionPreset(MotionPreset.entries[(motionPreset.ordinal + 1) % MotionPreset.entries.size]) })
                SettingGroupItem(stringResource(R.string.settings_typography), fontFamilyPreset.name, icon = { Icon(Icons.Default.FontDownload, null) }, position = ItemPosition.MIDDLE, settingsRepository = settingsRepo, onClick = { viewModel.setFontFamilyPreset(FontFamilyPreset.entries[(fontFamilyPreset.ordinal + 1) % FontFamilyPreset.entries.size]) })
                SettingGroupItem(stringResource(R.string.settings_chat_bubbles), bubbleStyle.name, icon = { Icon(Icons.Default.ChatBubble, null) }, position = ItemPosition.MIDDLE, settingsRepository = settingsRepo, onClick = { viewModel.setBubbleStyle(BubbleStyle.entries[(bubbleStyle.ordinal + 1) % BubbleStyle.entries.size]) })
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.settings_visual_density), style = MaterialTheme.typography.labelMedium); Slider(visualDensity, viewModel::setVisualDensity, valueRange = 0.8f..1.2f) }
                SettingGroupItem(stringResource(R.string.settings_visibility), stringResource(R.string.settings_visibility_desc), icon = { Icon(Icons.Default.Visibility, null) }, trailing = { Switch(networkVisible, viewModel::setNetworkVisibility) }, position = ItemPosition.LAST, settingsRepository = settingsRepo)
            }
            SettingsGroup(stringResource(R.string.settings_section_info)) { SettingGroupItem(stringResource(R.string.setting_device_id), deviceId, icon = { Icon(Icons.Default.Fingerprint, null) }, position = ItemPosition.FIRST, settingsRepository = settingsRepo); SettingGroupItem(stringResource(R.string.setting_app_version), appVersion, icon = { Icon(Icons.Default.Info, null) }, position = ItemPosition.LAST, settingsRepository = settingsRepo) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(ColorPresetTeal, ColorPresetPurple, ColorPresetGreen, ColorPresetRed, ColorPresetAmber, ColorPresetBlue, ColorPresetPink, ColorPresetNeutral)
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) { colors.forEach { color -> Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(if (selectedColor == color) 3.dp else 1.dp, if (selectedColor == color) MaterialTheme.colorScheme.onSurface else color.copy(0.3f), CircleShape).clickable { onColorSelected(color) }) } }
}
