package com.p2p.meshify.core.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.ui.R
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.MessageType
import java.io.File

/**
 * Professional Media Staging Chat Input.
 * Features:
 * - BasicTextField with caption support
 * - Gallery (InsertPhoto) button
 * - Video (Videocam) button
 * - Send button (ArrowUpward icon only)
 * - Send enabled when text OR attachments exist
 * - Loading bar during send operation
 */
@Composable
fun MediaStagingChatInput(
    textState: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasAttachments: Boolean = false,
    isSending: Boolean = false
) {
    val haptics = LocalPremiumHaptics.current
    var textFieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val hasContent = textState.text.isNotBlank() || hasAttachments

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gallery Button
            val galleryInteraction = remember { MutableInteractionSource() }
            val isGalleryPressed by galleryInteraction.collectIsPressedAsState()
            val galleryScale by animateFloatAsState(
                targetValue = if (isGalleryPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "gallery_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = galleryInteraction,
                        indication = null
                    ) {
                        haptics.perform(HapticPattern.Pop)
                        onGalleryClick()
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Gallery",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Video Button
            val videoInteraction = remember { MutableInteractionSource() }
            val isVideoPressed by videoInteraction.collectIsPressedAsState()
            val videoScale by animateFloatAsState(
                targetValue = if (isVideoPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "video_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = videoInteraction,
                        indication = null
                    ) {
                        haptics.perform(HapticPattern.Pop)
                        onVideoClick()
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = "Video",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text Field
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                BasicTextField(
                    value = textState,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            textFieldFocused = state.isFocused
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = LocalContentColor.current,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (hasContent) {
                                haptics.perform(HapticPattern.Send)
                                onSendClick()
                            }
                        },
                        onDone = {
                            if (hasContent) {
                                haptics.perform(HapticPattern.Send)
                                onSendClick()
                            }
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (textState.text.isEmpty() && !hasAttachments) {
                                Text(
                                    text = stringResource(R.string.chat_input_caption_placeholder),
                                    color = LocalContentColor.current.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Send Button
            val sendInteraction = remember { MutableInteractionSource() }
            val isSendPressed by sendInteraction.collectIsPressedAsState()
            val sendScale by animateFloatAsState(
                targetValue = if (isSendPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "send_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        enabled = hasContent && !isSending,
                        interactionSource = sendInteraction,
                        indication = null
                    ) {
                        haptics.perform(HapticPattern.Send)
                        onSendClick()
                    }
                    .background(
                        if (hasContent && !isSending) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                if (isSending) {
                    // Linear Progress Indicator - modern loading bar
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (hasContent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
