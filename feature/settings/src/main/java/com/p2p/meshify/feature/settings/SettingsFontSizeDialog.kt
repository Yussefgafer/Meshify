package com.p2p.meshify.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.MeshifySelectionDialog

/**
 * MD3E wrapper around [MeshifySelectionDialog] for choosing the font size scale.
 * The selection haptic + [SettingsViewModel.setFontSizeScale] call live in [onFontSizeSelected].
 */
@Composable
fun SettingsFontSizeDialog(
    fontSizeScale: Float,
    onFontSizeSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    MeshifySelectionDialog(
        title = stringResource(R.string.settings_dialog_font_size),
        options = listOf(0.8f, 1.0f, 1.2f, 1.5f),
        selectedOption = fontSizeScale,
        onOptionSelected = { scale -> onFontSizeSelected(scale) },
        onDismiss = onDismiss,
        optionLabel = { scale -> "${(scale * 100).toInt()}%" },
        optionIcon = { _ -> Icons.Default.TextFields }
    )
}
