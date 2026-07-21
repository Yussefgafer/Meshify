package com.p2p.meshify.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.MeshifyTextInputDialog

/**
 * MD3E wrapper around [MeshifyTextInputDialog] for editing the display name.
 */
@Composable
fun SettingsNameDialog(
    displayName: String,
    errorText: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    MeshifyTextInputDialog(
        title = stringResource(R.string.settings_dialog_edit_name),
        value = nameInput,
        onValueChange = { nameInput = it },
        onConfirm = { onConfirm(nameInput) },
        onDismiss = onDismiss,
        placeholder = stringResource(R.string.settings_dialog_name_placeholder),
        errorText = errorText
    )
}
