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
import com.p2p.meshify.ui.components.ExpressivePulseHeader
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
 * Uses ListItem-based layout with ColorPicker and FontPicker.
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

    // MD3E Settings
    val shapeStyle by viewModel.shapeStyle.collectAsState()
    val motionPreset by viewModel.motionPreset.collectAsState()
    val motionScale by viewModel.motionScale.collectAsState()
    val fontFamilyPreset by viewModel.fontFamilyPreset.collectAsState()
    val customFontUri by viewModel.customFontUri.collectAsState()
    val bubbleStyle by viewModel.bubbleStyle.collectAsState()
    val visualDensity by viewModel.visualDensity.collectAsState()
    val seedColorInt by viewModel.seedColor.collectAsState()

    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    val scrollState = rememberScrollState()

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

            // MD3E Expressive Header with static Morphing (no rotation)
            ExpressivePulseHeader(
                shapes = listOf(
                    MD3EShapes.Sunny,
                    MD3EShapes.Breezy,
                    MD3EShapes.Pentagon,
                    MD3EShapes.Blob,
                    MD3EShapes.Burst,
                    MD3EShapes.Clover,
                    MD3EShapes.Circle
                )
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            Spacer(modifier = Modifier.height(32.dp))

            // SECTION: IDENTITY
            SettingsSection(title = stringResource(R.string.settings_section_identity)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.setting_display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.updateDisplayName(nameInput) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.btn_save_changes))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: APPEARANCE (THEME)
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                // Theme Mode
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme_mode)) },
                    supportingContent = { Text("Light, Dark, or System") },
                    leadingContent = {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                val modes = ThemeMode.values()
                val haptic = LocalHapticFeedback.current
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setThemeMode(mode)
                            },
                            selected = themeMode == mode
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Dynamic Colors
                PreferenceSwitch(
                    label = stringResource(R.string.settings_dynamic_colors),
                    description = stringResource(R.string.settings_dynamic_colors_desc),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Seed Color Picker (only shown when dynamic color is off)
                if (!dynamicColor) {
                    Text(
                        text = stringResource(R.string.settings_seed_color),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorPicker(
                        selectedColor = Color(seedColorInt),
                        onColorSelected = viewModel::setSeedColor
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Custom Font Picker
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_custom_font)) },
                    supportingContent = {
                        Text(customFontUri?.let { 
                            val fileName = it.substringAfterLast('/')
                            stringResource(R.string.font_file_selected, fileName)
                        } ?: stringResource(R.string.no_custom_font))
                    },
                    leadingContent = {
                        Icon(Icons.Default.FontDownload, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        if (customFontUri != null) {
                            FilledTonalIconButton(onClick = { viewModel.setCustomFontUri(null) }) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { fontPickerLauncher.launch("font/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_upload_font))
                    }
                    if (customFontUri != null) {
                        FilledTonalButton(
                            onClick = { viewModel.setCustomFontUri(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_clear_font))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E SHAPE MORPHING
            SettingsSection(title = stringResource(R.string.settings_shape_style)) {
                Text(stringResource(R.string.settings_shape_style_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                ShapeStyle.values().forEach { style ->
                    ShapeSelectorItem(
                        style = style,
                        selected = shapeStyle == style,
                        onClick = { viewModel.setShapeStyle(style) }
                    )
                    if (style != ShapeStyle.values().last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E MOTION SYSTEM
            SettingsSection(title = stringResource(R.string.settings_motion_system)) {
                Text(stringResource(R.string.settings_motion_system_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                MotionPreset.values().forEach { preset ->
                    MotionPresetItem(
                        preset = preset,
                        selected = motionPreset == preset,
                        onClick = { viewModel.setMotionPreset(preset) }
                    )
                    if (preset != MotionPreset.values().last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.settings_motion_scale, String.format("%.2f", motionScale)), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = motionScale,
                    onValueChange = viewModel::setMotionScale,
                    valueRange = 0.5f..2.0f,
                    steps = 6
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E TYPOGRAPHY
            SettingsSection(title = stringResource(R.string.settings_typography)) {
                Text(stringResource(R.string.settings_typography_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                FontFamilyPreset.values().forEach { family ->
                    FontFamilySelectorItem(
                        family = family,
                        selected = fontFamilyPreset == family,
                        onClick = { viewModel.setFontFamilyPreset(family) }
                    )
                    if (family != FontFamilyPreset.values().last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E CHAT BUBBLES
            SettingsSection(title = stringResource(R.string.settings_chat_bubbles)) {
                Text(stringResource(R.string.settings_chat_bubbles_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                BubbleStyle.values().forEach { style ->
                    BubbleStyleSelectorItem(
                        style = style,
                        selected = bubbleStyle == style,
                        onClick = { viewModel.setBubbleStyle(style) }
                    )
                    if (style != BubbleStyle.values().last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E VISUAL DENSITY
            SettingsSection(title = stringResource(R.string.settings_visual_density)) {
                Text(stringResource(R.string.settings_visual_density_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.settings_density_label, String.format("%.2f", visualDensity)), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = visualDensity,
                    onValueChange = viewModel::setVisualDensity,
                    valueRange = 0.8f..1.5f,
                    steps = 7
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: PRIVACY
            SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                PreferenceSwitch(
                    label = stringResource(R.string.settings_visibility),
                    description = stringResource(R.string.settings_visibility_desc),
                    checked = networkVisible,
                    onCheckedChange = viewModel::setNetworkVisibility
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: INFO
            SettingsSection(title = stringResource(R.string.settings_section_info)) {
                InfoItem(label = stringResource(R.string.setting_device_id), value = deviceId, icon = Icons.Default.Fingerprint)
                Spacer(modifier = Modifier.height(16.dp))
                InfoItem(label = stringResource(R.string.setting_app_version), value = appVersion, icon = Icons.Default.Info)
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
        ColorPresetTeal,
        ColorPresetPurple,
        ColorPresetGreen,
        ColorPresetRed,
        ColorPresetAmber,
        ColorPresetBlue,
        ColorPresetPink,
        ColorPresetNeutral
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier.border(2.dp, color.copy(alpha = 0.3f), CircleShape)
                        }
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onColorSelected(color)
                    }
            )
        }
    }
}

@Composable
fun ShapeSelectorItem(
    style: ShapeStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        headlineContent = {
            Text(
                text = style.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = if (selected) stringResource(R.string.settings_shape_active)
                       else stringResource(R.string.settings_shape_tap_to_select),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) primaryColor else onSurfaceVariant
            )
        },
        leadingContent = {
            val shape = MD3EShapes.getShape(style)
            val staticMorph = remember(shape) { Morph(shape, shape) }
            val morphShape = remember(staticMorph) { com.p2p.meshify.ui.components.MorphPolygonShape(staticMorph, 0f) }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(morphShape)
                    .background(if (selected) primaryColor else onSurfaceVariant)
            )
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            } else {
                RadioButton(selected = false, onClick = null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .then(
                if (selected) {
                    Modifier.background(primaryColor.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
fun MotionPresetItem(
    preset: MotionPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        headlineContent = {
            Text(
                text = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = when (preset) {
                    MotionPreset.GENTLE -> stringResource(R.string.settings_motion_gentle)
                    MotionPreset.STANDARD -> stringResource(R.string.settings_motion_standard)
                    MotionPreset.SNAPPY -> stringResource(R.string.settings_motion_snappy)
                    MotionPreset.BOUNCY -> stringResource(R.string.settings_motion_bouncy)
                },
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = when (preset) {
                    MotionPreset.GENTLE -> Icons.Default.SlowMotionVideo
                    MotionPreset.STANDARD -> Icons.Default.Speed
                    MotionPreset.SNAPPY -> Icons.Default.FastForward
                    MotionPreset.BOUNCY -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
                tint = if (selected) primaryColor else onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .then(
                if (selected) {
                    Modifier.background(primaryColor.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
fun FontFamilySelectorItem(
    family: FontFamilyPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val fontFamily = MD3EFontFamilies.getFontFamily(family)

    ListItem(
        headlineContent = {
            Text(
                text = family.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = when (family) {
                    FontFamilyPreset.ROBOTO -> stringResource(R.string.settings_font_roboto)
                    FontFamilyPreset.POPPINS -> stringResource(R.string.settings_font_poppins)
                    FontFamilyPreset.LORA -> stringResource(R.string.settings_font_lora)
                    FontFamilyPreset.MONTSERRAT -> stringResource(R.string.settings_font_montserrat)
                    FontFamilyPreset.PLAYFAIR -> stringResource(R.string.settings_font_playfair)
                    FontFamilyPreset.INTER -> stringResource(R.string.settings_font_inter)
                },
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(Icons.Default.FontDownload, null, tint = if (selected) primaryColor else onSurfaceVariant)
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .then(
                if (selected) {
                    Modifier.background(primaryColor.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
fun BubbleStyleSelectorItem(
    style: BubbleStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        headlineContent = {
            Text(
                text = style.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(
                    when (style) {
                        BubbleStyle.ROUNDED -> 20.dp
                        BubbleStyle.TAILED -> 16.dp
                        BubbleStyle.SQUARCLES -> 8.dp
                        BubbleStyle.ORGANIC -> 24.dp
                    }
                ),
                color = if (selected) primaryColor else onSurfaceVariant
            ) {}
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .then(
                if (selected) {
                    Modifier.background(primaryColor.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
fun PreferenceSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(text = label, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(
                imageVector = if (checked) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = { Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) },
        leadingContent = {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    )
}
