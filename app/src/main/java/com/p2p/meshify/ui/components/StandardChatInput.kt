package com.p2p.meshify.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.R
import com.p2p.meshify.ui.hooks.HapticPattern
import com.p2p.meshify.ui.hooks.LocalPremiumHaptics

/**
 * ✅ MD3E Standard Chat Input Bar.
 * Ported from LastChat "Standard" style for Meshify.
 * Features:
 * - Floating pill-shaped container
 * - Physics-based animations for buttons
 * - Expandable attachment menu with rotation
 * - Integrated send button with state awareness
 */
/**
 * Renders a floating, pill-shaped chat input with an expandable attachment menu and integrated send button.
 *
 * The input shows a plus button to toggle an attachment menu (gallery, camera, file), a resizable text field, and a send button
 * that is enabled only when `text` is not blank.
 *
 * @param text Current text value displayed in the input field.
 * @param onTextChange Called when the text changes.
 * @param onSend Called when the send button is pressed.
 * @param onAttachClick Called when an attachment option is selected; receives one of `"image"`, `"camera"`, or `"file"`.
 * @param modifier Optional layout modifier applied to the root container.
 */
@Composable
fun StandardChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: (String) -> Unit, // "image", "file", "camera"

    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current
    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "plus_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Attachment Menu (Visible when expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.8f)) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentIconButton(Icons.Rounded.Photo, "Gallery") { onAttachClick("image"); isExpanded = false }
                    AttachmentIconButton(Icons.Rounded.CameraAlt, "Camera") { onAttachClick("camera"); isExpanded = false }
                    AttachmentIconButton(Icons.Rounded.FolderOpen, "File") { onAttachClick("file"); isExpanded = false }
                }
            }
        }

        // Main Input Bar
        Surface(
            shape = RoundedCornerShape(35.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Plus Button
                val plusInteraction = remember { MutableInteractionSource() }
                val isPlusPressed by plusInteraction.collectIsPressedAsState()
                val plusScale by animateFloatAsState(if (isPlusPressed) 0.96f else 1f, spring(dampingRatio = 0.6f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { scaleX = plusScale; scaleY = plusScale }
                        .clip(CircleShape)
                        .clickable(interactionSource = plusInteraction, indication = null) {
                            haptics.perform(HapticPattern.Pop)
                            isExpanded = !isExpanded
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "More",
                        modifier = Modifier.rotate(rotation),
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Text Field Capsule
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text(stringResource(R.string.input_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 5
                    )
                }

                // Send Button
                val hasText = text.isNotBlank()
                val sendInteraction = remember { MutableInteractionSource() }
                val isSendPressed by sendInteraction.collectIsPressedAsState()
                val sendScale by animateFloatAsState(if (isSendPressed) 0.96f else 1f, spring(dampingRatio = 0.6f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        .clip(CircleShape)
                        .clickable(
                            enabled = hasText,
                            interactionSource = sendInteraction,
                            indication = null
                        ) {
                            haptics.perform(HapticPattern.Send)
                            onSend()
                        }
                        .background(if (hasText) MaterialTheme.colorScheme.primary else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (hasText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
