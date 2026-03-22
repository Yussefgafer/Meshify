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
import androidx.compose.ui.res.stringResource
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
    
    // ✅ New Settings State flows
    val appLanguage by viewModel.appLanguage.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibrate by viewModel.notificationVibrate.collectAsState()

    // UI State for dialogs and bottom sheets
    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showMotionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(displayName) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

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
                        haptics.perform(HapticPattern.Pop) // ✅ UX04: Haptic feedback
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
                    subtitle = deviceId.take(8).uppercase() + stringResource(R.string.settings_label_device_id_suffix),
                    icon = Icons.Default.Fingerprint,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Device ID", deviceId)
                        clipboard.setPrimaryClip(clip)
                        haptics.perform(HapticPattern.Success) // ✅ UX04: Haptic feedback on copy
                    }
                )
            }

            // === SECTION 2: LOOK & FEEL ===
            MeshifySettingsGroup(title = context.getString(R.string.settings_section_appearance)) {
                // Theme Mode
                MeshifySettingsItem(
                    title = context.getString(R.string.settings_theme_mode),
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    },
                    icon = Icons.Default.Palette,
                    onClick = {
                        haptics.perform(HapticPattern.Pop) // ✅ UX04: Haptic feedback
                        showThemeSheet = true
                    }
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
            MeshifySettingsGroup(title = stringResource(R.string.settings_label_md3_expressive)) {
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

            // === SECTION 5: APP SETTINGS ✅ ===
            MeshifySettingsGroup(title = "App Settings") {
                // Language
                MeshifySettingsItem(
                    title = "Language",
                    subtitle = if (appLanguage == "ar") "العربية" else "English",
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Font Size
                MeshifySettingsItem(
                    title = "Font Size",
                    subtitle = "${(fontSizeScale * 100).toInt()}%",
                    icon = Icons.Default.TextFields,
                    onClick = { showFontSizeDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notifications
                MeshifySettingsItem(
                    title = "Notifications",
                    subtitle = if (notificationsEnabled) "Enabled" else "Disabled",
                    icon = Icons.Default.Notifications,
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationsEnabled(it)
                            }
                        )
                    },
                    onClick = {
                        viewModel.setNotificationsEnabled(!notificationsEnabled)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notification Sound
                MeshifySettingsItem(
                    title = "Notification Sound",
                    subtitle = "Play sound for new messages",
                    icon = Icons.Default.VolumeUp,
                    trailing = {
                        Switch(
                            checked = notificationSound,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationSound(it)
                            }
                        )
                    },
                    onClick = {
                        if (notificationsEnabled) {
                            viewModel.setNotificationSound(!notificationSound)
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Notification Vibrate
                MeshifySettingsItem(
                    title = "Vibration",
                    subtitle = "Vibrate for new messages",
                    icon = Icons.Default.Vibration,
                    trailing = {
                        Switch(
                            checked = notificationVibrate,
                            onCheckedChange = {
                                haptics.perform(HapticPattern.Tick)
                                viewModel.setNotificationVibrate(it)
                            }
                        )
                    },
                    onClick = {
                        if (notificationsEnabled) {
                            viewModel.setNotificationVibrate(!notificationVibrate)
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Clear Cache
                MeshifySettingsItem(
                    title = "Clear Cache",
                    subtitle = "Free up storage space",
                    icon = Icons.Default.DeleteSweep,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        viewModel.clearCache { result ->
                            backupStatus = if (result.isSuccess) "Cache cleared!" else "Failed to clear cache"
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Backup/Restore
                MeshifySettingsItem(
                    title = "Backup & Restore",
                    subtitle = "Export or import settings",
                    icon = Icons.Default.CloudUpload,
                    onClick = { showBackupDialog = true }
                )
            }

            // === SECTION 6: ABOUT ===
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
            title = stringResource(R.string.settings_dialog_edit_name),
            value = nameInput,
            onValueChange = { nameInput = it },
            onConfirm = { viewModel.updateDisplayName(nameInput) },
            onDismiss = { showNameDialog = false },
            placeholder = stringResource(R.string.settings_dialog_name_placeholder)
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
            title = stringResource(R.string.settings_dialog_motion_preset),
            options = MotionPreset.entries,
            selectedOption = motionPreset,
            onOptionSelected = {
                haptics.perform(HapticPattern.Pop)
                viewModel.setMotionPreset(it)
            },
            onDismiss = { showMotionDialog = false },
            optionLabel = {
                when (it) {
                    MotionPreset.GENTLE -> context.getString(R.string.settings_motion_gentle_label)
                    MotionPreset.STANDARD -> context.getString(R.string.settings_motion_standard_label)
                    MotionPreset.SNAPPY -> context.getString(R.string.settings_motion_snappy_label)
                    MotionPreset.BOUNCY -> context.getString(R.string.settings_motion_bouncy_label)
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

    // ✅ Language Selection Dialog
    if (showLanguageDialog) {
        MeshifySelectionDialog(
            title = "Select Language",
            options = listOf("en", "ar"),
            selectedOption = appLanguage,
            onOptionSelected = { lang ->
                haptics.perform(HapticPattern.Pop)
                viewModel.setAppLanguage(lang)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
            optionLabel = { lang -> if (lang == "ar") "العربية" else "English" },
            optionIcon = { lang -> if (lang == "ar") Icons.Default.Language else Icons.Default.Translate }
        )
    }

    // ✅ Font Size Dialog
    if (showFontSizeDialog) {
        MeshifySelectionDialog(
            title = "Font Size",
            options = listOf(0.8f, 1.0f, 1.2f, 1.5f),
            selectedOption = fontSizeScale,
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

    // ✅ Backup & Restore Dialog
    if (showBackupDialog) {
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
                                onSuccess = { stringResource(R.string.settings_backup_export_success) },
                                onFailure = { stringResource(R.string.settings_backup_export_error, it.message) }
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
                        backupStatus = stringResource(R.string.settings_backup_import_soon)
                        showBackupDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_backup_import))
                }
            }
        )
    }

    // ✅ Backup Status Message
    if (backupStatus != null) {
        LaunchedEffect(backupStatus) {
            kotlinx.coroutines.delay(3000)
            backupStatus = null
        }
        
        Snackbar(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
            action = {
                IconButton(onClick = { backupStatus = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        ) {
            Text(backupStatus ?: "")
        }
    }
}
