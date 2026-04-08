package com.p2p.meshify.feature.chat.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.ui.components.AlbumMediaGrid
import com.p2p.meshify.core.ui.components.VideoPlayer
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.core.common.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe date formatter for message timestamps.
 * Hoisted to file-level to avoid recreation on every recomposition.
 */
private val MessageTimeFormatter by lazy {
    SimpleDateFormat("hh:mm a", Locale.US)
}

/**
 * Status indicator icon size for standard states */
private val StatusIconSize = 14.dp

/**
 * Status indicator icon size for the sending spinner */
private val StatusIconSizeSmall = 12.dp

/**
 * Status indicator stroke width for the sending spinner */
private val StatusIconStrokeWidth = 1.5.dp

/**
 * Status icon alpha for queued (dim, waiting) */
private const val StatusAlphaQueued = 0.5f

/**
 * Status icon alpha for sent/delivered/received (medium confidence) */
private const val StatusAlphaDefault = 0.7f

/**
 * Individual message bubble composable displaying message content, status, and reactions.
 *
 * Renders a single message with proper alignment (right for sender, left for peer),
 * media content, status indicators, timestamps, and optional reaction badges.
 * Supports combined click handling for selection mode (tap) and context menu (long-press).
 *
 * @param message The message entity to display
 * @param attachments List of media attachments associated with this message
 * @param peerName Display name of the chat peer (used for context)
 * @param bubbleStyle Bubble shape style from theme configuration
 * @param isSelected Whether this message is currently selected in multi-select mode
 * @param uploadProgress Upload progress percentage (0-100), or null if not uploading
 * @param transportType Transport type used to send this message (LAN/BLE/BOTH)
 * @param onClick Called when the message is tapped
 * @param onLongClick Called when the message is long-pressed
 * @param onImageClick Called when an image inside the bubble is tapped (receives file path)
 * @param onReactionClick Called when a reaction badge is tapped (receives reaction string or null)
 */
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
                Column(
                    Modifier.padding(
                        horizontal = MeshifyDesignSystem.Spacing.Md,
                        vertical = MeshifyDesignSystem.Spacing.Xs
                    )
                ) {
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
                                caption = null,
                                onImageClick = onImageClick
                            )
                        } else {
                            // Single media (legacy support)
                            message.mediaPath?.let { path ->
                                if (message.text != null) Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))

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
                                            Text(
                                                stringResource(R.string.chat_message_file_prefix) + file.name,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                } else {
                                    // Show placeholder when file is missing
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

                    // Timestamp and status row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = MeshifyDesignSystem.Spacing.Xxs)
                    ) {
                        Text(
                            text = MessageTimeFormatter.format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = contentColor.copy(alpha = 0.6f)
                        )

                        if (message.isFromMe) {
                            Spacer(Modifier.width(4.dp))
                            StatusIcon(message.status, contentColor)
                            // Transport type indicator (only for non-LAN)
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
                            modifier = Modifier.offset(
                                y = (-12).dp,
                                x = if (message.isFromMe) (-12).dp else 12.dp
                            ),
                            shape = MeshifyDesignSystem.Shapes.CardSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = MeshifyDesignSystem.Elevation.Level2,
                            onClick = { onReactionClick(null) }
                        ) {
                            Text(
                                reaction,
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            // End of Surface content
        }
        // End of Box
    }
}

/**
 * Message status indicator icon.
 * Displays different icons based on the message status: queued, sending, sent, delivered,
 * received, read, or failed.
 */
@Composable
fun StatusIcon(status: MessageStatus, tint: Color) {
    when (status) {
        MessageStatus.QUEUED -> Icon(
            Icons.Default.Schedule,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = tint.copy(StatusAlphaQueued)
        )
        MessageStatus.SENDING -> CircularProgressIndicator(
            modifier = Modifier.size(StatusIconSizeSmall),
            strokeWidth = StatusIconStrokeWidth,
            color = tint
        )
        MessageStatus.SENT -> Icon(
            Icons.Default.Check,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = tint.copy(StatusAlphaDefault)
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Default.DoneAll,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = tint.copy(StatusAlphaDefault)
        )
        MessageStatus.RECEIVED -> Icon(
            Icons.Default.Done,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = tint.copy(StatusAlphaDefault)
        )
        MessageStatus.READ -> Icon(
            Icons.Default.DoneAll,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = MaterialTheme.colorScheme.tertiary
        )
        MessageStatus.FAILED -> Icon(
            Icons.Default.Error,
            null,
            modifier = Modifier.size(StatusIconSize),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Transport type indicator icon — shown next to message status for non-LAN sends.
 * Displays Bluetooth icon for BLE, grid icon for BOTH, and nothing for LAN.
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
