package com.p2p.meshify.ui.screens.settings

import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.p2p.meshify.R
import com.p2p.meshify.domain.model.*
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.ui.components.ExpressiveButton
import com.p2p.meshify.ui.components.ExpressiveCard
import com.p2p.meshify.ui.components.ExpressivePulseHeader
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MotionDurations

/**
 * MD3E Comprehensive Settings Screen.
 * Full control over all design variables: Motion, Shapes, Fonts, Colors, Bubbles.
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
    val bubbleStyle by viewModel.bubbleStyle.collectAsState()
    val visualDensity by viewModel.visualDensity.collectAsState()

    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    val scrollState = rememberScrollState()

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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // MD3E Expressive Header with Shape Morphing
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
                ExpressiveButton(
                    onClick = { viewModel.updateDisplayName(nameInput) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = stringResource(R.string.btn_save_changes))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: APPEARANCE (THEME)
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val modes = ThemeMode.values()
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            onClick = { viewModel.setThemeMode(mode) },
                            selected = themeMode == mode
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PreferenceSwitch(
                    label = stringResource(R.string.settings_dynamic_colors),
                    description = stringResource(R.string.settings_dynamic_colors_desc),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E SHAPE MORPHING
            SettingsSection(title = "Shape Morphing") {
                Text("Select the active shape for morphing animations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                ShapeStyle.values().forEach { style ->
                    ShapeSelectorItem(
                        style = style,
                        selected = shapeStyle == style,
                        onClick = { viewModel.setShapeStyle(style) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E MOTION SYSTEM
            SettingsSection(title = "Motion System") {
                Text("Configure spring physics and animation speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                MotionPreset.values().forEach { preset ->
                    MotionPresetItem(
                        preset = preset,
                        selected = motionPreset == preset,
                        onClick = { viewModel.setMotionPreset(preset) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Motion Scale: ${String.format("%.2f", motionScale)}x", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = motionScale,
                    onValueChange = viewModel::setMotionScale,
                    valueRange = 0.5f..2.0f,
                    steps = 6
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E TYPOGRAPHY
            SettingsSection(title = "Typography") {
                Text("Choose your preferred font family", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                FontFamilyPreset.values().forEach { family ->
                    FontFamilySelectorItem(
                        family = family,
                        selected = fontFamilyPreset == family,
                        onClick = { viewModel.setFontFamilyPreset(family) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E CHAT BUBBLES
            SettingsSection(title = "Chat Bubbles") {
                Text("Select chat bubble shape style", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                BubbleStyle.values().forEach { style ->
                    BubbleStyleSelectorItem(
                        style = style,
                        selected = bubbleStyle == style,
                        onClick = { viewModel.setBubbleStyle(style) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: MD3E VISUAL DENSITY
            SettingsSection(title = "Visual Density") {
                Text("Adjust UI element sizing and spacing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Density: ${String.format("%.2f", visualDensity)}x", style = MaterialTheme.typography.labelMedium)
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

@Composable
fun ShapeSelectorItem(
    style: ShapeStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview shape
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .drawBehind {
                        val shape = MD3EShapes.getShape(style)
                        val path = AndroidPath()
                        Morph(shape, shape).toPath(0f, path)
                        val sizeValue = size.minDimension / 2.2f
                        scale(sizeValue) {
                            drawPath(
                                path = path.asComposePath(),
                                color = if (selected) primaryColor
                                       else onSurfaceVariant
                            )
                        }
                    }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = if (selected) "Active" else "Tap to select",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) primaryColor
                           else onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = primaryColor
                )
            } else {
                RadioButton(
                    selected = false,
                    onClick = null
                )
            }
        }
    }
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

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (preset) {
                    MotionPreset.GENTLE -> Icons.Default.SlowMotionVideo
                    MotionPreset.STANDARD -> Icons.Default.Speed
                    MotionPreset.SNAPPY -> Icons.Default.FastForward
                    MotionPreset.BOUNCY -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
                tint = if (selected) primaryColor
                       else onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = when (preset) {
                        MotionPreset.GENTLE -> "Calm, subtle animations"
                        MotionPreset.STANDARD -> "Balanced MD3E default"
                        MotionPreset.SNAPPY -> "Quick, responsive"
                        MotionPreset.BOUNCY -> "Playful, elastic"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        }
    }
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

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = family.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = when (family) {
                        FontFamilyPreset.ROBOTO -> "Default system font"
                        FontFamilyPreset.POPTINS -> "Modern geometric"
                        FontFamilyPreset.LORA -> "Elegant serif"
                        FontFamilyPreset.MONTSERRAT -> "Urban contemporary"
                        FontFamilyPreset.PLAYFAIR -> "Display serif"
                        FontFamilyPreset.INTER -> "Clean UI font"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        }
    }
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

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = if (selected) primaryColor
                                   else onSurfaceVariant,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                when (style) {
                                    BubbleStyle.ROUNDED -> 24.dp.toPx()
                                    BubbleStyle.TAILED -> 16.dp.toPx()
                                    BubbleStyle.SQUARCLES -> 8.dp.toPx()
                                    BubbleStyle.ORGANIC -> 32.dp.toPx()
                                }
                            )
                        )
                    }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = primaryColor)
            }
        }
    }
}

@Composable
fun PreferenceSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) { content() }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(40.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
