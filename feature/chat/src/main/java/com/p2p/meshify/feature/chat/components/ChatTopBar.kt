package com.p2p.meshify.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.components.MorphingAvatar
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.common.R
import com.p2p.meshify.domain.security.model.TrustLevel

/**
 * Chat top app bar showing peer avatar, name, and online status.
 * Displays a back button, peer avatar with name, and connection status.
 *
 * @param trustLevel The peer's trust level. Defaults to [TrustLevel.UNKNOWN] — no indicator shown.
 * @param onVerifyClick Callback when the unverified warning icon is clicked. Null disables clicking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    peerName: String,
    isOnline: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    onVerifyClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MorphingAvatar(
                    initials = peerName.take(1),
                    isOnline = isOnline,
                    size = 40.dp
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TrustIndicator(trustLevel, onVerifyClick)
                    }
                    if (isOnline) {
                        Text(
                            text = stringResource(R.string.chat_status_online),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.chat_status_offline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
        },
        actions = {
            if (onSearchClick != null) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.content_desc_search)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

/**
 * Subtle trust indicator shown next to the peer name.
 * - OOB_VERIFIED: green shield icon
 * - TOFU or UNKNOWN (first session): subtle warning icon, optionally clickable
 * - REJECTED or UNKNOWN (no session): nothing shown
 */
@Composable
private fun TrustIndicator(
    trustLevel: TrustLevel,
    onVerifyClick: (() -> Unit)? = null
) {
    when (trustLevel) {
        TrustLevel.OOB_VERIFIED -> {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(R.string.content_desc_verified),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        TrustLevel.TOFU -> {
            val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            if (onVerifyClick != null) {
                IconButton(onClick = onVerifyClick, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.content_desc_unverified_tap_verify),
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.content_desc_unverified),
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        TrustLevel.UNKNOWN,
        TrustLevel.REJECTED -> {
            // No indicator for unknown or rejected peers
        }
    }
}
