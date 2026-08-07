package com.p2p.meshify.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics

/**
 * MD3E styled Backup & Restore dialog.
 * Confirm uses a FilledTonalButton (expressive "rich color" tactic); labels use semantic typography.
 */
@Composable
fun SettingsBackupDialog(
    onExportResult: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    val haptics = LocalPremiumHaptics.current
    val resources = LocalContext.current.resources
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    viewModel.exportBackup { result ->
                        onExportResult(
                            result.fold(
                                onSuccess = { resources.getString(R.string.settings_backup_export_success) },
                                onFailure = { resources.getString(R.string.settings_backup_export_error, it.message) }
                            )
                        )
                        onDismiss()
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_export),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onExportResult(resources.getString(R.string.settings_backup_import_soon))
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }
        }
    )
}
