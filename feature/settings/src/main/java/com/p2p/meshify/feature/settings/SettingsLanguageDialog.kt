package com.p2p.meshify.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.MeshifySelectionDialog
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics

/**
 * MD3E wrapper around [MeshifySelectionDialog] for choosing the app language.
 */
@Composable
fun SettingsLanguageDialog(
    appLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val haptics = LocalPremiumHaptics.current
    val arabicLabel = stringResource(R.string.settings_language_arabic)
    val englishLabel = stringResource(R.string.settings_language_english)
    MeshifySelectionDialog(
        title = stringResource(R.string.settings_dialog_select_language),
        options = listOf("en", "ar"),
        selectedOption = appLanguage,
        onOptionSelected = { lang ->
            haptics.perform(HapticPattern.Pop)
            onLanguageSelected(lang)
        },
        onDismiss = onDismiss,
        optionLabel = { lang -> if (lang == "ar") arabicLabel else englishLabel },
        optionIcon = { lang -> if (lang == "ar") Icons.Default.Language else Icons.Default.Translate }
    )
}
