package com.p2p.meshify.ui.screens.discovery

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
import com.p2p.meshify.R
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.MotionDurations

/**
 * ✅ MD3E Redesigned Discovery Header with Radar Pulse Morph.
 */
@Composable
fun DiscoveryHeader(isSearching: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarPulseMorph(
                isSearching = isSearching,
                size = 40.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = if (isSearching)
                    stringResource(R.string.searching_placeholder)
                else stringResource(R.string.screen_discovery_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Main Discovery Screen Composable.
 * Updated with LastChat-style grouped items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onPeerClick: (PeerDevice) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val motion = LocalMeshifyMotion.current
    val settingsRepo = (LocalContext.current.applicationContext as com.p2p.meshify.MeshifyApp).container.settingsRepository

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_discovery_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            DiscoveryHeader(isSearching = uiState.isSearching)

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.discoveredPeers.isEmpty()) {
                EmptyDiscoveryState(isSearching = uiState.isSearching)
            } else {
                PeerList(
                    peers = uiState.discoveredPeers,
                    onPeerClick = onPeerClick,
                    settingsRepository = settingsRepo
                )
            }
        }
    }
}

@Composable
fun PeerList(
    peers: List<PeerDevice>,
    onPeerClick: (PeerDevice) -> Unit,
    settingsRepository: com.p2p.meshify.domain.repository.ISettingsRepository
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp), // Grouped look
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(peers, key = { _, peer -> peer.id }) { index, peer ->
            val position = when {
                peers.size == 1 -> ItemPosition.ONLY
                index == 0 -> ItemPosition.FIRST
                index == peers.size - 1 -> ItemPosition.LAST
                else -> ItemPosition.MIDDLE
            }

            SettingGroupItem(
                title = peer.name,
                subtitle = peer.address,
                icon = {
                    SignalMorphAvatar(
                        initials = peer.name.take(1),
                        signalStrength = peer.signalStrength,
                        size = 40.dp
                    )
                },
                trailing = { SignalStrengthBadge(peer.signalStrength) },
                position = position,
                settingsRepository = settingsRepository,
                onClick = { onPeerClick(peer) }
            )
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
        if (isSearching) {
            RadarPulseMorph(
                isSearching = true,
                size = 64.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            text = stringResource(R.string.no_peers_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
