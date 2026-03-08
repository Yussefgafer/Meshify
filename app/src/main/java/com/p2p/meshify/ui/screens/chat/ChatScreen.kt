package com.p2p.meshify.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.data.local.entity.MessageStatus
import com.p2p.meshify.data.local.entity.MessageType
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.StatusOnline
import com.p2p.meshify.ui.theme.getBubbleShape
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val groupedMessages by viewModel.groupedMessages.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val pendingImage by viewModel.pendingImageUri.collectAsState()
    val selectedIds by viewModel.selectedMessageIds.collectAsState()
    val settingsRepo = (LocalContext.current.applicationContext as com.p2p.meshify.MeshifyApp).container.settingsRepository

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var selectedFullImage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setPendingImage(it) }
    }

    LaunchedEffect(groupedMessages.size) {
        if (groupedMessages.isNotEmpty()) {
            val lastIndex = groupedMessages.size - 1
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisibleIndex >= lastIndex - 2) {
                listState.scrollToItem(lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MorphingAvatar(
                                initials = peerName.take(1),
                                isOnline = isOnline,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = peerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isPeerTyping) stringResource(R.string.typing_indicator)
                                           else if (isOnline) stringResource(R.string.status_online)
                                           else stringResource(R.string.status_offline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPeerTyping || isOnline) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.msg_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Handle delete */ }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_desc_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        },
        bottomBar = {
            // ✅ Using the new StandardChatInput ported from LastChat
            StandardChatInput(
                text = inputText,
                onTextChange = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onAttachClick = { type ->
                    when(type) {
                        "image" -> photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        "camera" -> { /* Handle camera */ }
                        "file" -> { /* Handle file */ }
                    }
                },

            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            itemsIndexed(groupedMessages, key = { _, gm -> gm.message.id }) { _, groupedMessage ->
                MessageBubble(
                    message = groupedMessage.message,
                    isSelected = selectedIds.contains(groupedMessage.message.id),
                    isGroupedWithPrevious = groupedMessage.isGroupedWithPrevious,
                    isGroupedWithNext = groupedMessage.isGroupedWithNext,
                    showAvatar = groupedMessage.showAvatar,
                    avatarInitials = peerName.take(1),
                    isOnline = isOnline,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleMessageSelection(groupedMessage.message.id)
                    },
                    onClick = {
                        if (selectedIds.isNotEmpty()) viewModel.toggleMessageSelection(groupedMessage.message.id)
                    },
                    onImageClick = { path ->
                        if (selectedIds.isEmpty()) selectedFullImage = path
                        else viewModel.toggleMessageSelection(groupedMessage.message.id)
                    },
                    selectedIds = selectedIds
                )

                Spacer(modifier = Modifier.height(if (!groupedMessage.isGroupedWithNext) 8.dp else 2.dp))
            }
        }

        selectedFullImage?.let { path ->
            FullImageViewer(imagePath = path, onDismiss = { selectedFullImage = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isSelected: Boolean,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean,
    showAvatar: Boolean = false,
    avatarInitials: String = "",
    isOnline: Boolean = false,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit,
    selectedIds: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val themeConfig = LocalMeshifyThemeConfig.current

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (message.isFromMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    val bubblePadding = if (message.type == MessageType.TEXT) {
        PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    } else {
        PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    }

    val shape = getBubbleShape(
        bubbleStyle = themeConfig.bubbleStyle,
        isFromMe = message.isFromMe,
        isGroupedWithPrevious = isGroupedWithPrevious,
        isGroupedWithNext = isGroupedWithNext
    )

    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start

    if (!message.isFromMe && showAvatar) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            MorphingAvatar(
                initials = avatarInitials,
                isOnline = isOnline,
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            MessageBubbleContent(
                message = message,
                isSelected = isSelected,
                containerColor = containerColor,
                contentColor = contentColor,
                timeColor = timeColor,
                shape = shape,
                bubblePadding = bubblePadding,
                context = context,
                selectedIds = selectedIds,
                onLongClick = onLongClick,
                onClick = onClick,
                onImageClick = onImageClick
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            horizontalAlignment = alignment
        ) {
            MessageBubbleContent(
                message = message,
                isSelected = isSelected,
                containerColor = containerColor,
                contentColor = contentColor,
                timeColor = timeColor,
                shape = shape,
                bubblePadding = bubblePadding,
                context = context,
                selectedIds = selectedIds,
                onLongClick = onLongClick,
                onClick = onClick,
                onImageClick = onImageClick
            )
        }
    }
}

@Composable
fun MessageBubbleContent(
    message: MessageEntity,
    isSelected: Boolean,
    containerColor: Color,
    contentColor: Color,
    timeColor: Color,
    shape: RoundedCornerShape,
    bubblePadding: PaddingValues,
    context: android.content.Context,
    selectedIds: Set<String>,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = if (isSelected) 8.dp else 1.dp,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .then(
                if (!message.isFromMe) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(bubblePadding)
        ) {
            if (message.type == MessageType.IMAGE || message.mediaPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(message.mediaPath)
                        .memoryCacheKey(message.id)
                        .diskCacheKey(message.id)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (selectedIds.isEmpty()) {
                                onImageClick(message.mediaPath ?: "")
                            }
                        },
                    contentScale = ContentScale.Crop
                )
                if (!message.text.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
            } else {
                Text(
                    text = message.text ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    lineHeight = 22.sp
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor,
                    fontSize = 10.sp
                )
                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val statusIcon = when (message.status) {
                        MessageStatus.FAILED -> Icons.Default.Error
                        MessageStatus.RECEIVED -> Icons.Default.DoneAll
                        else -> Icons.Default.Check
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (message.status == MessageStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            timeColor
                        }
                    )
                }
            }
        }
    }
}
