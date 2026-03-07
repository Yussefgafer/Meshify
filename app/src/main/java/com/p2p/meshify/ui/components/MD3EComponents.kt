package com.p2p.meshify.ui.components

import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MotionDurations
import com.p2p.meshify.ui.theme.MotionSpecs

/**
 * MD3E Expressive Morphing FAB - Fixed Version.
 * Uses Surface as background with proper elevation and visible shape morphing.
 * Works correctly in both Light and Dark modes.
 */
@Composable
fun ExpressiveMorphingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes
) {
    val haptic = LocalHapticFeedback.current
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex by derivedStateOf { (currentShapeIndex + 1) % shapes.size }

    val morph by remember(currentShapeIndex) {
        derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "FABMorph")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDurations.ExtraLong, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphProgress"
    )

    LaunchedEffect(progress) {
        if (progress >= 0.98f) {
            currentShapeIndex = nextShapeIndex
        }
    }

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDurations.ExtraLong * shapes.size, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FABRotation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val androidPath = remember { AndroidPath() }

    // ✅ FIX: Use Surface with elevation as background, clip to morphing shape
    Surface(
        modifier = modifier
            .padding(16.dp)
            .size(size)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = primaryColor,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    androidPath.reset()
                    morph.toPath(progress, androidPath)
                    val sizeValue = size.value.dp.toPx() / 2.2f
                    scale(sizeValue) {
                        // Draw the morphing shape as a clip mask
                        drawPath(path = androidPath.asComposePath(), color = Color.White.copy(alpha = 0.15f))
                    }
                }
                .clip(androidPath.asComposePath()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = onPrimaryColor
            )
        }
    }
}

/**
 * MD3E Expressive Pulse Header.
 * Animated shape morphing for profile/settings headers.
 */
@Composable
fun ExpressivePulseHeader(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 120.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex by derivedStateOf { (currentShapeIndex + 1) % shapes.size }
    
    val morph by remember(currentShapeIndex) {
        derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "PulseMorph")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDurations.Long, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseProgress"
    )
    
    LaunchedEffect(progress) {
        if (progress >= 0.98f) {
            currentShapeIndex = nextShapeIndex
        }
    }
    
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val androidPath = remember { AndroidPath() }
    
    Box(
        modifier = modifier
            .size(size)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            .drawBehind {
                androidPath.reset()
                morph.toPath(progress, androidPath)
                val sizeValue = size.value.dp.toPx() / 2.2f
                scale(sizeValue) {
                    drawPath(path = androidPath.asComposePath(), color = containerColor)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * MD3E Expressive Button with Spring Physics.
 * Applies scale animation and haptic feedback on press.
 * Defaults to theme motion settings via LocalMeshifyMotion.
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    springSpec: SpringSpec<Float>? = null, // null = use theme default
    densityScale: Float? = null, // null = use theme default
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val motion = LocalMeshifyMotion.current
    val themeConfig = LocalMeshifyThemeConfig.current
    
    val effectiveSpring = springSpec ?: motion.springSpec
    val effectiveDensity = densityScale ?: themeConfig.visualDensity
    
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // Track pressed state via InteractionSource
    val isPressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(isPressed, enabled) {
        if (!enabled) {
            scale.snapTo(0.95f)
        } else {
            val targetScale = if (isPressed) 0.92f else 1f
            scale.animateTo(targetScale, animationSpec = effectiveSpring)
        }
    }
    
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .graphicsLayer {
                scaleX = scale.value * effectiveDensity
                scaleY = scale.value * effectiveDensity
            },
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 2.dp
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = (24.dp * effectiveDensity).coerceIn(16.dp, 32.dp),
                    vertical = (12.dp * effectiveDensity).coerceIn(8.dp, 16.dp)
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * MD3E Expressive Card with press effects and theme integration.
 * Defaults to theme motion settings via LocalMeshifyMotion.
 */
@Composable
fun ExpressiveCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape? = null,
    springSpec: SpringSpec<Float>? = null, // null = use theme default
    densityScale: Float? = null, // null = use theme default
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val motion = LocalMeshifyMotion.current
    val themeConfig = LocalMeshifyThemeConfig.current
    
    val effectiveSpring = springSpec ?: motion.springSpec
    val effectiveDensity = densityScale ?: themeConfig.visualDensity
    val effectiveShape = shape ?: RoundedCornerShape((28.dp * effectiveDensity).coerceIn(20.dp, 36.dp))
    
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // Track pressed state via InteractionSource
    val isPressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(isPressed) {
        val targetScale = if (isPressed) 0.96f else 1f
        scale.animateTo(targetScale, animationSpec = effectiveSpring)
    }
    
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        shape = effectiveShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        content = content
    )
}

/**
 * MD3E Morphing Avatar Component.
 * Morphs between 3 soft shapes (Blob → Circle → Clover) to indicate online status.
 * - Online: Fast morphing animation showing "connection vitality"
 * - Offline: Static circle with gray overlay
 */
@Composable
fun MorphingAvatar(
    initials: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val androidPath = remember { AndroidPath() }
    
    // Three soft shapes for avatar morphing (no sharp edges)
    val avatarShapes = remember {
        listOf(
            MD3EShapes.Blob,    // Organic blob
            MD3EShapes.Circle,  // Perfect circle
            MD3EShapes.Clover   // 4-leaf clover
        )
    }

    if (isOnline) {
        // ✅ Online: Animate morphing between shapes
        var currentShapeIndex by remember { mutableIntStateOf(0) }
        val nextShapeIndex by derivedStateOf { (currentShapeIndex + 1) % avatarShapes.size }

        val morph by remember(currentShapeIndex) {
            derivedStateOf { Morph(avatarShapes[currentShapeIndex], avatarShapes[nextShapeIndex]) }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "AvatarMorph")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MotionDurations.Long, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MorphProgress"
        )

        LaunchedEffect(progress) {
            if (progress >= 0.98f) {
                currentShapeIndex = nextShapeIndex
            }
        }

        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(size)
                    .drawBehind {
                        androidPath.reset()
                        morph.toPath(progress, androidPath)
                        val sizeValue = size.value.dp.toPx() / 2.2f
                        scale(sizeValue) {
                            drawPath(path = androidPath.asComposePath(), color = containerColor)
                        }
                    },
                color = Color.Transparent
            ) {
                Text(
                    text = initials.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainerColor,
                    fontWeight = FontWeight.Bold
                )
            }
            // Online indicator dot
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.27f)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    } else {
        // ❌ Offline: Static circle with gray overlay
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(size),
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = onContainerColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * MD3E Signal Morph Avatar Component.
 * Morphs between shapes based on signal strength (RSSI) for Discovery screen.
 * 
 * Signal Strength Mapping:
 * - STRONG (> -50 dBm): Sunny ↔ Breezy, fast morphing (500ms), vibrant color
 * - MEDIUM (-50 to -70 dBm): Breezy ↔ Circle, medium speed (900ms), muted color
 * - WEAK (< -70 dBm): Circle ↔ Blob, slow morphing (1500ms), desaturated
 * - OFFLINE: Static circle, no animation, gray overlay
 */
@Composable
fun SignalMorphAvatar(
    initials: String,
    signalStrength: com.p2p.meshify.domain.model.SignalStrength,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    val androidPath = remember { AndroidPath() }
    
    // Get shape pair and duration based on signal strength
    val shapes = signalStrength.getShapePair()
    val morphDuration = signalStrength.getMorphDuration()

    if (signalStrength == com.p2p.meshify.domain.model.SignalStrength.OFFLINE) {
        // ❌ Offline: Static circle with gray overlay
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(size),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // ✅ Online: Morph between shapes based on signal strength
        var currentShapeIndex by remember { mutableIntStateOf(0) }
        val nextShapeIndex by derivedStateOf { (currentShapeIndex + 1) % shapes.size }

        val morph by remember(currentShapeIndex) {
            derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "SignalMorph")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(morphDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SignalMorphProgress"
        )

        LaunchedEffect(progress) {
            if (progress >= 0.98f) {
                currentShapeIndex = nextShapeIndex
            }
        }

        // Determine container color based on signal strength
        val containerColor = when (signalStrength) {
            com.p2p.meshify.domain.model.SignalStrength.STRONG -> 
                MaterialTheme.colorScheme.primaryContainer
            com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> 
                MaterialTheme.colorScheme.secondaryContainer
            com.p2p.meshify.domain.model.SignalStrength.WEAK -> 
                MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

        val onContainerColor = when (signalStrength) {
            com.p2p.meshify.domain.model.SignalStrength.STRONG -> 
                MaterialTheme.colorScheme.onPrimaryContainer
            com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> 
                MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        }

        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(size)
                    .drawBehind {
                        androidPath.reset()
                        morph.toPath(progress, androidPath)
                        val sizeValue = size.value.dp.toPx() / 2.2f
                        scale(sizeValue) {
                            drawPath(path = androidPath.asComposePath(), color = containerColor)
                        }
                    },
                color = Color.Transparent
            ) {
                Text(
                    text = initials.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainerColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
