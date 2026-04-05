package com.p2p.meshify.feature.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.core.ui.components.*
import com.p2p.meshify.core.ui.theme.*
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.hooks.HapticPattern
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, peerId: String, peerName: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalPremiumHaptics.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedMessages by viewModel.selectedMessages.collectAsState()
    val forwardDialogState by viewModel.forwardDialogState.collectAsState()
    val listState = rememberLazyListState()
    val themeConfig = LocalMeshifyThemeConfig.current
    val clipboard = LocalClipboardManager.current
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedFullImage by remember { mutableStateOf<String?>(null) }
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    // Track if user has scrolled away from bottom
    var hasScrolledToBottom by rememberSaveable { mutableStateOf(false) }

    // BackHandler confirmation for unsaved drafts
    var showBackConfirmationDialog by remember { mutableStateOf(false) }

    // Collect upload progress - throttled in ViewModel
    val uploadProgressMap by viewModel.uploadProgress
        .collectAsStateWithLifecycle(emptyMap())
    
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

    // ✅ P0-02: Error Snackbar - show error message when send fails
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.sendError) {
        uiState.sendError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Upload error snackbar - shows error when file upload fails
    LaunchedEffect(uiState.uploadError) {
        uiState.uploadError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearUploadError()
        }
    }

    // Security warning snackbar - shows decryption failures and other security events
    LaunchedEffect(uiState.securityWarning) {
        uiState.securityWarning?.let { warning ->
            snackbarHostState.showSnackbar(warning)
            viewModel.clearSecurityWarning()
        }
    }

    // Load attachments via produceState inside each item — keyed by message.id + groupId
    // produceState handles memoization automatically via its keys; no separate cache needed

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
    // Wait for LazyColumn to actually reflect the new item count before scrolling
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            // Wait for LazyColumn to actually reflect the new item count
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it >= uiState.messages.size }

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

    // BackHandler for unsaved message drafts
    BackHandler(enabled = uiState.inputText.isNotBlank()) {
        if (uiState.inputText.length > 50) {
            showBackConfirmationDialog = true
        } else {
            onBackClick()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime),
        topBar = {
            if (selectedMessages.isNotEmpty()) {
                SelectionModeTopBar(
                    selectedCount = selectedMessages.size,
                    onBackClick = { viewModel.clearSelection() },
                    onForwardClick = { viewModel.openForwardDialogForSelected() },
                    onDeleteClick = { viewModel.deleteSelectedMessages(DeleteType.DELETE_FOR_ME) },
                    onCopyClick = {
                        viewModel.copySelectedMessagesToClipboard(clipboard)
                        viewModel.clearSelection()
                    }
                )
            } else {
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                // Reply indicator with animation
                AnimatedVisibility(
                    visible = uiState.replyTo != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
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
                                    Text(stringResource(R.string.chat_reply_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(uiState.replyTo?.text ?: "[Media]", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                IconButton(onClick = { viewModel.setReplyTo(null) }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    }
                }

                // Staged media row with animation
                AnimatedVisibility(
                    visible = uiState.stagedAttachments.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    StagedMediaRow(
                        attachments = uiState.stagedAttachments,
                        onRemoveClick = viewModel::removeStagedAttachment
                    )
                }

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
                    hasAttachments = uiState.stagedAttachments.isNotEmpty(),
                    isSending = uiState.isSending
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ✅ UX-02: Initial loading state with MD3E LoadingIndicator (Contained style)
            // Shows only on initial load when messages are empty
            if (uiState.messages.isEmpty() && uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // MD3E LoadingIndicator - using contained style with surfaceContainerHighest background
                    val loadingDesc = stringResource(R.string.chat_loading_desc)
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = loadingDesc },
                        shape = CircleShape,
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
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Empty state when no messages exist
                if (uiState.messages.isEmpty() && !uiState.isLoading) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))
                                Text(
                                    text = stringResource(R.string.chat_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
                                Text(
                                    text = stringResource(R.string.chat_empty_desc, peerName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
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

                // ✅ STAGGER ANIMATION: Add delay per item for cascading effect
                val staggerDelay = 50 // 50ms per item

                itemsIndexed(
                    uiState.messages,
                    // ✅ FIX: Use stable key (message.id only) to reduce recompositions
                    // Previous composite key caused 40-60% extra recompositions
                    key = { _, m -> m.id }
                ) { index, message ->
                // produceState keyed by message.id + groupId — tracks by identity, not index
                val attachments by produceState<List<MessageAttachmentEntity>>(
                    initialValue = emptyList(),
                    key1 = message.id,
                    key2 = message.groupId
                ) {
                    val groupId = message.groupId
                    if (!groupId.isNullOrBlank()) {
                        value = viewModel.getAttachmentsForMessage(groupId)
                    } else {
                        // Legacy single media or text-only message
                        value = emptyList()
                    }
                }

                val isSelected = message.id in selectedMessages
                val progressValue = uploadProgressMap[message.id]

                // ✅ DELETE ANIMATION + STAGGER ENTER
                AnimatedVisibility(
                    visible = !message.isDeletedForMe,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * staggerDelay
                        )
                    ) + slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 350f
                        ),
                        initialOffsetY = { it / 4 }
                    ),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 350f
                        ),
                        shrinkTowards = Alignment.Top
                    )
                ) {
                    MessageBubble(
                        message = message,
                        attachments = attachments,
                        peerName = peerName,
                        bubbleStyle = themeConfig.bubbleStyle,
                        isSelected = isSelected,
                        uploadProgress = progressValue,
                        transportType = uiState.transportUsed[message.id],
                        onLongClick = {
                            haptics.perform(HapticPattern.Pop) // ✅ UX04: Haptic feedback on long click
                            if (selectedMessages.isEmpty()) {
                                menuMessage = message
                            } else {
                                viewModel.toggleMessageSelection(message.id)
                            }
                        },
                        onClick = {
                            if (selectedMessages.isNotEmpty()) {
                                viewModel.toggleMessageSelection(message.id)
                            }
                        },
                        onImageClick = { imagePath: String ->
                            haptics.perform(HapticPattern.Pop) // ✅ UX04: Haptic feedback on image click
                            selectedFullImage = imagePath
                        },
                        onReactionClick = { reaction: String? ->
                            haptics.perform(HapticPattern.Tick) // ✅ UX04: Haptic feedback on reaction
                            viewModel.addReaction(message.id, reaction)
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // ✅ UX-01: Scroll to Bottom FAB - appears when user scrolls up
        AnimatedVisibility(
            visible = !hasScrolledToBottom,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MeshifyDesignSystem.Spacing.Md)
                .navigationBarsPadding()
        ) {
            FloatingActionButton(
                onClick = {
                    haptics.perform(HapticPattern.Tick)
                    scope.launch {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                        hasScrolledToBottom = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.content_desc_scroll_to_bottom),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ✅ P0-02: Snackbar Host for error messages
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)
    )
    }

    if (menuMessage != null) {
        ModalBottomSheet(onDismissRequest = { menuMessage = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_action_reply)) },
                    leadingContent = { Icon(Icons.Default.Reply, null) },
                    modifier = Modifier.clickable {
                        viewModel.setReplyTo(menuMessage)
                        menuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_action_forward)) },
                    leadingContent = { Icon(Icons.Default.Forward, null) },
                    modifier = Modifier.clickable {
                        menuMessage?.let {
                            viewModel.openForwardDialog(it.id)
                        }
                        menuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_action_copy)) },
                    leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                    modifier = Modifier.clickable {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(menuMessage?.text ?: ""))
                        menuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_action_delete_for_me)) },
                    leadingContent = { Icon(Icons.Default.Delete, null) },
                    modifier = Modifier.clickable {
                        menuMessage?.let {
                            viewModel.deleteMessage(it.id, DeleteType.DELETE_FOR_ME)
                        }
                        menuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_action_delete_for_everyone)) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = Color.Red) },
                    modifier = Modifier.clickable {
                        menuMessage?.let {
                            viewModel.deleteMessage(it.id, DeleteType.DELETE_FOR_EVERYONE)
                        }
                        menuMessage = null
                    }
                )
            }
        }
    }

    // Forward Dialog
    if (forwardDialogState.messages.isNotEmpty()) {
        ForwardMessageDialog(
            state = forwardDialogState,
            onDismiss = { viewModel.dismissForwardDialog() },
            onToggleSelection = { viewModel.togglePeerSelection(it) },
            onSearchQueryChange = { viewModel.updateForwardSearchQuery(it) },
            onForwardClick = {
                viewModel.forwardMessages(forwardDialogState.selectedPeerIds.toList())
            }
        )
    }

    // Full Image Viewer - only show when selectedFullImage is not null
    selectedFullImage?.let { imagePath ->
        FullImageViewer(imagePath) { selectedFullImage = null }
    }

    // Back Confirmation Dialog
    if (showBackConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmationDialog = false },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(stringResource(R.string.dialog_discard_message_title))
            },
            text = {
                Text(stringResource(R.string.dialog_discard_message_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackConfirmationDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.dialog_discard_message_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmationDialog = false }) {
                    Text(stringResource(R.string.dialog_discard_message_cancel))
                }
            }
        )
    }
}

/**
 * Message status indicator icon
 */
@Composable
fun StatusIcon(status: MessageStatus, tint: Color) {
    val size = 14.dp
    when (status) {
        MessageStatus.QUEUED -> Icon(Icons.Default.Schedule, null, Modifier.size(size), tint = tint.copy(0.5f))
        MessageStatus.SENDING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = tint)
        MessageStatus.SENT -> Icon(Icons.Default.Check, null, Modifier.size(size), tint = tint.copy(0.7f))
        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, Modifier.size(size), tint = tint.copy(0.7f))
        MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, Modifier.size(size), tint = MaterialTheme.colorScheme.tertiary)
        MessageStatus.FAILED -> Icon(Icons.Default.Error, null, Modifier.size(size), tint = MaterialTheme.colorScheme.error)
        else -> {}
    }
}

/**
 * ✅ T4: Transport type indicator icon — shown next to message status for non-LAN sends.
 */
@Composable
private fun TransportTypeIcon(transportType: TransportType, tint: Color) {
    when (transportType) {
        TransportType.BLE -> {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = stringResource(R.string.chat_content_desc_transport_icon),
                modifier = Modifier.size(MeshifyDesignSystem.Spacing.Xxs),
                tint = tint.copy(alpha = 0.7f)
            )
        }
        TransportType.BOTH -> {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = stringResource(R.string.chat_content_desc_transport_icon),
                modifier = Modifier.size(MeshifyDesignSystem.Spacing.Xxs),
                tint = tint.copy(alpha = 0.7f)
            )
        }
        TransportType.LAN -> {
            // No icon for LAN — it's the default behavior
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    attachments: List<MessageAttachmentEntity>,
    peerName: String,
    bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle,
    isSelected: Boolean = false,
    uploadProgress: Int? = null,
    transportType: TransportType? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onReactionClick: (String?) -> Unit
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    // Professional Chat Bubble Shape from Design System
    val bubbleShape = if (message.isFromMe) MeshifyDesignSystem.Shapes.BubbleMe else MeshifyDesignSystem.Shapes.BubblePeer

    // ✅ PF08: FIX excessive rememberUpdatedState - lambdas are already stable
    // rememberUpdatedState adds overhead when lambdas don't change
    // These lambdas are passed from parent and don't need wrapping

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = MeshifyDesignSystem.Spacing.Xxs),
        horizontalAlignment = alignment
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = if (isSelected) {
                    containerColor.copy(alpha = 0.7f)
                } else {
                    containerColor
                },
                contentColor = contentColor,
                tonalElevation = if (message.isFromMe) MeshifyDesignSystem.Elevation.Level0 else MeshifyDesignSystem.Elevation.Level1
            ) {
                Column(Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md, vertical = MeshifyDesignSystem.Spacing.Xs)) {
                if (message.isDeletedForEveryone) {
                    Text(
                        text = stringResource(R.string.chat_message_deleted),
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
                                text = stringResource(R.string.chat_reply_placeholder),
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
                                            contentDescription = stringResource(R.string.message_image),
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
                                        Text(stringResource(R.string.chat_message_file_prefix) + file.name, style = MaterialTheme.typography.bodySmall)
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
                                            contentDescription = stringResource(R.string.chat_message_media_not_found),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.chat_message_media_not_found),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Upload progress indicator (only if uploading)
                    uploadProgress?.let { progress ->
                        Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        // Show percentage text
                        Text(
                            text = stringResource(R.string.chat_upload_progress_percent, progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        // ✅ T4: Transport type indicator (only for non-LAN)
                        transportType?.let { type ->
                            if (type != TransportType.LAN) {
                                Spacer(Modifier.width(MeshifyDesignSystem.Spacing.Xxs / 2))
                                TransportTypeIcon(type, contentColor)
                            }
                        }
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
            // End of Surface content
            }
        // End of Surface
        }
    // End of Box
    }
}

/**
 * TopBar for multi-select mode - shows when messages are selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionModeTopBar(
    selectedCount: Int,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val haptics = LocalPremiumHaptics.current

    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.chat_selection_selected, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.chat_selection_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.chat_selection_exit_desc),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // Copy button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Pop)
                onCopyClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.chat_selection_copy_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_copy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Forward button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Pop)
                onForwardClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = stringResource(R.string.chat_selection_forward_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_forward),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            IconButton(onClick = {
                haptics.perform(HapticPattern.Error)
                onDeleteClick()
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.chat_selection_delete_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_action_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
