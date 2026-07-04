package com.p2p.meshify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun SkipConfirmationDialog(onStayClick: () -> Unit, onLeaveClick: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(onDismissRequest = onStayClick, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
        Surface(modifier = modifier.fillMaxWidth(0.92f), shape = MeshifyDesignSystem.Shapes.Dialog, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 12.dp) {
            Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))
                Text(text = stringResource(R.string.ob_skip_confirm_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))
                Text(text = stringResource(R.string.ob_skip_confirm_desc), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)) {
                    Surface(onClick = onStayClick, modifier = Modifier.weight(1f).height(56.dp), shape = MeshifyDesignSystem.Shapes.Button, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Box(contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.ob_skip_confirm_stay), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                    }

                    Surface(onClick = onLeaveClick, modifier = Modifier.weight(1f).height(56.dp), shape = MeshifyDesignSystem.Shapes.Button, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Box(contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.ob_skip_confirm_leave), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
