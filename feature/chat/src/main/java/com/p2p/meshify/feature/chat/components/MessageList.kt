package com.p2p.meshify.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.core.common.R

/**
 * Stagger delay per message for cascade enter animation.
 * Hoisted from local variable to avoid recomputation on every recomposition.
 */
private const val MessageStaggerDelay = 50

/**
 * LazyColumn message list with staggered enter animations, pagination trigger, and empty/loading states.
 *
 * Displays the conversation messages in a vertically scrollable list. Each message is rendered
 * by [MessageBubble]. Handles:
 * - Empty state icon when no messages exist
 * - Loading spinner at top during pagination
 * - Staggered fade+slide enter animations per message
 * - Attachment fetching via produceState keyed by message.id + groupId
 *
 * @param messages List of messages to display
 * @param isLoading Whether initial load is in progress (controls empty state visibility)
 * @param isLoadingMore Whether pagination is loading (shows top spinner)
 * @param selectedMessages Set of currently selected message IDs (for multi-select mode)
 * @param uploadProgressMap Map of message ID → upload progress percentage (0-100)
 * @param transportUsed Map of message ID → transport type used for sending
 * @param peerName Display name of the chat peer (used in empty state text)
 * @param bubbleStyle Bubble shape style from theme configuration
 * @param listState LazyListState for external scroll control
 * @param getAttachmentsForGroupId Suspend function that fetches attachments for a given groupId
 * @param onLongClick Called when a message is long-pressed (triggers context menu or selection)
 * @param onClick Called when a message is tapped (toggles selection in multi-select mode)
 * @param onImageClick Called when an image inside a message is tapped
 * @param onReaction Called when a reaction is added/removed (messageId, reactionEmoji or null)
 */
@Composable
fun MessageList(
    messages: List<MessageEntity>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    selectedMessages: Set<String>,
    uploadProgressMap: Map<String, Int>,
    transportUsed: Map<String, TransportType>,
    peerName: String,
    bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle,
    listState: LazyListState = rememberLazyListState(),
    getAttachmentsForGroupId: suspend (String) -> List<MessageAttachmentEntity>,
    onLongClick: (MessageEntity) -> Unit,
    onClick: (MessageEntity) -> Unit,
    onImageClick: (String) -> Unit,
    onReaction: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Empty state when no messages exist and not loading
        if (messages.isEmpty() && !isLoading) {
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
        if (isLoadingMore) {
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
            messages,
            key = { _, m -> m.id }
        ) { index, message ->
            val attachments by produceState<List<MessageAttachmentEntity>>(
                initialValue = emptyList(),
                key1 = message.id,
                key2 = message.groupId
            ) {
                val groupId = message.groupId
                if (!groupId.isNullOrBlank()) {
                    value = getAttachmentsForGroupId(groupId)
                } else {
                    value = emptyList()
                }
            }

            val isSelected = message.id in selectedMessages
            val progressValue = uploadProgressMap[message.id]
            val messageTransportType = transportUsed[message.id]

            AnimatedVisibility(
                visible = !message.isDeletedForMe,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = index * MessageStaggerDelay
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
                    bubbleStyle = bubbleStyle,
                    isSelected = isSelected,
                    uploadProgress = progressValue,
                    transportType = messageTransportType,
                    onLongClick = { onLongClick(message) },
                    onClick = { onClick(message) },
                    onImageClick = onImageClick,
                    onReactionClick = { reaction -> onReaction(message.id, reaction) }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
