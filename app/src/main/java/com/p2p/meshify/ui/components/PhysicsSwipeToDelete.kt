package com.p2p.meshify.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.p2p.meshify.R
import com.p2p.meshify.ui.hooks.HapticPattern
import com.p2p.meshify.ui.hooks.LocalPremiumHaptics
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

enum class ItemPosition { ONLY, FIRST, MIDDLE, LAST }

/**
 * Provides a swipe-to-delete composable with physics-like drag behavior and an optional delete action.
 *
 * Dragging the content horizontally reveals action buttons (cancel and delete when enabled). The component
 * animates corner radii during interaction, emits haptic feedback at key interaction points, and snaps
 * the content either back to its resting position or to the revealed state depending on release distance.
 *
 * @param modifier Modifier applied to the root container.
 * @param onDelete Callback invoked when the delete action is confirmed.
 * @param deleteEnabled When `true`, swipe-to-delete actions are available; when `false`, swipe resistance is increased and delete is disabled.
 * @param position ItemPosition that influences how corner radii are applied (ONLY, FIRST, MIDDLE, LAST).
 * @param groupCornerRadius Corner radius applied to grouped outer edges.
 * @param itemCornerRadius Corner radius applied to individual (non-grouped) item edges.
 * @param content Composable content displayed inside the swipable item.
 */
@Composable
fun PhysicsSwipeToDelete(modifier: Modifier = Modifier, onDelete: () -> Unit, deleteEnabled: Boolean = true, position: ItemPosition = ItemPosition.ONLY, groupCornerRadius: Dp = 28.dp, itemCornerRadius: Dp = 4.dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current; val haptics = LocalPremiumHaptics.current; val scope = rememberCoroutineScope()
    val dragFriction = if (deleteEnabled) 0.6f else 0.15f; val revealDistancePx = with(density) { 140.dp.toPx() }
    val unlockThresholdPx = revealDistancePx * 0.25f; val magneticPullStrength = 0.3f
    val offsetX = remember { Animatable(0f) }; var isUnlocked by remember { mutableStateOf(false) }; var isDragging by remember { mutableStateOf(false) }
    val unlockProgress by remember { derivedStateOf { (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f) } }
    val groupRadiusPx = with(density) { groupCornerRadius.toPx() }; val itemRadiusPx = with(density) { itemCornerRadius.toPx() }
    val targetTopRadius = if (position == ItemPosition.ONLY || position == ItemPosition.FIRST) groupRadiusPx else itemRadiusPx
    val targetBottomRadius = if (position == ItemPosition.ONLY || position == ItemPosition.LAST) groupRadiusPx else itemRadiusPx
    val animatedTopRadius by animateFloatAsState(targetTopRadius, spring(0.6f, 400f))
    val animatedBottomRadius by animateFloatAsState(targetBottomRadius, spring(0.6f, 400f))
    val shape by remember { derivedStateOf { val finalTop = animatedTopRadius + (groupRadiusPx - animatedTopRadius) * unlockProgress; val finalBottom = animatedBottomRadius + (groupRadiusPx - animatedBottomRadius) * unlockProgress; RoundedCornerShape(topStart = with(density) { finalTop.toDp() }, topEnd = with(density) { finalTop.toDp() }, bottomEnd = with(density) { finalBottom.toDp() }, bottomStart = with(density) { finalBottom.toDp() }) } }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.matchParentSize().padding(end = 12.dp), Arrangement.spacedBy(12.dp, Alignment.End), Alignment.CenterVertically) {
            if (deleteEnabled) {
                PhysicsSwipeActionButton({ haptics.perform(HapticPattern.Cancel); scope.launch { offsetX.animateTo(0f, spring(0.5f)); isUnlocked = false } }, MaterialTheme.colorScheme.surfaceVariant, unlockProgress) { Icon(Icons.Rounded.Close, null, Modifier.size(22.dp)) }
                PhysicsSwipeActionButton({ haptics.perform(HapticPattern.Error); onDelete() }, MaterialTheme.colorScheme.errorContainer, unlockProgress) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) }
            }
        }
        Box(Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }.clip(shape).background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { isDragging = true }, onDragEnd = { isDragging = false; scope.launch { if (!deleteEnabled || offsetX.value.absoluteValue < unlockThresholdPx) { if (offsetX.value.absoluteValue > 10f) haptics.perform(HapticPattern.Thud); offsetX.animateTo(0f, spring(0.55f)); isUnlocked = false } else { if (!isUnlocked) haptics.perform(HapticPattern.Pop); isUnlocked = true; offsetX.animateTo(-revealDistancePx, spring(0.6f)) } } }, onHorizontalDrag = { change, dragAmount -> change.consume(); if (dragAmount < 0 || offsetX.value < 0) { val friction = if (offsetX.value.absoluteValue < unlockThresholdPx && !isUnlocked) dragFriction * (1f - magneticPullStrength * (offsetX.value.absoluteValue / unlockThresholdPx)) else dragFriction; val newOffset = (offsetX.value + dragAmount * friction).coerceIn(-revealDistancePx * 1.2f, 0f); scope.launch { offsetX.snapTo(newOffset) }; if (offsetX.value.absoluteValue < unlockThresholdPx && newOffset.absoluteValue >= unlockThresholdPx) haptics.perform(HapticPattern.Pop) } })
        }) { content() }
    }
}

/**
 * Renders a circular action button used in the swipe action row.
 *
 * The button is 44.dp in diameter, applies the given background color and overall alpha, and scales
 * down slightly while pressed to provide tactile feedback.
 *
 * @param onClick Callback invoked when the button is clicked.
 * @param containerColor Background color of the circular button.
 * @param alpha Overall opacity applied to the button (0f = transparent, 1f = fully opaque).
 * @param content Composable content displayed centered inside the button (typically an icon).
 */
@Composable
private fun PhysicsSwipeActionButton(onClick: () -> Unit, containerColor: Color, alpha: Float, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }; val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, spring(0.6f))
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, modifier = Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }, interactionSource = interactionSource) { Box(contentAlignment = Alignment.Center) { content() } }
}
