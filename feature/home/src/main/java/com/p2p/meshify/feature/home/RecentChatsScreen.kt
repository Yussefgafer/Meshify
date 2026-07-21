package com.p2p.meshify.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.ui.components.PhysicsSwipeToDelete
import com.p2p.meshify.core.ui.components.MagneticChatItem
import com.p2p.meshify.core.ui.components.DeleteConfirmationDialog
import com.p2p.meshify.core.ui.components.ItemPosition
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem



/** Empty state text alpha */
private const val EMPTY_STATE_TEXT_ALPHA = 0.7f

/**
 * Enhanced Home Screen with LastChat-style Swipe-to-Delete and Grouping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentChatsScreen(
    viewModel: RecentChatsViewModel,
    onChatClick: (ChatEntity) -> Unit,
    onDiscoverClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var chatToDelete by remember { mutableStateOf<ChatEntity?>(null) }

    // Track swipe state for magnetic neighbor effect
    var swipingIndex by remember { mutableIntStateOf(-1) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_home_title),
                        fontWeight = FontWeight.Black
                    )
                },
                actions = {
                    IconButton(onClick = { /* placeholder — user has plans */ }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.content_desc_search))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_content_desc_settings))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onDiscoverClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_desc_discovery))
            }
        }
    ) { padding ->
        when {
            // Loading state
            uiState.isLoading -> {
                LoadingState(
                    padding = padding,
                    contentDescription = stringResource(R.string.home_loading_desc)
                )
            }
            // Error state - uiState.error is guaranteed non-null here
            uiState.error != null -> {
                ErrorState(
                    message = uiState.error ?: "Unknown error",
                    padding = padding,
                    onRetry = { viewModel.retryLoad() }
                )
            }
            // Empty state
            uiState.chats.isEmpty() -> {
                EmptyChatsState(padding)
            }
            // Content state - show chat list
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = MeshifyDesignSystem.Spacing.Md,
                        end = MeshifyDesignSystem.Spacing.Md,
                        bottom = MeshifyDesignSystem.Spacing.Xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        ExpressiveChatSectionHeader(stringResource(R.string.chats_recent_header))
                    }

                    itemsIndexed(uiState.chats, key = { _, chat -> chat.peerId }) { index, chat ->
                        val position = when {
                            uiState.chats.size == 1 -> ItemPosition.ONLY
                            index == 0 -> ItemPosition.FIRST
                            index == uiState.chats.size - 1 -> ItemPosition.LAST
                            else -> ItemPosition.MIDDLE
                        }

                        MagneticChatItem(
                            index = index,
                            swipingIndex = swipingIndex,
                            swipeProgress = swipeProgress
                        ) {
                            PhysicsSwipeToDelete(
                                onDelete = { chatToDelete = chat },
                                position = position,
                                groupCornerRadius = 24.dp,
                                itemIndex = index,
                                onSwipeProgress = { idx, progress ->
                                    swipingIndex = idx
                                    swipeProgress = progress
                                }
                            ) {
                                val isOnline = uiState.onlinePeers.contains(chat.peerId)
                                ExpressiveChatItem(
                                    peerName = chat.peerName,
                                    lastMessage = chat.lastMessage,
                                    timestamp = chat.lastTimestamp,
                                    unreadCount = chat.unreadCount,
                                    isOnline = isOnline,
                                    avatarHash = null,
                                    onClick = {
                                        viewModel.markChatAsRead(chat.peerId)
                                        onChatClick(chat)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        chatToDelete?.let { chat ->
            DeleteConfirmationDialog(
                title = stringResource(R.string.home_dialog_delete_title),
                text = stringResource(R.string.home_dialog_delete_text, chat.peerName),
                onConfirm = {
                    chatToDelete?.let { viewModel.deleteChat(it.peerId) }
                    chatToDelete = null
                },
                onDismiss = { chatToDelete = null }
            )
        }
    }
}

@Composable
fun EmptyChatsState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.no_recent_chats), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_state_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_STATE_TEXT_ALPHA))
    }
}

/**
 * Loading state composable with circular progress indicator.
 * Follows MD3E guidelines for loading indicators.
 */
@Composable
private fun LoadingState(
    padding: PaddingValues,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .semantics { this.contentDescription = contentDescription },
            shape = MeshifyDesignSystem.Shapes.IconContainer,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}

/**
 * Error state composable with retry button.
 * Shows error message and allows user to retry loading.
 */
@Composable
private fun ErrorState(
    message: String,
    padding: PaddingValues,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(R.string.content_desc_error_icon),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.home_error_retry))
            }
        }
    }
}


