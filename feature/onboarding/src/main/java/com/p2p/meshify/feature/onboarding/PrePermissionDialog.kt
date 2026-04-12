package com.p2p.meshify.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem

/**
 * Skip confirmation dialog shown when user tries to leave onboarding mid-flow.
 */
@Composable
fun SkipConfirmationDialog(
    onStayClick: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onStayClick,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .clip(MeshifyDesignSystem.Shapes.CardLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XXL),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                Text(
                    text = stringResource(R.string.ob_skip_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

                Text(
                    text = stringResource(R.string.ob_skip_confirm_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
                ) {
                    TextButton(
                        onClick = onStayClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.ob_skip_confirm_stay),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = onLeaveClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MeshifyDesignSystem.Shapes.Button
                    ) {
                        Text(
                            text = stringResource(R.string.ob_skip_confirm_leave),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
