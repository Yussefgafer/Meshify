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
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.ui.hooks.HapticPattern
import com.p2p.meshify.ui.hooks.rememberPremiumHaptics
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Position of item in a group for corner radius calculation.
 * Ported from LastChat.
 */
enum class ItemPosition {
    ONLY,   // Only item in group - all corners rounded
    FIRST,  // First item - top corners rounded
    MIDDLE, // Middle item - no corners rounded
    LAST    // Last item - bottom corners rounded
}

/**
 * Physics-based swipe-to-delete component.
 * Ported from LastChat for Meshify.
 */
@Composable
fun PhysicsSwipeToDelete(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    deleteEnabled: Boolean = true,
    position: ItemPosition = ItemPosition.ONLY,
    groupCornerRadius: Dp = 28.dp,
    itemCornerRadius: Dp = 4.dp,
    settingsRepository: ISettingsRepository,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = rememberPremiumHaptics(settingsRepository)
    val scope = rememberCoroutineScope()
    
    val dragFriction = if (deleteEnabled) 0.6f else 0.15f
    val revealDistancePx = with(density) { 140.dp.toPx() }
    val unlockThresholdPx = revealDistancePx * 0.25f
    val magneticPullStrength = 0.3f
    
    val offsetX = remember { Animatable(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    val unlockProgress by remember {
        derivedStateOf {
            (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f)
        }
    }
    
    val groupRadiusPx = with(density) { groupCornerRadius.toPx() }
    val itemRadiusPx = with(density) { itemCornerRadius.toPx() }
    
    val targetTopRadius = when (position) {
        ItemPosition.ONLY, ItemPosition.FIRST -> groupRadiusPx
        else -> itemRadiusPx
    }
    val targetBottomRadius = when (position) {
        ItemPosition.ONLY, ItemPosition.LAST -> groupRadiusPx
        else -> itemRadiusPx
    }
    
    val animatedTopRadius by animateFloatAsState(
        targetValue = targetTopRadius,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "topRadius"
    )
    val animatedBottomRadius by animateFloatAsState(
        targetValue = targetBottomRadius,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "bottomRadius"
    )
    
    val shape by remember {
        derivedStateOf {
            val finalTop = animatedTopRadius + (groupRadiusPx - animatedTopRadius) * unlockProgress
            val finalBottom = animatedBottomRadius + (groupRadiusPx - animatedBottomRadius) * unlockProgress
            
            RoundedCornerShape(
                topStart = with(density) { finalTop.toDp() },
                topEnd = with(density) { finalTop.toDp() },
                bottomEnd = with(density) { finalBottom.toDp() },
                bottomStart = with(density) { finalBottom.toDp() }
            )
        }
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Background Actions
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (deleteEnabled) {
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Cancel)
                        scope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = 0.5f))
                            isUnlocked = false
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    alpha = unlockProgress
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(22.dp))
                }
                
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Error)
                        onDelete()
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    alpha = unlockProgress
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                }
            }
        }
        
        // Foreground Content
        Box(
            modifier = Modifier
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
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.55f))
                                    isUnlocked = false
                                } else {
                                    if (!isUnlocked) haptics.perform(HapticPattern.Pop)
                                    isUnlocked = true
                                    offsetX.animateTo(-revealDistancePx, spring(dampingRatio = 0.6f))
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
        ) {
            content()
        }
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
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, spring(dampingRatio = 0.6f))
    
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
