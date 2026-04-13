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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
import com.p2p.meshify.domain.model.TransportMode
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
    onBackClick: () -> Unit,
    onDeveloperModeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalPremiumHaptics.current

    // Unified SettingsUiState — single collectAsState replacing 16+ individual flows
    val state by viewModel.settingsUiState.collectAsState()
    val appVersion = viewModel.appVersion

    // Derived state from unified state
    val seedColor = remember(state.seedColor) { Color(state.seedColor) }

    // UI State for dialogs and bottom sheets
    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showMotionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showBleSheet by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showShapeDialog by remember { mutableStateOf(false) }
    var showMotionScaleDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var showBubbleDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(state.displayName) }
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Avatar file from hash
    val avatarFile = remember(state.avatarHash) {
        state.avatarHash?.let { hash ->
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
                        text = stringResource(R.string.screen_settings_title),
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_content_desc_back)
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
                        contentDescription = stringResource(R.string.settings_content_desc_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.settings_avatar),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Display Name
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            // Device ID (short)
            Text(
                text = if (state.deviceIdLoaded) state.deviceId.take(8).uppercase() else stringResource(R.string.settings_device_id_loading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xl))

            // === SECTION 1: IDENTITY & PROFILE ===
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_identity)) {
                // Display Name Item
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_display_name),
                    subtitle = state.displayName,
                    icon = Icons.Default.Person,
                    onClick = {
                        haptics.perform(HapticPattern.Pop) // UX04: Haptic feedback
                        nameInput = state.displayName
                        showNameDialog = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Device ID Item (Copy full ID on click)
                val deviceTitle = stringResource(R.string.setting_device_id)
                val deviceSuffix = stringResource(R.string.settings_label_device_id_suffix)
                MeshifySettingsItem(
                    title = deviceTitle,
                    subtitle = state.deviceId.take(8).uppercase() + deviceSuffix,
                    icon = Icons.Default.Fingerprint,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(deviceTitle, state.deviceId)
                        clipboard.setPrimaryClip(clip)
                        haptics.perform(HapticPattern.Success) // UX04: Haptic feedback on copy
                    }
                )
            }

            // === SECTION 2: LOOK & FEEL ===
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
                // Theme Mode
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = when (state.themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    },
                    icon = Icons.Default.Palette,
                    onClick = {
                        haptics.perform(HapticPattern.Pop) // UX04: Haptic feedback
                        showThemeSheet = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Dynamic Colors
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_dynamic_colors),
                    subtitle = stringResource(R.string.settings_dynamic_colors_desc),
                    icon = Icons.Default.ColorLens,
                    trailing = {
                        Switch(
                            checked = state.dynamicColorEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setDynamicColor(it)
                            }
                        )
                    }
                )

                // Seed Color Picker (only when dynamic colors is OFF)
                if (!state.dynamicColorEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeshifyDesignSystem.Spacing.Md)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_label_accent_color),
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
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_md3e_expressive)) {
                // Motion Physics
                val gentleLabel = stringResource(R.string.settings_motion_gentle_label)
                val standardLabel = stringResource(R.string.settings_motion_standard_label)
                val snappyLabel = stringResource(R.string.settings_motion_snappy_label)
                val bouncyLabel = stringResource(R.string.settings_motion_bouncy_label)
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_motion_system),
                    subtitle = when (state.motionPreset) {
                        MotionPreset.GENTLE -> gentleLabel
                        MotionPreset.STANDARD -> standardLabel
                        MotionPreset.SNAPPY -> snappyLabel
                        MotionPreset.BOUNCY -> bouncyLabel
                    },
                    icon = Icons.Default.Animation,
                    onClick = { showMotionDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Shape Style
                val sunnyLabel = stringResource(R.string.settings_shape_label_sunny)
                val breezyLabel = stringResource(R.string.settings_shape_label_breezy)
                val pentagonLabel = stringResource(R.string.settings_shape_label_pentagon)
                val blobLabel = stringResource(R.string.settings_shape_label_blob)
                val burstLabel = stringResource(R.string.settings_shape_label_burst)
                val cloverLabel = stringResource(R.string.settings_shape_label_clover)
                val circleLabel = stringResource(R.string.settings_shape_label_circle)
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_shape_style),
                    subtitle = when (state.shapeStyle) {
                        ShapeStyle.SUNNY -> sunnyLabel
                        ShapeStyle.BREEZY -> breezyLabel
                        ShapeStyle.PENTAGON -> pentagonLabel
                        ShapeStyle.BLOB -> blobLabel
                        ShapeStyle.BURST -> burstLabel
                        ShapeStyle.CLOVER -> cloverLabel
                        ShapeStyle.CIRCLE -> circleLabel
                    },
                    icon = Icons.Default.Star,
                    onClick = { showShapeDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Motion Scale
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_motion_scale),
                    subtitle = stringResource(R.string.settings_motion_scale_desc) + " (${state.motionScale}x)",
                    icon = Icons.Default.AspectRatio,
                    onClick = { showMotionScaleDialog = true }
                )
            }

            // === SECTION 4: PRIVACY & VISIBILITY ===
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_visibility),
                    subtitle = stringResource(R.string.settings_visibility_desc),
                    icon = Icons.Default.Visibility,
                    trailing = {
                        Switch(
                            checked = state.isNetworkVisible,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNetworkVisibility(it)
                            }
                        )
                    }
                )
            }

            // === SECTION 4.5: BLUETOOTH TRANSPORT ===
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_network)) {
                // Bluetooth Toggle
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_bluetooth),
                    subtitle = stringResource(R.string.setting_bluetooth_desc),
                    icon = Icons.Default.Bluetooth,
                    trailing = {
                        Switch(
                            checked = state.bleEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setBleEnabled(it)
                            }
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // BLE Status row (opens bottom sheet)
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_bluetooth_status_title),
                    subtitle = if (state.bleEnabled) stringResource(R.string.setting_bluetooth_status_active) else stringResource(R.string.setting_bluetooth_status_inactive),
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showBleSheet = true
                    }
                )
            }

            // SECTION 5: APP SETTINGS
            MeshifySettingsGroup(title = stringResource(R.string.settings_group_app)) {
                // Language
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_language),
                    subtitle = if (state.appLanguage == "ar") stringResource(R.string.settings_language_arabic) else stringResource(R.string.settings_language_english),
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Font Size
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_font_size),
                    subtitle = "${(state.fontSizeScale * 100).toInt()}%",
                    icon = Icons.Default.TextFields,
                    onClick = { showFontSizeDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notifications
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_notifications),
                    subtitle = if (state.notificationsEnabled) stringResource(R.string.settings_status_enabled) else stringResource(R.string.settings_status_disabled),
                    icon = Icons.Default.Notifications,
                    trailing = {
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationsEnabled(it)
                            }
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Haptic Feedback
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_haptic_feedback),
                    subtitle = stringResource(R.string.setting_haptic_feedback_desc),
                    icon = Icons.Default.Vibration,
                    trailing = {
                        Switch(
                            checked = state.hapticFeedbackEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setHapticFeedback(it)
                            }
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notification Sound
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_notification_sound),
                    subtitle = stringResource(R.string.setting_notification_sound_desc),
                    icon = Icons.Filled.VolumeUp,
                    trailing = {
                        Switch(
                            checked = state.notificationSound,
                            enabled = state.notificationsEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationSound(it)
                            }
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notification Vibrate
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_vibration),
                    subtitle = stringResource(R.string.setting_vibration_desc),
                    icon = Icons.Default.Vibration,
                    trailing = {
                        Switch(
                            checked = state.notificationVibrate,
                            enabled = state.notificationsEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationVibrate(it)
                            }
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Clear Cache
                val cacheSuccess = stringResource(R.string.settings_cache_cleared_success)
                val cacheError = stringResource(R.string.settings_cache_cleared_error)
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_clear_cache),
                    subtitle = stringResource(R.string.setting_clear_cache_desc),
                    icon = Icons.Default.DeleteSweep,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        viewModel.clearCache { result ->
                            cacheStatus = if (result.isSuccess) cacheSuccess else cacheError
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Backup/Restore
                MeshifySettingsItem(
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    icon = Icons.Default.CloudUpload,
                    onClick = { showBackupDialog = true }
                )
            }

            // === SECTION 6: ABOUT ===
            MeshifySettingsGroup(title = stringResource(R.string.settings_section_info)) {
                // App Version with easter egg (tap 7 times to unlock developer mode)
                val versionTapCount = remember { mutableIntStateOf(0) }
                val lastTapTime = remember { mutableLongStateOf(0L) }
                
                MeshifySettingsItem(
                    title = stringResource(R.string.setting_app_version),
                    subtitle = appVersion,
                    icon = Icons.Default.Info,
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = stringResource(R.string.settings_label_github_repo),
                    subtitle = stringResource(R.string.settings_label_github_desc),
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
                    title = stringResource(R.string.settings_label_credits),
                    subtitle = stringResource(R.string.settings_label_credits_desc),
                    icon = Icons.Default.Favorite,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showCreditsDialog = true
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
            title = stringResource(R.string.settings_dialog_edit_name),
            value = nameInput,
            onValueChange = { nameInput = it },
            onConfirm = { viewModel.updateDisplayName(nameInput) },
            onDismiss = { showNameDialog = false },
            placeholder = stringResource(R.string.settings_dialog_name_placeholder),
            errorText = state.displayNameError
        )
    }

    // Theme Selection Bottom Sheet
    if (showThemeSheet) {
        ThemeSelectionBottomSheet(
            currentTheme = state.themeMode,
            onThemeSelected = {
                haptics.perform(HapticPattern.Selection)
                viewModel.setThemeMode(it)
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    // Motion Preset Selection Dialog
    if (showMotionDialog) {
        val gentleLabel = stringResource(R.string.settings_motion_gentle_label)
        val standardLabel = stringResource(R.string.settings_motion_standard_label)
        val snappyLabel = stringResource(R.string.settings_motion_snappy_label)
        val bouncyLabel = stringResource(R.string.settings_motion_bouncy_label)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_dialog_motion_preset),
            options = MotionPreset.entries,
            selectedOption = state.motionPreset,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setMotionPreset(it)
            },
            onDismiss = { showMotionDialog = false },
            optionLabel = {
                when (it) {
                    MotionPreset.GENTLE -> gentleLabel
                    MotionPreset.STANDARD -> standardLabel
                    MotionPreset.SNAPPY -> snappyLabel
                    MotionPreset.BOUNCY -> bouncyLabel
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

    // Language Selection Dialog
    if (showLanguageDialog) {
        val arabicLabel = stringResource(R.string.settings_language_arabic)
        val englishLabel = stringResource(R.string.settings_language_english)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_dialog_select_language),
            options = listOf("en", "ar"),
            selectedOption = state.appLanguage,
            onOptionSelected = { lang ->
                haptics.perform(HapticPattern.Pop)
                viewModel.setAppLanguage(lang)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
            optionLabel = { lang -> if (lang == "ar") arabicLabel else englishLabel },
            optionIcon = { lang -> if (lang == "ar") Icons.Default.Language else Icons.Default.Translate }
        )
    }

    // Font Size Dialog
    if (showFontSizeDialog) {
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_dialog_font_size),
            options = listOf(0.8f, 1.0f, 1.2f, 1.5f),
            selectedOption = state.fontSizeScale,
            onOptionSelected = { scale ->
                haptics.perform(HapticPattern.Pop)
                viewModel.setFontSizeScale(scale)
                showFontSizeDialog = false
            },
            onDismiss = { showFontSizeDialog = false },
            optionLabel = { scale -> "${(scale * 100).toInt()}%" },
            optionIcon = { _ -> Icons.Default.TextFields }
        )
    }

    // Backup & Restore Dialog
    if (showBackupDialog) {
        val resources = LocalContext.current.resources
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.settings_backup_title)) },
            text = { Text(stringResource(R.string.settings_backup_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        viewModel.exportBackup { result ->
                            backupStatus = result.fold(
                                onSuccess = { resources.getString(R.string.settings_backup_export_success) },
                                onFailure = { resources.getString(R.string.settings_backup_export_error, it.message) }
                            )
                        }
                        showBackupDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_backup_export))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Import functionality (requires file picker)
                        haptics.perform(HapticPattern.Pop)
                        backupStatus = resources.getString(R.string.settings_backup_import_soon)
                        showBackupDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_backup_import))
                }
            }
        )
    }

    // Cache Status Message
    if (cacheStatus != null) {
        LaunchedEffect(cacheStatus) {
            kotlinx.coroutines.delay(3000)
            cacheStatus = null
        }

        Snackbar(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
            action = {
                IconButton(onClick = { cacheStatus = null }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_close), tint = Color.White)
                }
            }
        ) {
            Text(cacheStatus ?: "")
        }
    }

    // Backup Status Message
    if (backupStatus != null) {
        LaunchedEffect(backupStatus) {
            kotlinx.coroutines.delay(3000)
            backupStatus = null
        }

        Snackbar(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
            action = {
                IconButton(onClick = { backupStatus = null }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_close), tint = Color.White)
                }
            }
        ) {
            Text(backupStatus ?: "")
        }
    }

    // BLE Status Bottom Sheet
    if (showBleSheet) {
        BleStatusBottomSheet(
            bleEnabled = state.bleEnabled,
            transportMode = state.transportMode,
            onModeSelected = { viewModel.setTransportMode(it) },
            onDismiss = { showBleSheet = false }
        )
    }

    // Credits Dialog
    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_credits_title),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = { Text(stringResource(R.string.settings_credits_text)) },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text(stringResource(R.string.dialog_btn_close))
                }
            }
        )
    }

    // Shape Style Dialog
    if (showShapeDialog) {
        val sunnyLabel = stringResource(R.string.settings_shape_label_sunny)
        val breezyLabel = stringResource(R.string.settings_shape_label_breezy)
        val pentagonLabel = stringResource(R.string.settings_shape_label_pentagon)
        val blobLabel = stringResource(R.string.settings_shape_label_blob)
        val burstLabel = stringResource(R.string.settings_shape_label_burst)
        val cloverLabel = stringResource(R.string.settings_shape_label_clover)
        val circleLabel = stringResource(R.string.settings_shape_label_circle)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_shape_style),
            options = ShapeStyle.entries,
            selectedOption = state.shapeStyle,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setShapeStyle(it)
            },
            onDismiss = { showShapeDialog = false },
            optionLabel = {
                when (it) {
                    ShapeStyle.SUNNY -> sunnyLabel
                    ShapeStyle.BREEZY -> breezyLabel
                    ShapeStyle.PENTAGON -> pentagonLabel
                    ShapeStyle.BLOB -> blobLabel
                    ShapeStyle.BURST -> burstLabel
                    ShapeStyle.CLOVER -> cloverLabel
                    ShapeStyle.CIRCLE -> circleLabel
                }
            },
            optionIcon = { Icons.Default.Star }
        )
    }

    // Motion Scale Dialog
    if (showMotionScaleDialog) {
        val scaleOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_motion_scale),
            options = scaleOptions,
            selectedOption = state.motionScale.coerceIn(0.5f, 2.0f),
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setMotionScale(it)
            },
            onDismiss = { showMotionScaleDialog = false },
            optionLabel = { "${it}x" },
            optionIcon = { Icons.Default.AspectRatio }
        )
    }

    // Font Family Dialog
    if (showFontFamilyDialog) {
        val robotoLabel = stringResource(R.string.settings_font_roboto)
        val poppinsLabel = stringResource(R.string.settings_font_label_poppins)
        val loraLabel = stringResource(R.string.settings_font_label_lora)
        val montserratLabel = stringResource(R.string.settings_font_label_montserrat)
        val playfairLabel = stringResource(R.string.settings_font_label_playfair)
        val interLabel = stringResource(R.string.settings_font_label_inter)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_font_family),
            options = FontFamilyPreset.entries,
            selectedOption = state.fontFamilyPreset,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setFontFamilyPreset(it)
            },
            onDismiss = { showFontFamilyDialog = false },
            optionLabel = {
                when (it) {
                    FontFamilyPreset.ROBOTO -> robotoLabel
                    FontFamilyPreset.POPPINS -> poppinsLabel
                    FontFamilyPreset.LORA -> loraLabel
                    FontFamilyPreset.MONTSERRAT -> montserratLabel
                    FontFamilyPreset.PLAYFAIR -> playfairLabel
                    FontFamilyPreset.INTER -> interLabel
                }
            },
            optionIcon = { Icons.Default.TextFields }
        )
    }

    // Bubble Style Dialog
    if (showBubbleDialog) {
        val roundedLabel = stringResource(R.string.settings_bubble_rounded)
        val tailedLabel = stringResource(R.string.settings_bubble_label_tailed)
        val squarclesLabel = stringResource(R.string.settings_bubble_label_squarcles)
        val organicLabel = stringResource(R.string.settings_bubble_label_organic)
        MeshifySelectionDialog(
            title = stringResource(R.string.settings_bubble_style),
            options = BubbleStyle.entries,
            selectedOption = state.bubbleStyle,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setBubbleStyle(it)
            },
            onDismiss = { showBubbleDialog = false },
            optionLabel = {
                when (it) {
                    BubbleStyle.ROUNDED -> roundedLabel
                    BubbleStyle.TAILED -> tailedLabel
                    BubbleStyle.SQUARCLES -> squarclesLabel
                    BubbleStyle.ORGANIC -> organicLabel
                }
            },
            optionIcon = { Icons.Default.ChatBubble }
        )
    }
}

/**
 * BLE Status Bottom Sheet — shows BLE state, connected peers, and transport mode selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BleStatusBottomSheet(
    bleEnabled: Boolean,
    transportMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalPremiumHaptics.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(
                text = stringResource(R.string.ble_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Md)
            )

            // Status Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MeshifyDesignSystem.Shapes.CardSmall,
                color = if (bleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
                    ) {
                        Icon(
                            imageVector = if (bleEnabled) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (bleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (bleEnabled) stringResource(R.string.ble_sheet_active) else stringResource(R.string.ble_sheet_inactive),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (bleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Transport Mode Selector
            Text(
                text = stringResource(R.string.ble_transport_mode_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Sm)
            )

            // Mode chips in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportMode.entries.forEach { mode ->
                    val isSelected = mode == transportMode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            onModeSelected(mode)
                        },
                        label = {
                            Text(
                                text = when (mode) {
                                    TransportMode.MULTI_PATH -> stringResource(R.string.ble_transport_mode_multipath)
                                    TransportMode.LAN_ONLY -> stringResource(R.string.ble_transport_mode_lan)
                                    TransportMode.BLE_ONLY -> stringResource(R.string.ble_transport_mode_ble)
                                    TransportMode.AUTO -> stringResource(R.string.ble_transport_mode_auto)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
        }
    }
}
