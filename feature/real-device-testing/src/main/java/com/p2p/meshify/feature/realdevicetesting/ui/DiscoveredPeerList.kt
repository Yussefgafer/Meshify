package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.SignalLevel

private val PEER_LIST_MAX_HEIGHT = 300.dp

@Composable
fun DiscoveredPeerList(
    peers: List<DiscoveredPeer>,
    selectedPeer: DiscoveredPeer?,
    isScanning: Boolean,
    onSelectPeer: (DiscoveredPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            Text(
                text = stringResource(
                    R.string.peer_selection_title,
                    peers.size
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            if (isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Sm))
                    Text(
                        text = stringResource(R.string.peer_selection_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (peers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MeshifyDesignSystem.Spacing.Lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))
                    Text(
                        text = stringResource(R.string.peer_selection_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = PEER_LIST_MAX_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
                ) {
                    items(peers, key = { it.id }) { peer ->
                        PeerListItem(
                            peer = peer,
                            isSelected = selectedPeer?.id == peer.id,
                            onClick = { onSelectPeer(peer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerListItem(
    peer: DiscoveredPeer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MeshifyDesignSystem.Motion.expressiveSpring(),
        label = "peer_background"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = MeshifyDesignSystem.Motion.expressiveSpring(),
        label = "peer_border"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MeshifyDesignSystem.Shapes.CardSmall
            )
            .clickable(onClick = onClick),
        shape = MeshifyDesignSystem.Shapes.CardSmall,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = MeshifyDesignSystem.Shapes.Pill
                    )
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MeshifyDesignSystem.Shapes.Pill
                        ) {}
                    }
                }
            }

            Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))

                    // Transport type badge
                    TransportTypeBadge(peer.transportType)

                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))

                    // Signal strength
                    SignalStrengthIndicator(peer.signalLevel)
                }

                Text(
                    text = peer.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransportTypeBadge(transportType: TransportType) {
    val (icon, label) = when (transportType) {
        TransportType.LAN -> Icons.Default.Wifi to stringResource(R.string.transport_lan_display)
        TransportType.BLE -> Icons.Default.Bluetooth to stringResource(R.string.transport_ble_display)
        TransportType.BOTH -> Icons.Default.Wifi to "${stringResource(R.string.transport_lan_display)}+${stringResource(R.string.transport_ble_display)}"
    }

    Surface(
        shape = MeshifyDesignSystem.Shapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Xs, vertical = MeshifyDesignSystem.Spacing.Xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun SignalStrengthIndicator(level: SignalLevel) {
    val bars = when (level) {
        SignalLevel.EXCELLENT -> 4
        SignalLevel.GOOD -> 3
        SignalLevel.FAIR -> 2
        SignalLevel.WEAK -> 1
        SignalLevel.UNKNOWN -> 0
    }

    val barColor = when (level) {
        SignalLevel.EXCELLENT, SignalLevel.GOOD -> MaterialTheme.colorScheme.primary
        SignalLevel.FAIR -> MaterialTheme.colorScheme.tertiary
        SignalLevel.WEAK -> MaterialTheme.colorScheme.error
        SignalLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val height = when (i) {
                1 -> 6.dp
                2 -> 10.dp
                3 -> 14.dp
                4 -> 18.dp
                else -> 18.dp
            }
            Surface(
                modifier = Modifier.width(4.dp).height(height),
                shape = MeshifyDesignSystem.Shapes.Pill,
                color = if (i <= bars) barColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            ) {}
        }
    }
}
