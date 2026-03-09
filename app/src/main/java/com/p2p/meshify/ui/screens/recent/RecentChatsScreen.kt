package com.p2p.meshify.ui.screens.recent

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.ChatEntity
import androidx.compose.material3.HorizontalDivider
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.MeshifyThemeProperties
import java.text.SimpleDateFormat
import java.util.*

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
    val chats by viewModel.recentChats.collectAsState()
    val onlinePeers by viewModel.onlinePeers.collectAsState()
    val context = LocalContext.current

    var chatToDelete by remember { mutableStateOf<ChatEntity?>(null) }

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
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            ExpressiveMorphingFAB(onClick = onDiscoverClick)
        }
    ) { padding ->
        if (chats.isEmpty()) {
            EmptyChatsState(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                item {
                    MeshifySectionHeader(stringResource(R.string.chats_recent_header))
                }
                
                itemsIndexed(chats, key = { _, chat -> chat.peerId }) { index, chat ->
                    val position = when {
                        chats.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == chats.size - 1 -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

                    PhysicsSwipeToDelete(
                        onDelete = { chatToDelete = chat },
                        position = position,
                    ) {
                        val isOnline = onlinePeers.contains(chat.peerId)
                        MeshifyListItem(
                            headline = chat.peerName,
                            supporting = chat.lastMessage ?: stringResource(R.string.last_msg_none),
                            leadingContent = {
                                MorphingAvatar(
                                    initials = chat.peerName.take(1),
                                    isOnline = isOnline,
                                    size = 56.dp
                                )
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatRecentTime(chat.lastTimestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isOnline) {
                                        Spacer(Modifier.height(4.dp))
                                        MeshifyPill("Online", MaterialTheme.colorScheme.primaryContainer)
                                    }
                                }
                            },
                            onClick = { onChatClick(chat) }
                        )
                    }
                    if (index < chats.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        chatToDelete?.let { chat ->
            DeleteConfirmationDialog(
                title = "Delete Conversation",
                text = "Are you sure you want to delete the conversation with ${chat.peerName}?",
                onConfirm = {
                    chatToDelete = null
                },
                onDismiss = { chatToDelete = null }
            )
        }
    }
}

@Composable
fun ChatListItem(chat: ChatEntity, isOnline: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorphingAvatar(
            initials = chat.peerName.take(1),
            isOnline = isOnline,
            size = 52.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = chat.peerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isImage = chat.lastMessage == stringResource(R.string.last_msg_image)
                if (isImage) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = chat.lastMessage ?: stringResource(R.string.last_msg_none),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(text = formatRecentTime(chat.lastTimestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(stringResource(R.string.no_recent_chats_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

fun formatRecentTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
