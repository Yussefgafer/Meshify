package com.p2p.meshify.feature.discovery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.*
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.core.ui.theme.MotionDurations

@Composable
fun DiscoveryHeader(isSearching: Boolean) {
    MeshifyCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarPulseMorph(
                isSearching = isSearching,
                size = 44.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = if (isSearching)
                    stringResource(R.string.searching_placeholder)
                else stringResource(R.string.screen_discovery_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_discovery_title),
                        fontWeight = FontWeight.Black
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MeshifyDesignSystem.Spacing.Md)
        ) {
            DiscoveryHeader(isSearching = uiState.isSearching)

            Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

            if (uiState.discoveredPeers.isEmpty()) {
                EmptyDiscoveryState(isSearching = uiState.isSearching)
            } else {
                MeshifySectionHeader(stringResource(R.string.discovery_peers_found))
                PeerList(
                    peers = uiState.discoveredPeers,
                    onPeerClick = onPeerClick
                )
            }
        }
    }
}

@Composable
fun PeerList(
    peers: List<PeerDevice>,
    onPeerClick: (PeerDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeshifyDesignSystem.Spacing.Xxl)
    ) {
        itemsIndexed(peers, key = { _, peer -> peer.id }) { index, peer ->
            MeshifyListItem(
                headline = peer.name,
                supporting = peer.address,
                leadingContent = {
                    MorphingAvatar(
                        initials = peer.name.take(1),
                        isOnline = true,
                        size = 52.dp
                    )
                },
                trailingContent = { SignalStrengthBadge(peer.signalStrength) },
                onClick = { onPeerClick(peer) }
            )
            if (index < peers.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 84.dp, end = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SignalStrengthBadge(signalStrength: com.p2p.meshify.domain.model.SignalStrength) {
    val (badgeText, badgeStyle) = when (signalStrength) {
        com.p2p.meshify.domain.model.SignalStrength.STRONG ->
            stringResource(R.string.signal_strong) to StrongBadgeStyle()
        com.p2p.meshify.domain.model.SignalStrength.MEDIUM ->
            stringResource(R.string.signal_medium) to MediumBadgeStyle()
        com.p2p.meshify.domain.model.SignalStrength.WEAK ->
            stringResource(R.string.signal_weak) to WeakBadgeStyle()
        com.p2p.meshify.domain.model.SignalStrength.OFFLINE ->
            stringResource(R.string.signal_offline) to OfflineBadgeStyle()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeStyle.backgroundColor
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = badgeStyle.textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

data class BadgeStyle(
    val backgroundColor: Color,
    val textColor: Color
)

@Composable
fun StrongBadgeStyle(): BadgeStyle {
    return BadgeStyle(
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        textColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun MediumBadgeStyle(): BadgeStyle {
    return BadgeStyle(
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
        textColor = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun WeakBadgeStyle(): BadgeStyle {
    return BadgeStyle(
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun OfflineBadgeStyle(): BadgeStyle {
    return BadgeStyle(
        backgroundColor = Color.Transparent,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun EmptyDiscoveryState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_peers_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
