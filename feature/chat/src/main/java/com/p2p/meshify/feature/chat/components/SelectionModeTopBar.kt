package com.p2p.meshify.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.common.R

/**
 * Top app bar shown during multi-select mode when messages are selected.
 *
 * Displays the count of selected messages and provides action buttons:
 * - Copy: copies selected messages to clipboard
 * - Forward: opens forward dialog for selected messages
 * - Delete: triggers delete confirmation for selected messages
 *
 * @param selectedCount Number of currently selected messages
 * @param onBackClick Called when the back/close button is tapped (exits selection mode)
 * @param onForwardClick Called when the forward button is tapped
 * @param onDeleteClick Called when the delete button is tapped
 * @param onCopyClick Called when the copy button is tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionModeTopBar(
    selectedCount: Int,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current

    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.chat_selection_selected,
                        selectedCount,
                        selectedCount
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.chat_selection_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.chat_selection_exit_desc),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // Copy button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Pop)
                onCopyClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.chat_selection_copy_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_copy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Forward button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Pop)
                onForwardClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = stringResource(R.string.chat_selection_forward_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_forward),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Error)
                onDeleteClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.chat_selection_delete_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
