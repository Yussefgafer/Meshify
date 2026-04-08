package com.p2p.meshify.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.DeleteType

/**
 * Sealed interface representing pending delete actions awaiting confirmation.
 * Mirrors the type from ChatScreen to keep this dialog self-contained.
 */
sealed interface DeleteAction {
    /** Delete a single message with a specific delete type */
    data class Single(val messageId: String, val deleteType: DeleteType) : DeleteAction
    /** Delete multiple messages (always DELETE_FOR_ME from selection mode) */
    data class Multiple(val messageIds: Set<String>) : DeleteAction
}

/**
 * Confirmation dialog shown before deleting one or more messages.
 * Dynamically adjusts title, body, and warning based on the [action] type.
 *
 * @param action The pending delete action to confirm.
 * @param onDismiss Called when the user cancels the dialog.
 * @param onConfirm Called when the user confirms deletion.
 * @param hapticTick Trigger a haptic tick feedback on confirm.
 */
@Composable
fun DeleteConfirmationDialog(
    action: DeleteAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    hapticTick: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = when (action) {
                    is DeleteAction.Single -> stringResource(R.string.dialog_delete_single_title)
                    is DeleteAction.Multiple -> stringResource(R.string.dialog_delete_multiple_title)
                }
            )
        },
        text = {
            Column {
                Text(
                    text = when (action) {
                        is DeleteAction.Single -> stringResource(R.string.dialog_delete_single_text)
                        is DeleteAction.Multiple -> stringResource(
                            R.string.dialog_delete_multiple_text,
                            action.messageIds.size
                        )
                    }
                )
                if (action is DeleteAction.Single && action.deleteType == DeleteType.DELETE_FOR_EVERYONE) {
                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))
                    Text(
                        text = stringResource(R.string.dialog_delete_everyone_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    hapticTick()
                    onConfirm()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.dialog_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}
