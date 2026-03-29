package com.p2p.meshify.core.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

enum class ItemPosition { ONLY, FIRST, MIDDLE, LAST }

/**
 * CompositionLocal to track global swipe state across all items in a list.
 * When one item is being swiped, adjacent items can react to it.
 */
data class SwipeState(
    val swipingIndex: Int = -1,
    val swipeProgress: Float = 0f
)

val LocalSwipeState = compositionLocalOf { SwipeState() }

/**
 * Enhanced PhysicsSwipeToDelete with magnetic neighbor effect.
 * When swiping, adjacent items subtly shift and scale to create a magnetic pull effect.
 */
@Composable
fun PhysicsSwipeToDelete(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    deleteEnabled: Boolean = true,
    position: ItemPosition = ItemPosition.ONLY,
    groupCornerRadius: Dp = 24.dp,
    itemCornerRadius: Dp = 0.dp,
    itemIndex: Int = -1,
    onSwipeProgress: ((Int, Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalPremiumHaptics.current
    val scope = rememberCoroutineScope()
    val dragFriction = if (deleteEnabled) 0.6f else 0.15f
    val revealDistancePx = with(density) { 140.dp.toPx() }
    val unlockThresholdPx = revealDistancePx * 0.25f
    val magneticPullStrength = 0.4f
    val offsetX = remember { Animatable(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val unlockProgress by remember { derivedStateOf { (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f) } }
    val groupRadiusPx = with(density) { groupCornerRadius.toPx() }
    val itemRadiusPx = with(density) { itemCornerRadius.toPx() }
    val targetTopRadius = if (position == ItemPosition.ONLY || position == ItemPosition.FIRST) groupRadiusPx else itemRadiusPx
    val targetBottomRadius = if (position == ItemPosition.ONLY || position == ItemPosition.LAST) groupRadiusPx else itemRadiusPx
    val animatedTopRadius by animateFloatAsState(targetTopRadius, spring(0.6f, 400f))
    val animatedBottomRadius by animateFloatAsState(targetBottomRadius, spring(0.6f, 400f))

    // Report swipe progress to parent for neighbor effects
    LaunchedEffect(unlockProgress, isDragging) {
        if (isDragging || unlockProgress > 0f) {
            onSwipeProgress?.invoke(itemIndex, unlockProgress)
        } else {
            onSwipeProgress?.invoke(itemIndex, 0f)
        }
    }

    val shape by remember { derivedStateOf {
        val finalTop = animatedTopRadius + (groupRadiusPx - animatedTopRadius) * unlockProgress
        val finalBottom = animatedBottomRadius + (groupRadiusPx - animatedBottomRadius) * unlockProgress
        RoundedCornerShape(
            topStart = with(density) { finalTop.toDp() },
            topEnd = with(density) { finalTop.toDp() },
            bottomEnd = with(density) { finalBottom.toDp() },
            bottomStart = with(density) { finalBottom.toDp() }
        )
    } }

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.matchParentSize().padding(end = 12.dp),
            Arrangement.spacedBy(12.dp, Alignment.End),
            Alignment.CenterVertically
        ) {
            if (deleteEnabled) {
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Cancel)
                        scope.launch {
                            offsetX.animateTo(0f, spring(0.5f))
                            isUnlocked = false
                            onSwipeProgress?.invoke(itemIndex, 0f)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    alpha = unlockProgress
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(22.dp))
                }
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Error)
                        onDelete()
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    alpha = unlockProgress
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                if (!deleteEnabled || offsetX.value.absoluteValue < unlockThresholdPx) {
                                    if (offsetX.value.absoluteValue > 10f) haptics.perform(HapticPattern.Thud)
                                    offsetX.animateTo(0f, spring(0.55f))
                                    isUnlocked = false
                                    onSwipeProgress?.invoke(itemIndex, 0f)
                                } else {
                                    if (!isUnlocked) haptics.perform(HapticPattern.Pop)
                                    isUnlocked = true
                                    offsetX.animateTo(-revealDistancePx, spring(0.6f))
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount < 0 || offsetX.value < 0) {
                                val friction = if (offsetX.value.absoluteValue < unlockThresholdPx && !isUnlocked) {
                                    dragFriction * (1f - magneticPullStrength * (offsetX.value.absoluteValue / unlockThresholdPx))
                                } else {
                                    dragFriction
                                }
                                val newOffset = (offsetX.value + dragAmount * friction).coerceIn(-revealDistancePx * 1.2f, 0f)
                                scope.launch { offsetX.snapTo(newOffset) }
                                if (offsetX.value.absoluteValue < unlockThresholdPx && newOffset.absoluteValue >= unlockThresholdPx) {
                                    haptics.perform(HapticPattern.Pop)
                                }
                            }
                        }
                    )
                }
        ) { content() }
    }
}

/**
 * Wrapper that applies magnetic effect to a chat item based on neighbor swipe state.
 */
@Composable
fun MagneticChatItem(
    index: Int,
    swipingIndex: Int,
    swipeProgress: Float,
    content: @Composable () -> Unit
) {
    // Calculate magnetic effect based on distance from swiping item
    val distance = (index - swipingIndex).absoluteValue
    val isAffected = swipingIndex >= 0 && distance in 1..2 && swipeProgress > 0f

    val targetOffsetX = if (isAffected) {
        val strength = swipeProgress * (1f / distance) * 12f
        -strength
    } else 0f

    val targetScale = if (isAffected) {
        1f - (swipeProgress * (1f / distance) * 0.015f)
    } else 1f

    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "magnetic_offset"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "magnetic_scale"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            translationX = animatedOffsetX
            scaleX = animatedScale
            scaleY = animatedScale
        }
    ) {
        content()
    }
}

@Composable
private fun PhysicsSwipeActionButton(
    onClick: () -> Unit,
    containerColor: Color,
    alpha: Float,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, spring(0.6f))
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(44.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
