package com.p2p.meshify.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.common.R

/**
 * Context menu shown as a bottom sheet when a message is long-pressed.
 * Provides actions: reply, forward, copy, delete for me, delete for everyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContextMenu(
    message: MessageEntity?,
    clipboardManager: ClipboardManager,
    onDismiss: () -> Unit,
    onReply: (MessageEntity) -> Unit,
    onForward: (String) -> Unit,
    onDeleteForMe: (String) -> Unit,
    onDeleteForEveryone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (message == null) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_action_reply)) },
                leadingContent = { Icon(Icons.Default.Reply, null) },
                modifier = Modifier.clickable {
                    onReply(message)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_action_forward)) },
                leadingContent = { Icon(Icons.Default.Forward, null) },
                modifier = Modifier.clickable {
                    onForward(message.id)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_action_copy)) },
                leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(message.text ?: ""))
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_action_delete_for_me)) },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable {
                    onDeleteForMe(message.id)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_action_delete_for_everyone)) },
                leadingContent = {
                    Icon(
                        Icons.Default.DeleteForever,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    onDeleteForEveryone(message.id)
                    onDismiss()
                }
            )
        }
    }
}
