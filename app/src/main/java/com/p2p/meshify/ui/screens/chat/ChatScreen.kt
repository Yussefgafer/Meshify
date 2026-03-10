package com.p2p.meshify.ui.screens.chat

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
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.*
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
    
    // Load attachments for messages with groupId
    LaunchedEffect(uiState.messages.size) {
        val attachmentsMap = mutableMapOf<String, List<MessageAttachmentEntity>>()
        uiState.messages.forEach { message ->
            if (!message.groupId.isNullOrBlank()) {
                // In a real implementation, you'd fetch from DAO
                // For now, we'll handle single-message attachments
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

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
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
            itemsIndexed(uiState.messages, key = { _, m -> m.id }) { index, message ->
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
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
                                Text("File: ", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.align(Alignment.End).padding(top = MeshifyDesignSystem.Spacing.Xxs)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    if (message.isFromMe) {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(message.status, contentColor)
                    }
                }
            }
        }
    }
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
