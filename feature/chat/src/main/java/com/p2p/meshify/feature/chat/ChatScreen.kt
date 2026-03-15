package com.p2p.meshify.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.core.ui.components.*
import com.p2p.meshify.core.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, peerId: String, peerName: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val themeConfig = LocalMeshifyThemeConfig.current
    val clipboard = LocalClipboardManager.current
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedFullImage by remember { mutableStateOf<String?>(null) }
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    // Store attachments grouped by message ID
    var messageAttachments by remember { mutableStateOf<Map<String, List<MessageAttachmentEntity>>>(emptyMap()) }
    
    // Track if user has scrolled away from bottom
    var hasScrolledToBottom by rememberSaveable { mutableStateOf(false) }
    
    // Use derivedStateOf for expensive calculations - avoids unnecessary recompositions
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItemIndex >= totalItemsNumber - 5
        }
    }
    
    // Update hasScrolledToBottom based on derived state
    LaunchedEffect(isAtBottom) {
        hasScrolledToBottom = isAtBottom
    }

    // ✅ FIX: Load attachments for messages with groupId - optimized with LazyColumn
    // Only load attachments for visible messages to reduce memory pressure
    val visibleMessageIds by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            visibleItems.mapNotNull { item ->
                val index = item.index
                if (index >= 0 && index < uiState.messages.size) {
                    uiState.messages[index].id
                } else null
            }
        }
    }

    LaunchedEffect(visibleMessageIds) {
        val attachmentsMap = mutableMapOf<String, List<MessageAttachmentEntity>>()
        
        // Only load attachments for visible messages
        visibleMessageIds.forEach { messageId ->
            val message = uiState.messages.find { it.id == messageId }
            if (message != null) {
                val groupId = message.groupId
                if (!groupId.isNullOrBlank()) {
                    // Fetch attachments from DAO
                    val attachments = viewModel.getAttachmentsForMessage(groupId)
                    attachmentsMap[messageId] = attachments
                } else if (message.mediaPath != null) {
                    // Legacy support for single media - use empty list
                    attachmentsMap[messageId] = emptyList()
                }
            }
        }
        
        messageAttachments = attachmentsMap
    }

    // Image launcher - stages attachment
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.stageAttachment(it, bytes, MessageType.IMAGE)
            }
        }
    }

    // Video launcher - stages attachment
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.stageAttachment(it, bytes, MessageType.VIDEO)
            }
        }
    }

    // Smart scroll: only auto-scroll if user is at bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            if (hasScrolledToBottom) {
                // Only scroll if user is near the bottom
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val lastIndex = uiState.messages.size - 1
                
                if (lastVisibleIndex >= lastIndex - 3) {
                    listState.animateScrollToItem(lastIndex)
                }
            } else {
                // First scroll to bottom
                hasScrolledToBottom = true
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    // Track user scroll position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                // User is at bottom if within 5 items of the end
                hasScrolledToBottom = (firstVisibleIndex >= uiState.messages.size - 5)
            }
    }
    
    // Lazy loading: load more when user scrolls to top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                // Load more when user is near the top (first 5 items)
                if (firstVisibleIndex < 5 && uiState.hasMoreMessages && !uiState.isLoadingMore) {
                    viewModel.loadMoreMessages()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MorphingAvatar(initials = peerName.take(1), isOnline = uiState.isOnline, size = 40.dp)
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = peerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isOnline) {
                                Text(
                                    text = "Online",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "Offline",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                // Reply indicator
                uiState.replyTo?.let { reply ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Message, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Replying to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(uiState.replyTo?.text ?: "[Media]", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(onClick = { viewModel.setReplyTo(null) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                }

                // Staged media row
                StagedMediaRow(
                    attachments = uiState.stagedAttachments,
                    onRemoveClick = viewModel::removeStagedAttachment
                )

                // Chat input
                MediaStagingChatInput(
                    textState = textState,
                    onTextChange = { textState = it },
                    onSendClick = {
                        viewModel.onInputChanged(textState.text)
                        viewModel.sendMessage()
                        textState = TextFieldValue()
                    },
                    onGalleryClick = { imageLauncher.launch("image/*") },
                    onVideoClick = { videoLauncher.launch("video/*") },
                    hasAttachments = uiState.stagedAttachments.isNotEmpty()
                )
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            // Loading indicator at top when loading more messages
            if (uiState.isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            
            itemsIndexed(
                uiState.messages,
                // ✅ CRITICAL FIX: Use composite key for better stability
                // This reduces unnecessary recompositions when messages are added/removed
                key = { _, m -> "${m.id}_${m.timestamp}_${m.status}" }
            ) { index, message ->
                val attachments = messageAttachments[message.id] ?: emptyList()
                MessageBubble(
                    message = message,
                    attachments = attachments,
                    peerName = peerName,
                    bubbleStyle = themeConfig.bubbleStyle,
                    onLongClick = { menuMessage = message },
                    onImageClick = { selectedFullImage = it },
                    onReactionClick = { viewModel.addReaction(message.id, it) }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (menuMessage != null) {
        ModalBottomSheet(onDismissRequest = { menuMessage = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text("Reply") },
                    leadingContent = { Icon(Icons.Default.Reply, null) },
                    modifier = Modifier.clickable {
                        viewModel.setReplyTo(menuMessage)
                        menuMessage = null
                    }
                )
                ListItem(headlineContent = { Text("Copy") }, leadingContent = { Icon(Icons.Default.ContentCopy, null) }, modifier = Modifier.clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(menuMessage?.text ?: "")); menuMessage = null })
                ListItem(headlineContent = { Text("Delete for Me") }, leadingContent = { Icon(Icons.Default.Delete, null) }, modifier = Modifier.clickable { viewModel.deleteMessage(menuMessage!!.id, DeleteType.DELETE_FOR_ME); menuMessage = null })
                ListItem(headlineContent = { Text("Delete for Everyone", color = Color.Red) }, leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = Color.Red) }, modifier = Modifier.clickable { viewModel.deleteMessage(menuMessage!!.id, DeleteType.DELETE_FOR_EVERYONE); menuMessage = null })
            }
        }
    }

    if (selectedFullImage != null) FullImageViewer(selectedFullImage!!) { selectedFullImage = null }
}

/**
 * Message status indicator icon
 */
@Composable
fun StatusIcon(status: MessageStatus, tint: Color) {
    val size = 12.dp
    when (status) {
        MessageStatus.QUEUED -> Icon(Icons.Default.Schedule, null, Modifier.size(size), tint = tint.copy(0.5f))
        MessageStatus.SENDING -> CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.dp, color = tint)
        MessageStatus.SENT -> Icon(Icons.Default.Check, null, Modifier.size(size), tint = tint.copy(0.7f))
        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, Modifier.size(size), tint = tint.copy(0.7f))
        MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, Modifier.size(size), tint = MaterialTheme.colorScheme.tertiary)
        MessageStatus.FAILED -> Icon(Icons.Default.Error, null, Modifier.size(size), tint = MaterialTheme.colorScheme.error)
        else -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    attachments: List<MessageAttachmentEntity>,
    peerName: String,
    bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onReactionClick: (String?) -> Unit
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    // Professional Chat Bubble Shape from Design System
    val bubbleShape = if (message.isFromMe) MeshifyDesignSystem.Shapes.BubbleMe else MeshifyDesignSystem.Shapes.BubblePeer
    
    // ✅ CRITICAL FIX: Use rememberUpdatedState to stabilize lambda references
    // This reduces allocations and GC pressure during recompositions
    val stableLongClick by rememberUpdatedState(onLongClick)
    val stableImageClick by rememberUpdatedState(onImageClick)
    val stableReactionClick by rememberUpdatedState(onReactionClick)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = stableLongClick  // ✅ Use stable reference
            )
            .padding(vertical = MeshifyDesignSystem.Spacing.Xxs),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = if (message.isFromMe) MeshifyDesignSystem.Elevation.Level0 else MeshifyDesignSystem.Elevation.Level1
        ) {
            Column(Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md, vertical = MeshifyDesignSystem.Spacing.Xs)) {
                if (message.isDeletedForEveryone) {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                } else {
                    if (message.replyToId != null) {
                        Surface(
                            color = contentColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "Replying to...",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Caption text
                    message.text?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 22.sp
                        )
                    }

                    // Album grid for grouped attachments
                    if (attachments.isNotEmpty()) {
                        if (message.text != null) Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
                        AlbumMediaGrid(
                            attachments = attachments,
                            caption = null, // Caption already shown above
                            onImageClick = onImageClick
                        )
                    } else {
                        // Single media (legacy support)
                        message.mediaPath?.let { path ->
                            if (message.text != null) Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
                            
                            // ✅ FIX: Check if file exists before displaying
                            val file = File(path)
                            if (file.exists()) {
                                when (message.type) {
                                    MessageType.IMAGE -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(File(path))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .sizeIn(maxWidth = 260.dp, maxHeight = 320.dp)
                                                .clip(MeshifyDesignSystem.Shapes.CardSmall)
                                                .clickable { onImageClick(path) },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    MessageType.VIDEO -> {
                                        VideoPlayer(
                                            videoUri = Uri.fromFile(File(path)),
                                            modifier = Modifier
                                                .width(260.dp)
                                                .height(180.dp)
                                                .clip(MeshifyDesignSystem.Shapes.CardSmall)
                                        )
                                    }
                                    else -> {
                                        Text("File: ${file.name}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else {
                                // ✅ FIX: Show placeholder when file is missing
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .sizeIn(maxWidth = 260.dp, maxHeight = 120.dp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Media file not found",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End).padding(top = MeshifyDesignSystem.Spacing.Xxs)
                ) {
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.US).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    if (message.isFromMe) {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(message.status, contentColor)
                    }
                }

                // Reaction badge
                message.reaction?.let { reaction ->
                    Surface(
                        modifier = Modifier.offset(y = (-12).dp, x = if(message.isFromMe) (-12).dp else 12.dp),
                        shape = MeshifyDesignSystem.Shapes.CardSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = MeshifyDesignSystem.Elevation.Level2,
                        onClick = { onReactionClick(null) }
                    ) {
                        Text(reaction, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
