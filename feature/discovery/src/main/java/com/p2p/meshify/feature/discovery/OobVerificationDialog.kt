package com.p2p.meshify.feature.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.QrCodeDisplay
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.security.model.OobVerificationMethod

/**
 * Dialog for Out-Of-Band (OOB) identity verification.
 *
 * Provides a UI for users to verify their peer's identity through:
 * - **QR Code**: Display QR code for peer to scan
 * - **SAS Comparison**: Compare 6-character Short Authentication Strings
 * - **NFC**: Tap devices together (coming soon)
 *
 * Triggered from the chat or discovery screen during first session establishment
 * to prevent Man-In-The-Middle (MITM) attacks.
 *
 * @param onVerified Callback when verification succeeds
 * @param onDismiss Callback when user dismisses the dialog
 * @param viewModel OOB verification ViewModel (injected via Hilt)
 */
@Composable
fun OobVerificationDialog(
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: OobVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate to success when verified
    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) {
            onVerified()
        }
    }

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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Method selector tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
                ) {
                    FilterChip(
                        selected = uiState.selectedMethod == OobVerificationMethod.QR,
                        onClick = { viewModel.selectMethod(OobVerificationMethod.QR) },
                        label = { Text(stringResource(R.string.oob_method_qr)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Small)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = uiState.selectedMethod == OobVerificationMethod.SAS,
                        onClick = { viewModel.selectMethod(OobVerificationMethod.SAS) },
                        label = { Text(stringResource(R.string.oob_method_sas)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Small)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = uiState.selectedMethod == OobVerificationMethod.NFC,
                        onClick = { viewModel.selectMethod(OobVerificationMethod.NFC) },
                        label = { Text(stringResource(R.string.oob_method_nfc)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Small)
                            )
                        },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                // Content based on selected method
                when (uiState.selectedMethod) {
                    OobVerificationMethod.QR -> {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl)
                            )
                        } else {
                            QrCodeDisplay(
                                qrData = uiState.myPeerId,
                                title = stringResource(R.string.oob_qr_title),
                                subtitle = stringResource(R.string.oob_qr_subtitle)
                            )
                        }
                    }

                    OobVerificationMethod.SAS -> {
                        PeerIdComparisonContent(
                            myPeerId = uiState.myPeerId,
                            peerPeerId = uiState.peerPeerId,
                            isLoading = uiState.isLoading,
                            onMatch = { viewModel.verifyIdsMatch() },
                            onMismatch = { viewModel.reportMismatch() }
                        )
                    }

                    OobVerificationMethod.NFC -> {
                        NfcPlaceholderContent()
                    }
                }

                // Error message
                uiState.verificationError?.let { errorRes ->
                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MeshifyDesignSystem.Shapes.CardSmall
                    ) {
                        Text(
                            text = stringResource(errorRes),
                            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (uiState.isVerified) {
                Button(
                    onClick = onDismiss,
                    shape = MeshifyDesignSystem.Shapes.Button
                ) {
                    Text(stringResource(R.string.oob_btn_done))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_btn_close))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.reset()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        },
        shape = MeshifyDesignSystem.DialogShapes.Default
    )
}

/**
 * Peer ID comparison content — inline rendering to avoid nested AlertDialogs.
 *
 * Displays the local peer ID and allows the user to compare with the peer's ID.
 */
@Composable
private fun PeerIdComparisonContent(
    myPeerId: String,
    peerPeerId: String?,
    isLoading: Boolean,
    onMatch: () -> Unit,
    onMismatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.oob_peer_id_instruction),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        // Your Peer ID
        Text(
            text = stringResource(R.string.oob_peer_id_your),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = myPeerId,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

        // Peer Peer ID
        if (peerPeerId != null) {
            Text(
                text = stringResource(R.string.oob_peer_id_contact),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isMatch = myPeerId.equals(peerPeerId, ignoreCase = true)

            Text(
                text = peerPeerId,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // Match/mismatch indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs)
            ) {
                Text(
                    text = if (isMatch)
                        stringResource(R.string.oob_peer_ids_match)
                    else
                        stringResource(R.string.oob_peer_ids_differ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MeshifyDesignSystem.Spacing.Sm,
                    Alignment.CenterHorizontally
                )
            ) {
                Button(
                    onClick = onMatch,
                    shape = MeshifyDesignSystem.Shapes.Button,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.oob_peer_id_confirm_match))
                }

                OutlinedButton(
                    onClick = onMismatch,
                    shape = MeshifyDesignSystem.Shapes.Button,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.oob_peer_id_report_mismatch),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)
            )
        } else {
            Text(
                text = stringResource(R.string.oob_peer_id_waiting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs)
            )

            Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            CircularProgressIndicator()
        }
    }
}

/**
 * Placeholder for NFC verification (not yet implemented).
 */
@Composable
private fun NfcPlaceholderContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XL)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Text(
            text = stringResource(R.string.oob_nfc_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

        Text(
            text = stringResource(R.string.oob_nfc_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
