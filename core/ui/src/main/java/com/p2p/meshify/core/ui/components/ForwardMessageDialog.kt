package com.p2p.meshify.core.ui.components

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.SignalStrength
import java.text.SimpleDateFormat
import java.util.*

/**
 * State holder for Forward Message Dialog.
 */
data class ForwardDialogState(
    val messages: List<MessageEntity> = emptyList(),
    val recentChats: List<ChatEntity> = emptyList(),
    val discoveredDevices: List<PeerDevice> = emptyList(),
    val allChats: List<ChatEntity> = emptyList(),
    val onlinePeerIds: Set<String> = emptySet(), // ✅ FIX: Track online peers
    val selectedPeerIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isForwarding: Boolean = false,
    val forwardProgress: Int = 0,
    val errorMessage: String? = null
) {
    val canForward: Boolean = selectedPeerIds.isNotEmpty()
    val selectedCount: Int = selectedPeerIds.size
    
    val filteredRecentChats: List<ChatEntity>
        get() = if (searchQuery.isBlank()) {
            recentChats.take(5)
        } else {
            recentChats.filter { it.peerName.contains(searchQuery, ignoreCase = true) }
        }
    
    val filteredDiscoveredDevices: List<PeerDevice>
        get() = if (searchQuery.isBlank()) {
            discoveredDevices
        } else {
            discoveredDevices.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    
    val filteredAllChats: List<ChatEntity>
        get() = if (searchQuery.isBlank()) {
            allChats.filterNot { it.peerId in recentChats.map { chat -> chat.peerId }.toSet() }
        } else {
            allChats.filterNot { it.peerId in recentChats.map { chat -> chat.peerId }.toSet() }
                .filter { it.peerName.contains(searchQuery, ignoreCase = true) }
        }
}

/**
 * Forward Message Dialog - Full featured dialog for forwarding messages.
 *
 * Features:
 * - Search functionality
 * - Message preview
 * - 3 sections: Recent, Discovered, All
 * - Multi-select support
 * - Progress indicator during forwarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardMessageDialog(
    state: ForwardDialogState,
    onDismiss: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onForwardClick: () -> Unit
) {
    val haptics = LocalPremiumHaptics.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    // Auto-focus search field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // ✅ FIX: Back button handling - prevent data loss during forwarding
    BackHandler(enabled = !state.isForwarding) {
        haptics.perform(HapticPattern.Pop)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isForwarding,
            dismissOnClickOutside = !state.isForwarding
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = MeshifyDesignSystem.Elevation.Level3
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshifyDesignSystem.Spacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.forward_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!state.isForwarding) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.forward_dialog_close_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))
                
                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.forward_dialog_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.forward_dialog_search_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.forward_dialog_clear_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    singleLine = true,
                    enabled = !state.isForwarding
                )
                
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
                
                // Message Preview
                if (state.messages.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.forward_dialog_preview),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Sm)
                    )
                    
                    ForwardPreviewCard(messages = state.messages)
                    
                    Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
                }
                
                // Loading indicator during forwarding
                if (state.isForwarding) {
                    LinearProgressIndicator(
                        progress = state.forwardProgress.toFloat() / state.selectedPeerIds.size.coerceAtLeast(1),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    
                    Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))
                    
                    Text(
                        text = stringResource(R.string.forward_dialog_forwarding, state.forwardProgress, state.selectedPeerIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))
                }
                
                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = MeshifyDesignSystem.Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
                ) {
                    // Recent Chats Section
                    if (state.filteredRecentChats.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.forward_dialog_section_quick, state.filteredRecentChats.size),
                                icon = Icons.Default.Schedule
                            )
                        }

                        items(state.filteredRecentChats, key = { it.peerId }) { chat ->
                            ForwardPeerItem(
                                name = chat.peerName,
                                subtitle = chat.lastMessage ?: "",
                                isOnline = chat.peerId in state.onlinePeerIds, // ✅ FIX: Use real online status
                                isSelected = state.selectedPeerIds.contains(chat.peerId),
                                onToggle = {
                                    haptics.perform(HapticPattern.Pop)
                                    onToggleSelection(chat.peerId)
                                },
                                enabled = !state.isForwarding
                            )
                        }
                    }
                    
                    // Discovered Devices Section
                    if (state.filteredDiscoveredDevices.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.forward_dialog_section_discovered, state.filteredDiscoveredDevices.size),
                                icon = Icons.Default.DeviceHub
                            )
                        }

                        items(state.filteredDiscoveredDevices, key = { it.id }) { device ->
                            ForwardPeerItem(
                                name = device.name,
                                subtitle = device.address,
                                isOnline = true, // Discovered devices are always online
                                isSelected = state.selectedPeerIds.contains(device.id),
                                onToggle = {
                                    haptics.perform(HapticPattern.Pop)
                                    onToggleSelection(device.id)
                                },
                                enabled = !state.isForwarding,
                                signalStrength = device.signalStrength
                            )
                        }
                    }
                    
                    // All Conversations Section
                    if (state.filteredAllChats.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.forward_dialog_section_all, state.filteredAllChats.size),
                                icon = Icons.Default.Chat
                            )
                        }

                        items(state.filteredAllChats, key = { it.peerId }) { chat ->
                            ForwardPeerItem(
                                name = chat.peerName,
                                subtitle = chat.lastMessage ?: "",
                                isOnline = chat.peerId in state.onlinePeerIds, // ✅ FIX: Use real online status
                                isSelected = state.selectedPeerIds.contains(chat.peerId),
                                onToggle = {
                                    haptics.perform(HapticPattern.Pop)
                                    onToggleSelection(chat.peerId)
                                },
                                enabled = !state.isForwarding
                            )
                        }
                    }
                    
                    // Empty state
                    if (state.filteredRecentChats.isEmpty() &&
                        state.filteredDiscoveredDevices.isEmpty() &&
                        state.filteredAllChats.isEmpty()
                    ) {
                        item {
                            EmptySearchState(query = state.searchQuery)
                        }
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MeshifyDesignSystem.Spacing.Sm),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isForwarding,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.forward_dialog_btn_cancel))
                    }

                    Button(
                        onClick = onForwardClick,
                        modifier = Modifier.weight(1f),
                        enabled = state.canForward && !state.isForwarding,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (state.isForwarding) {
                            // ✅ Loading state with progress
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${state.forwardProgress}/${state.selectedCount}",
                                fontWeight = FontWeight.Bold
                            )
                        } else if (state.errorMessage != null) {
                            // ✅ Error state
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = stringResource(R.string.forward_dialog_error),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.forward_dialog_btn_forward_retry),
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            // Normal state
                            Text(
                                text = stringResource(R.string.forward_dialog_btn_forward, state.selectedCount),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ✅ Error message display
                if (state.errorMessage != null && !state.isForwarding) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MeshifyDesignSystem.Spacing.Sm)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(MeshifyDesignSystem.Spacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = stringResource(R.string.forward_dialog_error_outline),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview card showing messages to be forwarded.
 */
@Composable
private fun ForwardPreviewCard(
    messages: List<MessageEntity>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = MeshifyDesignSystem.Elevation.Level1
    ) {
        Column(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
        ) {
            // First message preview
            messages.firstOrNull()?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            Icon(
                                imageVector = Icons.Default.Message,
                                contentDescription = stringResource(R.string.forward_dialog_message_type),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = message.text ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MessageType.IMAGE -> {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = stringResource(R.string.forward_dialog_image_type),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = stringResource(R.string.forward_dialog_media_image),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        MessageType.VIDEO -> {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = stringResource(R.string.forward_dialog_video_type),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = stringResource(R.string.forward_dialog_media_video),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = stringResource(R.string.forward_dialog_file_type),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = message.text ?: stringResource(R.string.forward_dialog_media_file),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Additional messages count
            if (messages.size > 1) {
                Text(
                    text = "+${messages.size - 1} more message${if (messages.size > 2) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Section header with icon.
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshifyDesignSystem.Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.forward_dialog_section_icon),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Individual peer item in the forward dialog list.
 */
@Composable
private fun ForwardPeerItem(
    name: String,
    subtitle: String,
    isOnline: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    signalStrength: SignalStrength? = null
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            
            // Avatar
            MorphingAvatar(
                initials = name.take(1),
                isOnline = isOnline,
                size = 44.dp
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    signalStrength?.let { strength ->
                        SignalStrengthIndicator(strength)
                    }
                }
            }
        }
    }
}

/**
 * Signal strength indicator badge.
 */
@Composable
private fun SignalStrengthIndicator(
    signalStrength: com.p2p.meshify.domain.model.SignalStrength
) {
    val (color, labelRes) = when (signalStrength) {
        com.p2p.meshify.domain.model.SignalStrength.STRONG ->
            MaterialTheme.colorScheme.primary to R.string.forward_dialog_signal_strong
        com.p2p.meshify.domain.model.SignalStrength.MEDIUM ->
            MaterialTheme.colorScheme.secondary to R.string.forward_dialog_signal_medium
        com.p2p.meshify.domain.model.SignalStrength.WEAK ->
            MaterialTheme.colorScheme.tertiary to R.string.forward_dialog_signal_weak
        com.p2p.meshify.domain.model.SignalStrength.OFFLINE ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to R.string.forward_dialog_signal_offline
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Empty state when search returns no results.
 */
@Composable
private fun EmptySearchState(
    query: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshifyDesignSystem.Spacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = stringResource(R.string.forward_dialog_empty_search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = stringResource(R.string.forward_dialog_empty_search),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.forward_dialog_empty_search_desc, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2
        )
    }
}
