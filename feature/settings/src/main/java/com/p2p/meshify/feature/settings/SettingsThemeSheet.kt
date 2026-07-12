package com.p2p.meshify.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.ui.components.ThemeSelectionBottomSheet
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.domain.repository.ThemeMode

/**
 * MD3E wrapper around [ThemeSelectionBottomSheet] for choosing the theme mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemeSheet(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalPremiumHaptics.current
    ThemeSelectionBottomSheet(
        currentTheme = currentTheme,
        onThemeSelected = {
            haptics.perform(HapticPattern.Selection)
            onThemeSelected(it)
        },
        onDismiss = onDismiss
    )
}
