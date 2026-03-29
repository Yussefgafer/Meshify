package com.p2p.meshify.feature.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.*
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.SignalStrength

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onPeerClick: (PeerDevice) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nearby",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.discoveredPeers.isEmpty()) {
            EmptyDiscoveryState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            PeerList(
                modifier = Modifier.padding(padding),
                peers = uiState.discoveredPeers,
                onPeerClick = onPeerClick
            )
        }
    }
}

@Composable
fun PeerList(
    modifier: Modifier = Modifier,
    peers: List<PeerDevice>,
    onPeerClick: (PeerDevice) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MeshifyDesignSystem.Spacing.Md,
            vertical = MeshifyDesignSystem.Spacing.Sm
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(peers, key = { _, peer -> peer.id }) { index, peer ->
            PeerListItem(
                peer = peer,
                onClick = { onPeerClick(peer) }
            )
        }
    }
}

@Composable
private fun PeerListItem(
    peer: PeerDevice,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MorphingAvatar(
                initials = peer.name.take(1),
                isOnline = true,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(MeshifyDesignSystem.Spacing.Md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = peer.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SignalStrengthIndicator(peer.signalStrength)
        }
    }
}

@Composable
private fun SignalStrengthIndicator(signalStrength: SignalStrength) {
    val (bars, color) = when (signalStrength) {
        SignalStrength.STRONG -> 3 to MaterialTheme.colorScheme.primary
        SignalStrength.MEDIUM -> 2 to MaterialTheme.colorScheme.secondary
        SignalStrength.WEAK -> 1 to MaterialTheme.colorScheme.tertiary
        SignalStrength.OFFLINE -> 0 to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val height = when (index) {
                0 -> 8.dp
                1 -> 12.dp
                2 -> 16.dp
                else -> 8.dp
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .background(
                        if (index < bars) color
                        else color.copy(alpha = 0.2f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun EmptyDiscoveryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(MeshifyDesignSystem.Spacing.Xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Text(
            text = "No devices nearby",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

        Text(
            text = "Make sure Wi-Fi is enabled on other devices",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
