package com.p2p.meshify.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.core.ui.components.*
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import java.io.File

/**
 * Deep Refactored Settings Screen with MD3E aesthetic.
 * Structured groups, interactive items, and proper ViewModel binding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalPremiumHaptics.current

    // State flows from ViewModel
    val displayName by viewModel.displayName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
    val networkVisible by viewModel.isNetworkVisible.collectAsState()
    val avatarHash by viewModel.avatarHash.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val appVersion = viewModel.appVersion
    val motionPreset by viewModel.motionPreset.collectAsState()
    val visualDensity by viewModel.visualDensity.collectAsState()
    val seedColorInt by viewModel.seedColor.collectAsState()
    val seedColor = remember(seedColorInt) { Color(seedColorInt) }

    // UI State for dialogs and bottom sheets
    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showMotionDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(displayName) }

    val scrollState = rememberScrollState()

    // Avatar file from hash
    val avatarFile = remember(avatarHash) {
        avatarHash?.let { hash ->
            FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) }
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.updateAvatar(context, it) }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = context.getString(R.string.screen_settings_title),
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
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
                .padding(horizontal = MeshifyDesignSystem.Spacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))

            // === EXPRESSIVE PULSE HEADER (Avatar) ===
            ExpressivePulseHeader(
                size = 140.dp,
                modifier = Modifier
                    .scale(animateFloatAsState(1f, spring(dampingRatio = 0.7f, stiffness = 350f)).value)
                    .clickable {
                        haptics.perform(HapticPattern.Pop)
                        imagePickerLauncher.launch("image/*")
                    }
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Display Name
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            // Device ID (short)
            Text(
                text = deviceId.take(8).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xl))

            // === SECTION 1: IDENTITY & PROFILE ===
            MeshifySettingsGroup(title = context.getString(R.string.settings_section_identity)) {
                // Display Name Item
                MeshifySettingsItem(
                    title = context.getString(R.string.setting_display_name),
                    subtitle = displayName,
                    icon = Icons.Default.Person,
                    onClick = {
                        nameInput = displayName
                        showNameDialog = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Device ID Item (Copy full ID on click)
                MeshifySettingsItem(
                    title = context.getString(R.string.setting_device_id),
                    subtitle = deviceId.take(8).uppercase() + " (Tap to copy full ID)",
                    icon = Icons.Default.Fingerprint,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Device ID", deviceId)
                        clipboard.setPrimaryClip(clip)
                        haptics.perform(HapticPattern.Success)
                    }
                )
            }

            // === SECTION 2: LOOK & FEEL ===
            MeshifySettingsGroup(title = context.getString(R.string.settings_section_appearance)) {
                // Theme Mode
                MeshifySettingsItem(
                    title = context.getString(R.string.settings_theme_mode),
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System Default"
                    },
                    icon = Icons.Default.Palette,
                    onClick = { showThemeSheet = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Dynamic Colors
                MeshifySettingsItem(
                    title = context.getString(R.string.settings_dynamic_colors),
                    subtitle = context.getString(R.string.settings_dynamic_colors_desc),
                    icon = Icons.Default.ColorLens,
                    trailing = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setDynamicColor(it)
                            }
                        )
                    },
                    onClick = {
                        viewModel.setDynamicColor(!dynamicColor)
                    }
                )

                // Seed Color Picker (only when dynamic colors is OFF)
                if (!dynamicColor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeshifyDesignSystem.Spacing.Md)
                    ) {
                        Text(
                            text = "Accent Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Sm)
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

            // === SECTION 3: MESH ENGINE (MD3E) ===
            MeshifySettingsGroup(title = "MD3 Expressive System") {
                // Motion Physics
                MeshifySettingsItem(
                    title = context.getString(R.string.settings_motion_system),
                    subtitle = motionPreset.name,
                    icon = Icons.Default.Animation,
                    onClick = { showMotionDialog = true }
                )
            }

            // === SECTION 4: PRIVACY & VISIBILITY ===
            MeshifySettingsGroup(title = context.getString(R.string.settings_section_privacy)) {
                MeshifySettingsItem(
                    title = context.getString(R.string.settings_visibility),
                    subtitle = context.getString(R.string.settings_visibility_desc),
                    icon = Icons.Default.Visibility,
                    trailing = {
                        Switch(
                            checked = networkVisible,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNetworkVisibility(it)
                            }
                        )
                    },
                    onClick = {
                        viewModel.setNetworkVisibility(!networkVisible)
                    }
                )
            }

            // === SECTION 5: ABOUT ===
            MeshifySettingsGroup(title = context.getString(R.string.settings_section_info)) {
                MeshifySettingsItem(
                    title = context.getString(R.string.setting_app_version),
                    subtitle = appVersion,
                    icon = Icons.Default.Info,
                    onClick = { }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "GitHub Repository",
                    subtitle = "View source code and contribute",
                    icon = Icons.Default.Code,
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Yussefgafer/Meshify"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Developer Credits",
                    subtitle = "Built with ❤️ by Jo",
                    icon = Icons.Default.Favorite,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        // Could navigate to credits screen or show dialog
                    }
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xxl))
        }
    }

    // === DIALOGS & BOTTOM SHEETS ===

    // Display Name Dialog
    if (showNameDialog) {
        MeshifyTextInputDialog(
            title = "Edit Display Name",
            value = nameInput,
            onValueChange = { nameInput = it },
            onConfirm = { viewModel.updateDisplayName(nameInput) },
            onDismiss = { showNameDialog = false },
            placeholder = "Enter your name"
        )
    }

    // Theme Selection Bottom Sheet
    if (showThemeSheet) {
        ThemeSelectionBottomSheet(
            currentTheme = themeMode,
            onThemeSelected = {
                haptics.perform(HapticPattern.Selection)
                viewModel.setThemeMode(it)
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    // Motion Preset Selection Dialog
    if (showMotionDialog) {
        MeshifySelectionDialog(
            title = "Select Motion Preset",
            options = MotionPreset.entries,
            selectedOption = motionPreset,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setMotionPreset(it)
            },
            onDismiss = { showMotionDialog = false },
            optionLabel = {
                when (it) {
                    MotionPreset.GENTLE -> "Gentle (Calm)"
                    MotionPreset.STANDARD -> "Standard (Balanced)"
                    MotionPreset.SNAPPY -> "Snappy (Quick)"
                    MotionPreset.BOUNCY -> "Bouncy (Playful)"
                }
            },
            optionIcon = {
                when (it) {
                    MotionPreset.GENTLE -> Icons.Default.SlowMotionVideo
                    MotionPreset.STANDARD -> Icons.Default.Speed
                    MotionPreset.SNAPPY -> Icons.Default.FastForward
                    MotionPreset.BOUNCY -> Icons.Default.TrendingUp
                }
            }
        )
    }
}
