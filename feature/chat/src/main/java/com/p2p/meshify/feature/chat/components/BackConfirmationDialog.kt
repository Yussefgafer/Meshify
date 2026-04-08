package com.p2p.meshify.feature.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R

/**
 * Confirmation dialog shown when the user attempts to navigate back
 * with an unsaved message draft (input text > 50 characters).
 *
 * @param onDismiss Called when the user cancels the dialog.
 * @param onDiscard Called when the user confirms discarding the draft and leaving.
 */
@Composable
fun BackConfirmationDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.dialog_discard_message_title)) },
        text = { Text(stringResource(R.string.dialog_discard_message_text)) },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.dialog_discard_message_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_discard_message_cancel))
            }
        }
    )
}
