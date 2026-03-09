package com.p2p.meshify.ui.screens.settings

import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.util.FileUtils
import java.io.File
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
import androidx.compose.ui.layout.ContentScale
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
    val context = LocalContext.current
    val displayName by viewModel.displayName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val networkVisible by viewModel.isNetworkVisible.collectAsState()
    val avatarHash by viewModel.avatarHash.collectAsState()
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateAvatar(context, it) }
    }

    val avatarFile = remember(avatarHash) {
        avatarHash?.let { hash ->
            FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) }
        }
    }

    Scaffold(
        topBar = { 
            LargeTopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title), fontWeight = FontWeight.Black) }, 
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
            Spacer(Modifier.height(24.dp))
            
            ExpressivePulseHeader(
                size = 140.dp,
                modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") }
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(deviceId.take(8).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.7f))
            
            Spacer(Modifier.height(32.dp))

            // IDENTITY SECTION
            MeshifySectionHeader(stringResource(R.string.settings_section_identity))
            MeshifyCard {
                Box(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.setting_display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (nameInput != displayName) {
                                IconButton(onClick = { viewModel.updateDisplayName(nameInput) }) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }
            }

            // APPEARANCE SECTION
            MeshifySectionHeader(stringResource(R.string.settings_section_appearance))
            MeshifyCard {
                MeshifyListItem(
                    headline = stringResource(R.string.settings_theme_mode),
                    supporting = themeMode.name,
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    onClick = { 
                        viewModel.setThemeMode(when(themeMode){
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            else -> ThemeMode.SYSTEM
                        })
                    }
                )
                
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                
                MeshifyListItem(
                    headline = stringResource(R.string.settings_dynamic_colors),
                    supporting = stringResource(R.string.settings_dynamic_colors_desc),
                    leadingContent = { Icon(Icons.Default.ColorLens, null) },
                    trailingContent = { Switch(dynamicColor, viewModel::setDynamicColor) },
                    onClick = { viewModel.setDynamicColor(!dynamicColor) }
                )

                if (!dynamicColor) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        ColorPicker(Color(viewModel.seedColor.collectAsState().value), viewModel::setSeedColor)
                    }
                }
            }

            // SYSTEM SECTION (MD3E)
            MeshifySectionHeader("MD3 Expressive System")
            MeshifyCard {
                MeshifyListItem(
                    headline = stringResource(R.string.settings_shape_style),
                    supporting = shapeStyle.name,
                    leadingContent = { Icon(Icons.Default.Category, null) },
                    onClick = { viewModel.setShapeStyle(ShapeStyle.entries[(shapeStyle.ordinal + 1) % ShapeStyle.entries.size]) }
                )
                
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

                MeshifyListItem(
                    headline = stringResource(R.string.settings_motion_system),
                    supporting = motionPreset.name,
                    leadingContent = { Icon(Icons.Default.Animation, null) },
                    onClick = { viewModel.setMotionPreset(MotionPreset.entries[(motionPreset.ordinal + 1) % MotionPreset.entries.size]) }
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Height, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.settings_visual_density), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(visualDensity, viewModel::setVisualDensity, valueRange = 0.8f..1.2f, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // INFO SECTION
            MeshifySectionHeader(stringResource(R.string.settings_section_info))
            MeshifyCard {
                MeshifyListItem(
                    headline = stringResource(R.string.setting_device_id),
                    supporting = deviceId,
                    leadingContent = { Icon(Icons.Default.Fingerprint, null) },
                    onClick = { /* Copy ID? */ }
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                MeshifyListItem(
                    headline = stringResource(R.string.setting_app_version),
                    supporting = appVersion,
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    onClick = { }
                )
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(ColorPresetTeal, ColorPresetPurple, ColorPresetGreen, ColorPresetRed, ColorPresetAmber, ColorPresetBlue, ColorPresetPink, ColorPresetNeutral)
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) { colors.forEach { color -> Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(if (selectedColor == color) 3.dp else 1.dp, if (selectedColor == color) MaterialTheme.colorScheme.onSurface else color.copy(0.3f), CircleShape).clickable { onColorSelected(color) }) } }
}
