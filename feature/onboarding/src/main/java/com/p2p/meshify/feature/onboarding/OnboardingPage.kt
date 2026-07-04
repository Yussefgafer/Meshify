package com.p2p.meshify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem

@Composable
fun WelcomePage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = MeshifyDesignSystem.Shapes.IconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        Text(text = stringResource(R.string.ob_welcome_title), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Text(text = stringResource(R.string.ob_welcome_desc), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private data class HowItWorksStep(val titleRes: Int, val descRes: Int, val icon: ImageVector)

@Composable
fun HowItWorksPage(modifier: Modifier = Modifier) {
    val steps = listOf(
        HowItWorksStep(R.string.ob_step_discover_title, R.string.ob_step_discover_desc, Icons.Default.Wifi),
        HowItWorksStep(R.string.ob_step_connect_title, R.string.ob_step_connect_desc, Icons.Default.Devices),
        HowItWorksStep(R.string.ob_step_chat_title, R.string.ob_step_chat_desc, Icons.Default.Forum)
    )

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = MeshifyDesignSystem.Spacing.Lg),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.ob_how_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        Column(verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)) {
            steps.forEachIndexed { index, step ->
                StepCard(stepNumber = index + 1, titleRes = step.titleRes, descRes = step.descRes, icon = step.icon)
            }
        }
    }
}

@Composable
private fun StepCard(stepNumber: Int, titleRes: Int, descRes: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.Card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(MeshifyDesignSystem.Spacing.Md), horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = stringResource(descRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PermissionsOverviewPage(permissions: List<PermissionInfo>, permissionStatuses: Map<String, PermissionStatus>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = MeshifyDesignSystem.Shapes.IconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        Text(text = stringResource(R.string.ob_perm_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

        Text(text = stringResource(R.string.ob_perm_desc), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        Surface(modifier = Modifier.fillMaxWidth(), shape = MeshifyDesignSystem.Shapes.Card, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
            Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)) {
                permissions.forEach { perm ->
                    PermissionRow(perm.iconType, perm.labelRes, perm.importanceLabelRes, permissionStatuses[perm.id] ?: PermissionStatus.NotAsked, perm.isRequired)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(iconType: PermissionIconType, labelRes: Int, importanceLabelRes: Int, status: PermissionStatus, isRequired: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)) {
            Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (iconType) {
                        PermissionIconType.Wifi -> Icons.Filled.Wifi
                        PermissionIconType.Bluetooth -> Icons.AutoMirrored.Filled.BluetoothSearching
                        PermissionIconType.Notifications -> Icons.Filled.Notifications
                        PermissionIconType.Location -> Icons.Filled.LocationOn
                    },
                    contentDescription = null, modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Large), tint = MaterialTheme.colorScheme.primary
                )
            }

            Column {
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = stringResource(importanceLabelRes), style = MaterialTheme.typography.labelSmall, color = if (isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        StatusBadge(status = status)
    }
}

@Composable
private fun StatusBadge(status: PermissionStatus, modifier: Modifier = Modifier) {
    val (textRes, badgeColor) = when (status) {
        PermissionStatus.NotAsked -> R.string.ob_perm_not_asked to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        PermissionStatus.Granted -> R.string.ob_perm_granted to MaterialTheme.colorScheme.primary
        PermissionStatus.Denied -> R.string.ob_perm_denied to MaterialTheme.colorScheme.error
        PermissionStatus.DeniedPermanently -> R.string.ob_perm_denied_permanent to MaterialTheme.colorScheme.error
        PermissionStatus.Skipped -> R.string.ob_perm_skipped to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        PermissionStatus.AlreadyGranted -> R.string.ob_perm_already_granted to MaterialTheme.colorScheme.primary
    }

    Text(
        text = stringResource(textRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = badgeColor,
        modifier = modifier.background(badgeColor.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.Pill).padding(horizontal = MeshifyDesignSystem.Spacing.Sm, vertical = MeshifyDesignSystem.Spacing.Xxs)
    )
}
