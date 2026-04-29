package com.p2p.meshify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
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
    val haptics = LocalPremiumHaptics.current
    
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
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    shape = SquircleShape(4.0f)
                    clip = true
                },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tactile Warning Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            shape = SquircleShape(3.0f)
                            clip = true
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                Text(
                    text = stringResource(R.string.ob_skip_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                Text(
                    text = stringResource(R.string.ob_skip_confirm_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
                ) {
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            onStayClick()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SquircleShape(3.5f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.ob_skip_confirm_stay),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Cancel)
                            onLeaveClick()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SquircleShape(3.5f),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.ob_skip_confirm_leave),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
