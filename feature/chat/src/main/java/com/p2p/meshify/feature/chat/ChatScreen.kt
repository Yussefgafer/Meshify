package com.p2p.meshify.feature.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.ui.components.ForwardMessageDialog
import com.p2p.meshify.core.ui.components.FullImageViewer
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.feature.chat.components.BackConfirmationDialog
import com.p2p.meshify.feature.chat.components.ChatContextMenu
import com.p2p.meshify.feature.chat.components.ChatInputBar
import com.p2p.meshify.feature.chat.components.ChatTopBar
import com.p2p.meshify.feature.chat.components.DeleteAction
import com.p2p.meshify.feature.chat.components.DeleteConfirmationDialog
import com.p2p.meshify.feature.chat.components.MessageList
import com.p2p.meshify.feature.chat.components.ReplyIndicator
import com.p2p.meshify.feature.chat.components.ScrollToFAB
import com.p2p.meshify.feature.chat.components.SelectionModeTopBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Search UI constants ──────────────────────────────────────────────
private const val SEARCH_RESULT_BG_ALPHA_FROM_ME = 0.3f
private const val SEARCH_RESULT_BG_ALPHA_OTHER = 0.5f
private const val SEARCH_RESULT_TEXT_ALPHA = 0.6f
private const val SEARCH_HIGHLIGHT_ALPHA = 0.2f
private const val SEARCH_BAR_BORDER_ALPHA = 0.5f

/**
 * Main chat screen composable — orchestrates all chat sub-components.
 *
 * This file is intentionally thin (~150 lines of orchestration logic).
 * All UI concerns are delegated to focused components in the `components` package:
 * - [ChatTopBar] — peer avatar, name, online status
 * - [SelectionModeTopBar] — multi-select mode top bar
 * - [ReplyIndicator] — reply-to-message indicator
 * - [ChatInputBar] — text input + media staging
 * - [MessageList] — LazyColumn with messages and stagger animations
 * - [ScrollToFAB] — scroll-to-bottom floating action button
 * - [ChatContextMenu] — long-press message action sheet
 * - [BackConfirmationDialog] — unsaved draft back navigation confirmation
 * - [DeleteConfirmationDialog] — message deletion confirmation
 * - [DeleteAction] — sealed interface for delete action types
 *
 * @param viewModel ChatViewModel providing state and business logic
 * @param peerId ID of the chat peer
 * @param peerName Display name of the chat peer
 * @param onBackClick Callback invoked when the user navigates back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    peerId: String,
    peerName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalPremiumHaptics.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedMessages by viewModel.selectedMessages.collectAsState()
    val forwardDialogState by viewModel.forwardDialogState.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val listState = rememberLazyListState()
    val themeConfig = LocalMeshifyThemeConfig.current
    val clipboard = LocalClipboardManager.current
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedFullImage by remember { mutableStateOf<String?>(null) }
    var searchTextField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    // P2-11: Initialize textState from ViewModel draftText, survive config changes via rememberSaveable
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.draftText))
    }

    // P2-11: Sync draftText from ViewModel → Composable only when draftText changes externally
    LaunchedEffect(uiState.draftText) {
        val draft = uiState.draftText
        if (textState.text.isEmpty() && draft.isNotEmpty()) {
            textState = TextFieldValue(draft)
        } else if (textState.text.isNotEmpty() && draft.isEmpty() && textState.text != draft) {
            textState = TextFieldValue("")
        }
    }

    // Delete confirmation state
    var pendingDeleteAction by remember { mutableStateOf<DeleteAction?>(null) }

    // Track if user has scrolled away from bottom
    var hasScrolledToBottom by rememberSaveable { mutableStateOf(false) }

    // BackHandler confirmation for unsaved drafts
    var showBackConfirmationDialog by remember { mutableStateOf(false) }

    // Collect upload progress
    val uploadProgressMap by viewModel.uploadProgress.collectAsStateWithLifecycle(emptyMap())

    // Derived state for bottom detection
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItemIndex >= totalItemsNumber - 5
        }
    }

    LaunchedEffect(isAtBottom) {
        hasScrolledToBottom = isAtBottom
    }

    // Error snackbars
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.sendError) {
        uiState.sendError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.uploadError) {
        uiState.uploadError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearUploadError()
        }
    }

    // Media pickers
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.stageAttachment(it, bytes, MessageType.IMAGE)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.stageAttachment(it, bytes, MessageType.VIDEO)
            }
        }
    }

    // Smart scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it >= uiState.messages.size }

            if (hasScrolledToBottom) {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val lastIndex = uiState.messages.size - 1
                if (lastVisibleIndex >= lastIndex - 3) {
                    listState.animateScrollToItem(lastIndex)
                }
            } else {
                hasScrolledToBottom = true
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Track user scroll position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                hasScrolledToBottom = (firstVisibleIndex >= uiState.messages.size - 5)
            }
    }

    // Lazy loading: load more when user scrolls to top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                if (firstVisibleIndex < 5 && uiState.hasMoreMessages && !uiState.isLoadingMore) {
                    viewModel.loadMoreMessages()
                }
            }
    }

    // BackHandler: exit search mode first
    BackHandler(enabled = isSearching) {
        viewModel.stopSearch()
        searchTextField = TextFieldValue("")
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
                    onDeleteClick = {
                        val selected = selectedMessages
                        if (selected.isNotEmpty()) {
                            pendingDeleteAction = DeleteAction.Multiple(selected)
                        }
                    },
                    onCopyClick = {
                        viewModel.copySelectedMessagesToClipboard(clipboard)
                        viewModel.clearSelection()
                    }
                )
            } else if (isSearching) {
                // Search mode top bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchTextField,
                            onValueChange = {
                                searchTextField = it
                                viewModel.updateSearchQuery(it.text)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = MeshifyDesignSystem.Spacing.Sm, vertical = MeshifyDesignSystem.Spacing.Xs),
                            placeholder = { Text(stringResource(R.string.search_in_chat_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = SEARCH_BAR_BORDER_ALPHA)
                            )
                        )
                        IconButton(onClick = {
                            viewModel.stopSearch()
                            searchTextField = TextFieldValue("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.content_desc_close_search)
                            )
                        }
                    }
                    // Results count indicator
                    if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.search_results_count,
                                searchResults.size,
                                searchResults.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md)
                        )
                    }
                }
            } else {
                ChatTopBar(
                    peerName = peerName,
                    isOnline = uiState.isOnline,
                    onBackClick = onBackClick,
                    onSearchClick = {
                        viewModel.startSearch()
                    }
                )
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                ReplyIndicator(
                    replyTo = uiState.replyTo,
                    onDismissClick = { viewModel.setReplyTo(null) }
                )

                ChatInputBar(
                    textState = textState,
                    onTextChange = { textState = it },
                    onSendClick = {
                        viewModel.onInputChanged(textState.text)
                        viewModel.sendMessage()
                        textState = TextFieldValue()
                    },
                    stagedAttachments = uiState.stagedAttachments,
                    onRemoveAttachment = viewModel::removeStagedAttachment,
                    onStageAttachment = viewModel::stageAttachment,
                    isSending = uiState.isSending
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Initial loading state
            if (uiState.messages.isEmpty() && uiState.isLoading && !isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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

            if (isSearching) {
                // Show search results instead of message list
                SearchResultsList(
                    results = searchResults,
                    query = searchQuery,
                    listState = listState
                )
            } else {
                // Message list
                MessageList(
                messages = uiState.messages,
                isLoading = uiState.isLoading,
                isLoadingMore = uiState.isLoadingMore,
                selectedMessages = selectedMessages,
                uploadProgressMap = uploadProgressMap,
                transportUsed = uiState.transportUsed,
                peerName = peerName,
                bubbleStyle = themeConfig.bubbleStyle,
                listState = listState,
                getAttachmentsForGroupId = viewModel::getAttachmentsForMessage,
                onLongClick = { message ->
                    haptics.perform(HapticPattern.Pop)
                    if (selectedMessages.isEmpty()) {
                        menuMessage = message
                    } else {
                        viewModel.toggleMessageSelection(message.id)
                    }
                },
                onClick = { message ->
                    if (selectedMessages.isNotEmpty()) {
                        viewModel.toggleMessageSelection(message.id)
                    }
                },
                onImageClick = { imagePath ->
                    haptics.perform(HapticPattern.Pop)
                    selectedFullImage = imagePath
                },
                onReaction = { messageId, reaction ->
                    haptics.perform(HapticPattern.Tick)
                    viewModel.addReaction(messageId, reaction)
                },
                modifier = Modifier.fillMaxSize()
            )
            }

            // Scroll to bottom FAB (hidden during search)
            if (!isSearching) {
                ScrollToFAB(
                    isVisible = !hasScrolledToBottom,
                    onScrollToBottom = {
                        haptics.perform(HapticPattern.Tick)
                        scope.launch {
                            listState.animateScrollToItem(uiState.messages.size - 1)
                            hasScrolledToBottom = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }

    // Snackbar Host
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)
    )

    // Context menu for long-pressed message
    ChatContextMenu(
        message = menuMessage,
        clipboardManager = clipboard,
        onDismiss = { menuMessage = null },
        onReply = { msg -> viewModel.setReplyTo(msg) },
        onForward = { msgId -> viewModel.openForwardDialog(msgId) },
        onDeleteForMe = { msgId ->
            pendingDeleteAction = DeleteAction.Single(msgId, DeleteType.DELETE_FOR_ME)
        },
        onDeleteForEveryone = { msgId ->
            pendingDeleteAction = DeleteAction.Single(msgId, DeleteType.DELETE_FOR_EVERYONE)
        }
    )

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

    // Full Image Viewer
    selectedFullImage?.let { imagePath ->
        FullImageViewer(imagePath) { selectedFullImage = null }
    }

    // Back Confirmation Dialog
    if (showBackConfirmationDialog) {
        BackConfirmationDialog(
            onDismiss = { showBackConfirmationDialog = false },
            onDiscard = {
                showBackConfirmationDialog = false
                onBackClick()
            }
        )
    }

    // Delete Confirmation Dialog
    pendingDeleteAction?.let { action ->
        DeleteConfirmationDialog(
            action = action,
            onDismiss = { pendingDeleteAction = null },
            onConfirm = {
                when (action) {
                    is DeleteAction.Single ->
                        viewModel.deleteMessage(action.messageId, action.deleteType)
                    is DeleteAction.Multiple ->
                        viewModel.deleteSelectedMessages(DeleteType.DELETE_FOR_ME)
                }
                pendingDeleteAction = null
            },
            hapticTick = { haptics.perform(HapticPattern.Tick) }
        )
    }
}

/**
 * Search results list shown when the user is searching within a chat.
 * Displays messages with highlighted matching text.
 */
@Composable
private fun SearchResultsList(
    results: List<MessageEntity>,
    query: String,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (results.isEmpty() && query.isNotBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = MeshifyDesignSystem.Spacing.Sm)
        ) {
            items(results, key = { it.id }) { message ->
                SearchResultItem(
                    message = message,
                    query = query
                )
            }
        }
    }
}

/**
 * Individual search result with highlighted matching text.
 */
@Composable
private fun SearchResultItem(
    message: MessageEntity,
    query: String
) {
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val isFromMe = message.isFromMe

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeshifyDesignSystem.Spacing.Md, vertical = MeshifyDesignSystem.Spacing.Xs)
    ) {
        // Sender and timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
        ) {
            Text(
                text = if (isFromMe) stringResource(R.string.chat_message_you, "") else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Message text with highlighted match
        val displayText = message.text ?: ""
        if (displayText.isNotBlank()) {
            val annotatedText = buildAnnotatedString {
                val lowerText = displayText.lowercase()
                val lowerQuery = query.lowercase()
                var startIndex = 0

                while (startIndex < displayText.length) {
                    val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                    if (matchIndex == -1) {
                        // No more matches — append the rest
                        append(displayText.substring(startIndex))
                        break
                    } else {
                        // Append text before match
                        if (matchIndex > startIndex) {
                            append(displayText.substring(startIndex, matchIndex))
                        }
                        // Append highlighted match
                        withStyle(SpanStyle(background = highlightColor)) {
                            append(displayText.substring(matchIndex, matchIndex + query.length))
                        }
                        startIndex = matchIndex + query.length
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (isFromMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = SEARCH_RESULT_BG_ALPHA_FROM_ME)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SEARCH_RESULT_BG_ALPHA_OTHER)
                }
            ) {
                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm)
                )
            }
        }

        // Timestamp
        Text(
            text = formatChatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SEARCH_RESULT_TEXT_ALPHA),
            modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xxs)
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs),
            color = MaterialTheme.colorScheme.outline.copy(alpha = SEARCH_HIGHLIGHT_ALPHA)
        )
    }
}

private fun formatChatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
    return sdf.format(Date(timestamp))
}
