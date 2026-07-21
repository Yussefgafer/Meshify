package com.p2p.meshify.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.TransportMode

/**
 * BLE Status Bottom Sheet — shows BLE state, connected peers, and transport mode selector.
 * MD3E styled: primaryContainer status surface, expressive top corners, subtle chip selection motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleStatusBottomSheet(
    bleEnabled: Boolean,
    transportMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalPremiumHaptics.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ble_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Md)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MeshifyDesignSystem.Shapes.CardSmall,
                color = if (bleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
                    ) {
                        Icon(
                            imageVector = if (bleEnabled) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (bleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (bleEnabled) stringResource(R.string.ble_sheet_active) else stringResource(R.string.ble_sheet_inactive),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (bleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            Text(
                text = stringResource(R.string.ble_transport_mode_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Sm)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportMode.entries.forEach { mode ->
                    val isSelected = mode == transportMode
                    val selectedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.96f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "bleModeScale"
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            onModeSelected(mode)
                        },
                        label = {
                            Text(
                                text = when (mode) {
                                    TransportMode.MULTI_PATH -> stringResource(R.string.ble_transport_mode_multipath)
                                    TransportMode.LAN_ONLY -> stringResource(R.string.ble_transport_mode_lan)
                                    TransportMode.BLE_ONLY -> stringResource(R.string.ble_transport_mode_ble)
                                    TransportMode.AUTO -> stringResource(R.string.ble_transport_mode_auto)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .scale(selectedScale)
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
        }
    }
}
