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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.toPath
import com.p2p.meshify.R
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.core.util.Logger

/**
 * Fortified Settings Screen with Corrected Morphing and Localization.
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

            ExpressivePulseHeader(displayName)

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
                Button(
                    onClick = { viewModel.updateDisplayName(nameInput) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = stringResource(R.string.btn_save_changes))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: PREFERENCES (THEME)
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
fun ExpressivePulseHeader(name: String) {
    val haptic = LocalHapticFeedback.current
    val shapes = remember {
        listOf(
            RoundedPolygon.star(numVerticesPerRadius = 10, innerRadius = 0.65f, rounding = CornerRounding(0.2f)),
            RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = 0.85f, rounding = CornerRounding(0.3f)),
            RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.2f)),
            RoundedPolygon.star(numVerticesPerRadius = 2, innerRadius = 0.3f, rounding = CornerRounding(0.9f)),
            RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = CornerRounding(0.15f)),
            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.7f, rounding = CornerRounding(0.4f)),
            RoundedPolygon.circle(numVertices = 12)
        )
    }

    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex = (currentShapeIndex + 1) % shapes.size
    val morph = remember(currentShapeIndex) { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Restart),
        label = "Progress"
    )

    LaunchedEffect(progress) {
        if (progress >= 0.98f) currentShapeIndex = nextShapeIndex
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val androidPath = remember { AndroidPath() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                .drawBehind {
                androidPath.reset()
                try {
                    morph.populatePathSecure(progress, androidPath)
                } catch (e: Exception) { }
                val sizeValue = size.minDimension / 2.2f
                scale(sizeValue) { drawPath(path = androidPath.asComposePath(), color = containerColor) }
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(56.dp), tint = primaryColor)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Secure extension to handle Morph path population.
 */
fun Morph.populatePathSecure(progress: Float, path: AndroidPath) {
    try {
        val method = this.javaClass.getDeclaredMethod("asPath", Float::class.java, AndroidPath::class.java)
        method.isAccessible = true
        method.invoke(this, progress, path)
    } catch (e: Exception) {
        Logger.e("Morphing failed: ${e.message}")
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
