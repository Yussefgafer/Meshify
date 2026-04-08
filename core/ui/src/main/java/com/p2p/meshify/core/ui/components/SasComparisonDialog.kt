package com.p2p.meshify.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem

/**
 * Dialog for comparing Short Authentication Strings (SAS) during OOB verification.
 *
 * This dialog displays two 6-character codes (yours and peer's) for visual comparison.
 * The user confirms if they match, which verifies the peer's identity and prevents MITM attacks.
 *
 * @param mySas Your 6-character authentication string
 * @param peerSas Peer's 6-character authentication string (null if not yet received)
 * @param onMatch Called when user confirms codes match
 * @param onMismatch Called when user reports codes don't match
 * @param onDismiss Called when user dismisses dialog without deciding
 * @param modifier Modifier for the dialog content
 */
@Composable
fun SasComparisonDialog(
    mySas: String,
    peerSas: String?,
    onMatch: () -> Unit,
    onMismatch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.oob_verify_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.oob_sas_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                // Your SAS
                Text(
                    text = stringResource(R.string.oob_sas_your_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = mySas,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                // Peer SAS
                if (peerSas != null) {
                    Text(
                        text = stringResource(R.string.oob_sas_peer_code),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val isMatch = mySas.equals(peerSas, ignoreCase = true)

                    Text(
                        text = peerSas,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    // Auto-indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs)
                    ) {
                        Icon(
                            imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Medium)
                        )

                        Spacer(modifier = Modifier.width(MeshifyDesignSystem.Spacing.Xs))

                        Text(
                            text = if (isMatch) stringResource(R.string.oob_sas_codes_match) else stringResource(R.string.oob_sas_codes_differ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Md)
                    )

                    Text(
                        text = stringResource(R.string.oob_sas_waiting_peer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onMatch,
                enabled = peerSas != null,
                shape = MeshifyDesignSystem.Shapes.Button
            ) {
                Text(stringResource(R.string.oob_sas_btn_match))
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
            ) {
                TextButton(onClick = onMismatch) {
                    Text(stringResource(R.string.oob_sas_btn_differ), color = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        }
    )
}
