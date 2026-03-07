package com.p2p.meshify.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.data.local.entity.MessageStatus
import com.p2p.meshify.data.local.entity.MessageType
import com.p2p.meshify.ui.components.FullImageViewer
import com.p2p.meshify.ui.theme.ChatBubbleShapes
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.getBubbleShape
import java.text.SimpleDateFormat
import java.util.*

private const val GROUPING_TIMEOUT_MS = 5 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val pendingImage by viewModel.pendingImageUri.collectAsState()
    val selectedIds by viewModel.selectedMessageIds.collectAsState()
    
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var showSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedFullImage by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setPendingImage(it) }
        showSheet = false
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisibleIndex >= lastIndex - 2) {
                listState.scrollToItem(lastIndex)
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex == 0 && messages.size >= 50) {
            viewModel.loadMoreMessages()
        }
    }

    Scaffold(
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = { 
                        Column {
                            Text(text = peerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isPeerTyping) stringResource(R.string.typing_indicator) 
                                       else if (isOnline) stringResource(R.string.status_online) 
                                       else stringResource(R.string.status_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPeerTyping || isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.msg_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        },
        bottomBar = {
            ChatInputArea(
                text = inputText,
                pendingImage = pendingImage,
                onTextChange = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onAttachClick = { showSheet = true },
                onRemoveImage = viewModel::removePendingImage
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                val prevMessage = if (index > 0) messages[index - 1] else null
                val nextMessage = if (index < messages.size - 1) messages[index + 1] else null
                
                val isGroupedWithPrevious = prevMessage?.senderId == message.senderId &&
                        (message.timestamp - prevMessage.timestamp) < GROUPING_TIMEOUT_MS
                
                val isGroupedWithNext = nextMessage?.senderId == message.senderId &&
                        (nextMessage.timestamp - message.timestamp) < GROUPING_TIMEOUT_MS

                MessageBubble(
                    message = message,
                    isSelected = selectedIds.contains(message.id),
                    isGroupedWithPrevious = isGroupedWithPrevious,
                    isGroupedWithNext = isGroupedWithNext,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleMessageSelection(message.id)
                    },
                    onClick = {
                        if (selectedIds.isNotEmpty()) viewModel.toggleMessageSelection(message.id)
                    },
                    onImageClick = { path ->
                        if (selectedIds.isEmpty()) selectedFullImage = path
                        else viewModel.toggleMessageSelection(message.id)
                    },
                    selectedIds = selectedIds
                )
                
                Spacer(modifier = Modifier.height(if (!isGroupedWithNext) 8.dp else 2.dp))
            }
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
                AttachmentOptions(
                    onImageClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_confirmation_text, selectedIds.size)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSelectedMessages()
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
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
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit,
    selectedIds: Set<Long> = emptySet()
) {
    val context = LocalContext.current
    val themeConfig = LocalMeshifyThemeConfig.current
    
    // ✅ MD3E Redesigned color scheme per spec
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (message.isFromMe) {
        // My messages: primaryContainer (Teal brand color)
        MaterialTheme.colorScheme.primaryContainer
    } else {
        // Peer messages: surfaceContainerHigh
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

    // Use dynamic bubble shape from settings
    val shape = getBubbleShape(
        bubbleStyle = themeConfig.bubbleStyle,
        isFromMe = message.isFromMe,
        isGroupedWithPrevious = isGroupedWithPrevious,
        isGroupedWithNext = isGroupedWithNext
    )

    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            shape = shape,
            tonalElevation = if (isSelected) 8.dp else 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (message.type == MessageType.TEXT) 14.dp else 4.dp,
                    vertical = if (message.type == MessageType.TEXT) 10.dp else 4.dp
                )
            ) {
                if (message.type == MessageType.IMAGE || message.mediaPath != null) {
                    // ✅ Image message with improved styling
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
                    // ✅ Text message
                    Text(
                        text = message.text ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        lineHeight = 22.sp
                    )
                }

                // ✅ Metadata row with improved styling
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
}

@Composable
fun ChatInputArea(
    text: String,
    pendingImage: Uri?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        // ✅ Pending image preview with animation
        AnimatedVisibility(
            visible = pendingImage != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = pendingImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clickable { onRemoveImage() }
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        // ✅ MD3E BottomAppBar-style input area
        BottomAppBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 8.dp,
            actions = {
                // ✅ Attachment button
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.attach_image),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            floatingActionButton = {
                // ✅ Small FAB for send button (56dp)
                FloatingActionButton(
                    onClick = onSend,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.btn_send),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        ) {
            // ✅ OutlinedTextField for message input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.input_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                maxLines = 4
            )
        }
    }
}

/**
 * ✅ MD3E Redesigned Attachment Bottom Sheet.
 * Uses NavigationDrawerItem-style options with proper layout.
 */
@Composable
fun AttachmentOptions(onImageClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
            ) {}
        }

        // Title
        Text(
            text = stringResource(R.string.attach_file_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // ✅ Gallery option (enabled)
        AttachmentDrawerItem(
            icon = Icons.Default.Image,
            label = stringResource(R.string.attach_image),
            subtext = stringResource(R.string.attach_from_gallery),
            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onImageClick,
            enabled = true
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // ✅ Camera option (disabled - coming soon)
        AttachmentDrawerItem(
            icon = Icons.Default.PhotoCamera,
            label = stringResource(R.string.attach_camera),
            subtext = stringResource(R.string.coming_soon),
            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { },
            enabled = false
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // ✅ File option (disabled - coming soon)
        AttachmentDrawerItem(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            label = stringResource(R.string.attach_file),
            subtext = stringResource(R.string.coming_soon),
            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { },
            enabled = false
        )
    }
}

/**
 * MD3E Attachment Drawer Item.
 * Styled like NavigationDrawerItem with icon, labels, and proper spacing.
 */
@Composable
fun AttachmentDrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtext: String,
    iconBackgroundColor: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Surface(
            color = iconBackgroundColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text content
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
    }
}
