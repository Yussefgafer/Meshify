package com.p2p.meshify.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.MeshifyAvatar
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem

/**
 * Settings screen — structured groups, interactive items, and proper ViewModel binding.
 * Sections and dialogs/sheets are extracted into single-responsibility composables in this package.
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

    val state by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val appVersion = viewModel.appVersion

    // UI State for dialogs and bottom sheets
    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showBleSheet by remember { mutableStateOf(false) }
    var cacheStatus by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.updateAvatar(context, it) }
    }

    // Single SnackbarHostState for all snackbar messages
    val snackbarHostState = remember { SnackbarHostState() }

    val cacheSuccess = stringResource(R.string.settings_cache_cleared_success)
    val cacheError = stringResource(R.string.settings_cache_cleared_error)
    val deviceIdLabel = stringResource(R.string.setting_device_id)
    val deviceIdCopied = stringResource(R.string.settings_device_id_copied)

    val onEditName = {
        haptics.perform(HapticPattern.Pop)
        showNameDialog = true
    }
    val onOpenThemeSheet = {
        haptics.perform(HapticPattern.Pop)
        showThemeSheet = true
    }
    val onOpenBleSheet = {
        haptics.perform(HapticPattern.Pop)
        showBleSheet = true
    }
    val onOpenLanguage = { showLanguageDialog = true }
    val onOpenFontSize = { showFontSizeDialog = true }
    val onClearCache = {
        haptics.perform(HapticPattern.Pop)
        viewModel.clearCache { result ->
            cacheStatus = if (result.isSuccess) cacheSuccess else cacheError
        }
    }
    val onOpenGithub = {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Yussefgafer/Meshify")))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_settings_title),
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.topAppBarColors(
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

            // Avatar inside an expressive tonal container
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        haptics.perform(HapticPattern.Pop)
                        imagePickerLauncher.launch("image/*")
                    }
            ) {
                Box(
                    modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
                    contentAlignment = Alignment.Center
                ) {
                    MeshifyAvatar(
                        avatarHash = state.avatarHash,
                        initials = state.displayName.take(2),
                        size = 120.dp
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Display Name
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))

            // Device ID (short) as a pill — tap to copy the full ID
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = state.deviceIdLoaded) {
                        haptics.perform(HapticPattern.Success)
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(deviceIdLabel, state.deviceId)
                        )
                        Toast.makeText(context, deviceIdCopied, Toast.LENGTH_SHORT).show()
                    }
            ) {
                Text(
                    text = if (state.deviceIdLoaded) state.deviceId.take(8).uppercase() else stringResource(R.string.settings_device_id_loading),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = MeshifyDesignSystem.Spacing.Md,
                        vertical = MeshifyDesignSystem.Spacing.Xxs
                    )
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))

            IdentitySection(
                state = state,
                onEditName = onEditName
            )

            AppearanceSection(
                state = state,
                viewModel = viewModel,
                haptics = haptics,
                onOpenThemeSheet = onOpenThemeSheet
            )

            PrivacySection(
                state = state,
                viewModel = viewModel,
                haptics = haptics
            )

            NetworkSection(
                state = state,
                viewModel = viewModel,
                haptics = haptics,
                onOpenBleSheet = onOpenBleSheet
            )

            AppSettingsSection(
                state = state,
                viewModel = viewModel,
                haptics = haptics,
                onOpenLanguage = onOpenLanguage,
                onOpenFontSize = onOpenFontSize,
                onClearCache = onClearCache
            )

            AboutSection(
                appVersion = appVersion,
                haptics = haptics,
                onDeveloperModeClick = onDeveloperModeClick,
                onOpenGithub = onOpenGithub
            )

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xxl))
        }
    }

    // === DIALOGS & BOTTOM SHEETS ===

    if (showNameDialog) {
        SettingsNameDialog(
            displayName = state.displayName,
            errorText = state.displayNameError,
            onConfirm = { viewModel.updateDisplayName(it) },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showThemeSheet) {
        SettingsThemeSheet(
            currentTheme = state.themeMode,
            onThemeSelected = { viewModel.setThemeMode(it) },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showLanguageDialog) {
        SettingsLanguageDialog(
            appLanguage = state.appLanguage,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                viewModel.setAppLanguage(lang) {
                    (context as? ComponentActivity)?.recreate()
                }
                showLanguageDialog = false
            }
        )
    }

    if (showFontSizeDialog) {
        SettingsFontSizeDialog(
            fontSizeScale = state.fontSizeScale,
            onFontSizeSelected = { scale ->
                haptics.perform(HapticPattern.Pop)
                viewModel.setFontSizeScale(scale)
            },
            onDismiss = { showFontSizeDialog = false }
        )
    }

    if (showBleSheet) {
        BleStatusBottomSheet(
            bleEnabled = state.bleEnabled,
            transportMode = state.transportMode,
            onModeSelected = { viewModel.setTransportMode(it) },
            onDismiss = { showBleSheet = false }
        )
    }

    // Cache Status Message via SnackbarHost
    LaunchedEffect(cacheStatus) {
        cacheStatus?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            cacheStatus = null
        }
    }

    // Error Message from ViewModel via SnackbarHost
    val errorMessage by viewModel.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }
}
