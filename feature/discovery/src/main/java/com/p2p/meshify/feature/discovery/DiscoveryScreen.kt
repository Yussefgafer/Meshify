package com.p2p.meshify.feature.discovery

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.domain.model.TransportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onPeerClick: (PeerDevice) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.discovery_screen_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    // ✅ UX-04: Refresh button in app bar
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.content_desc_refresh),
                            tint = if (uiState.isRefreshing) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            viewModel.checkWifiState()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Wi-Fi disabled state (CHECK FIRST)
            if (!uiState.isWifiEnabled) {
                WifiDisabledState(
                    onOpenSettings = {
                        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }
            // ✅ P0-4: Error state — display error message with retry
            else if (uiState.errorMessage != null) {
                ErrorState(
                    message = uiState.errorMessage!!,
                    padding = padding,
                    onRetry = { viewModel.refresh() }
                )
            }
            // ✅ UX-04: Modern Loading Bar at top (MD3E style)
            else if (uiState.isSearching && uiState.discoveredPeers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Modern linear progress indicator with MD3E motion
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                    EmptyDiscoveryState(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (uiState.discoveredPeers.isEmpty()) {
                EmptyDiscoveryState(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PeerList(
                    modifier = Modifier.fillMaxSize(),
                    peers = uiState.discoveredPeers,
                    onPeerClick = onPeerClick,
                    listState = listState
                )
            }
        }
    }
}

@Composable
fun PeerList(
    modifier: Modifier = Modifier,
    peers: List<PeerDevice>,
    onPeerClick: (PeerDevice) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
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

            // ✅ T3: Transport type badge
            TransportBadge(peer.transportType)

            Spacer(modifier = Modifier.width(MeshifyDesignSystem.Spacing.Sm))

            SignalStrengthIndicator(peer.signalStrength)
        }
    }
}

@Composable
private fun TransportBadge(transportType: TransportType) {
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val label: String
    val badgeColor: androidx.compose.ui.graphics.Color
    val contentDescRes: String

    when (transportType) {
        TransportType.BLE -> {
            icon = Icons.Default.Bluetooth
            label = stringResource(R.string.discovery_transport_ble)
            badgeColor = MaterialTheme.colorScheme.primary
            contentDescRes = stringResource(R.string.content_desc_transport_badge)
        }
        TransportType.LAN -> {
            icon = Icons.Default.Wifi
            label = stringResource(R.string.discovery_transport_lan)
            badgeColor = MaterialTheme.colorScheme.secondary
            contentDescRes = stringResource(R.string.content_desc_transport_badge)
        }
        TransportType.BOTH -> {
            icon = Icons.Default.Wifi
            label = stringResource(R.string.discovery_transport_both)
            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant
            contentDescRes = stringResource(R.string.content_desc_transport_badge)
        }
    }

    Surface(
        shape = MeshifyDesignSystem.Shapes.Pill,
        color = badgeColor.copy(alpha = 0.12f),
        contentColor = badgeColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MeshifyDesignSystem.Spacing.Xs,
                vertical = MeshifyDesignSystem.Spacing.Xxs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xxs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescRes,
                modifier = Modifier.size(14.dp),
                tint = badgeColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
            if (transportType == TransportType.BOTH) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = badgeColor
                )
            }
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
            text = stringResource(R.string.discovery_no_devices),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

        Text(
            text = stringResource(R.string.discovery_wifi_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WifiDisabledState(
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MeshifyDesignSystem.Spacing.Xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = stringResource(R.string.discovery_wifi_off_desc),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Text(
            text = stringResource(R.string.discovery_wifi_disabled_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

        Text(
            text = stringResource(R.string.discovery_wifi_disabled_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.discovery_open_wifi_settings))
        }
    }
}

// ✅ P0-4: Error state composable for discovery errors
@Composable
private fun ErrorState(
    message: String,
    padding: PaddingValues,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Text(
            text = stringResource(R.string.discovery_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Xl)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(MeshifyDesignSystem.Spacing.Sm))
            Text(stringResource(R.string.discovery_error_retry))
        }
    }
}
