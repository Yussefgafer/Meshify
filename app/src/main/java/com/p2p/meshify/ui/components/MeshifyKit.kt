package com.p2p.meshify.ui.components

import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MotionDurations
import android.graphics.Matrix

// ============================================================================
// MARK: - MorphPolygonShape
// Optimized custom shape for morphing animations with pre-allocated path
// ============================================================================

/**
 * Optimized MorphPolygonShape for morphing animations.
 * Uses pre-allocated AndroidPath to reduce GC pressure during frame rendering.
 * Includes fallback to CircleShape if path calculation fails.
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    // Pre-allocated path to avoid allocation during createOutline
    private val androidPath = AndroidPath()
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return try {
            androidPath.reset()
            morph.toPath(progress, androidPath)

            // Get path bounds
            val bounds = android.graphics.RectF()
            androidPath.computeBounds(bounds, false)

            // Calculate scale to fit into size
            val pathWidth = bounds.width()
            val pathHeight = bounds.height()

            if (pathWidth > 0 && pathHeight > 0) {
                val scaleX = size.width / pathWidth
                val scaleY = size.height / pathHeight
                val scale = minOf(scaleX, scaleY) * 0.9f // 90% to leave some padding

                // Reset and apply transform: translate to origin, scale, then center
                matrix.reset()
                matrix.postScale(scale, scale)
                matrix.postTranslate(
                    (size.width - pathWidth * scale) / 2 - bounds.left * scale,
                    (size.height - pathHeight * scale) / 2 - bounds.top * scale
                )
                androidPath.transform(matrix)
            }

            Outline.Generic(androidPath.asComposePath())
        } catch (e: Exception) {
            // Fallback to CircleShape if path calculation fails
            val circleOutline = CircleShape.createOutline(size, layoutDirection, density)
            circleOutline
        }
    }
}

// ============================================================================
// MARK: - ExpressiveMorphingFAB
// MD3E Expressive Morphing FAB with decoupled morph and icon animation
// ============================================================================

/**
 * MD3E Expressive Morphing FAB with proper easing and decoupled animations.
 * - Background shape morphs with FastOutSlowInEasing
 * - Background rotates with LinearEasing over 3000ms
 * - Icon remains stable (no rotation) with subtle scale pulse
 */
@Composable
fun ExpressiveMorphingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes
) {
    val haptic = LocalHapticFeedback.current
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex by derivedStateOf { (currentShapeIndex + 1) % shapes.size }

    val morph by remember(currentShapeIndex) {
        derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "FABMorph")
    
    // ✅ Morph with FastOutSlowInEasing for expressive feel
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDurations.ExtraLong, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphProgress"
    )

    // ✅ Rotation with LinearEasing over 3000ms (matching LastChat)
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FABRotation"
    )

    // ✅ Subtle scale pulse for icon (independent from background rotation)
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IconScale"
    )

    LaunchedEffect(morphProgress) {
        if (morphProgress >= 0.98f) {
            currentShapeIndex = nextShapeIndex
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val morphShape = remember(morph, morphProgress) {
        MorphPolygonShape(morph, morphProgress)
    }

    Surface(
        modifier = modifier
            .padding(16.dp)
            .size(size)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            // ✅ Apply rotation ONLY to background shape
            .graphicsLayer { rotationZ = rotationAngle },
        color = primaryColor,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = morphShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            // ✅ Icon is stable - only subtle scale pulse, NO rotation
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        // Counter-rotate icon to keep it upright
                        rotationZ = -rotationAngle
                    },
                tint = onPrimaryColor
            )
        }
    }
}

// ============================================================================
// MARK: - ExpressivePulseHeader
// MD3E Expressive Pulse Header for profile/settings screens
// ============================================================================

@Composable
fun ExpressivePulseHeader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
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
            animation = tween(MotionDurations.Long, easing = FastOutSlowInEasing),
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
    val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress) }

    Box(
        modifier = modifier
            .size(size)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            .clip(morphShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ============================================================================
// MARK: - ExpressiveButton
// MD3E Expressive Button with spring physics and haptic feedback
// ============================================================================

@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    springSpec: SpringSpec<Float>? = null,
    densityScale: Float? = null,
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

// ============================================================================
// MARK: - ExpressiveCard
// MD3E Expressive Card with press effects
// ============================================================================

@Composable
fun ExpressiveCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape? = null,
    springSpec: SpringSpec<Float>? = null,
    densityScale: Float? = null,
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

// ============================================================================
// MARK: - MorphingAvatar
// MD3E Morphing Avatar with online/offline states
// ============================================================================

@Composable
fun MorphingAvatar(
    initials: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val avatarShapes = remember {
        listOf(
            MD3EShapes.Blob,
            MD3EShapes.Circle,
            MD3EShapes.Clover
        )
    }

    if (isOnline) {
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
                animation = tween(MotionDurations.Long, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MorphProgress"
        )

        LaunchedEffect(progress) {
            if (progress >= 0.98f) {
                currentShapeIndex = nextShapeIndex
            }
        }

        val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress) }

        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(size),
                shape = morphShape,
                color = containerColor
            ) {
                Text(
                    text = initials.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainerColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.27f)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    } else {
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

// ============================================================================
// MARK: - SignalMorphAvatar
// MD3E Signal Morph Avatar for Discovery screen
// ============================================================================

@Composable
fun SignalMorphAvatar(
    initials: String,
    signalStrength: SignalStrength,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    val shapes = signalStrength.let {
        when (it) {
            SignalStrength.STRONG -> listOf(MD3EShapes.Sunny, MD3EShapes.Breezy)
            SignalStrength.MEDIUM -> listOf(MD3EShapes.Breezy, MD3EShapes.Circle)
            SignalStrength.WEAK -> listOf(MD3EShapes.Circle, MD3EShapes.Blob)
            SignalStrength.OFFLINE -> listOf(MD3EShapes.Circle, MD3EShapes.Circle)
        }
    }
    val morphDuration = when (signalStrength) {
        SignalStrength.STRONG -> 500
        SignalStrength.MEDIUM -> 900
        SignalStrength.WEAK -> 1500
        SignalStrength.OFFLINE -> 0
    }

    if (signalStrength == SignalStrength.OFFLINE) {
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
                animation = tween(morphDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SignalMorphProgress"
        )

        LaunchedEffect(progress) {
            if (progress >= 0.98f) {
                currentShapeIndex = nextShapeIndex
            }
        }

        val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress) }

        val containerColor = when (signalStrength) {
            SignalStrength.STRONG -> MaterialTheme.colorScheme.primaryContainer
            SignalStrength.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
            SignalStrength.WEAK -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

        val onContainerColor = when (signalStrength) {
            SignalStrength.STRONG -> MaterialTheme.colorScheme.onPrimaryContainer
            SignalStrength.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        }

        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(size),
                shape = morphShape,
                color = containerColor
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

// ============================================================================
// MARK: - AttachmentOptions
// MD3E Attachment Bottom Sheet for chat screen
// ============================================================================

@Composable
fun AttachmentOptions(onImageClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
            ) {}
        }

        Text(
            text = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.attach_file_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        AttachmentDrawerItem(
            icon = Icons.Default.Image,
            label = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.attach_image),
            subtext = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.attach_from_gallery),
            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onImageClick,
            enabled = true
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        AttachmentDrawerItem(
            icon = Icons.Default.PhotoCamera,
            label = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.attach_camera),
            subtext = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.coming_soon),
            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { },
            enabled = false
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        AttachmentDrawerItem(
            icon = Icons.Default.InsertDriveFile,
            label = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.attach_file),
            subtext = androidx.compose.ui.res.stringResource(com.p2p.meshify.R.string.coming_soon),
            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { },
            enabled = false
        )
    }
}

// ============================================================================
// MARK: - Shimmer Effect
// Premium shimmer loading effect for images (LastChat style)
// ============================================================================

/**
 * Shimmer loading effect for AsyncImage placeholders.
 * Uses diagonal LinearGradient for premium feel.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    return this.then(
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                start = Offset(0f, 0f),
                end = Offset(300f, 300f)
            )
        )
    )
}

@Composable
fun AttachmentDrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtext: String,
    iconBackgroundColor: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconBackgroundColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
    }
}

// ============================================================================
// MARK: - FullImageViewer
// Immersive full-screen image viewer with zoom and blur
// ============================================================================

/**
 * Enhanced Full Screen Image Viewer with zoom and blur effects.
 * Supports:
 * - Double tap to zoom
 * - Pinch to zoom
 * - Pan gestures
 * - Blur background (Android 12+)
 */
@Composable
fun FullImageViewer(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            scale = if (scale > 1f) {
                                1f
                            } else {
                                2.5f
                            }
                            offset = if (scale > 1f) {
                                tapOffset.copy(
                                    x = -tapOffset.x * (scale - 1),
                                    y = -tapOffset.y * (scale - 1)
                                )
                            } else {
                                Offset.Zero
                            }
                        },
                        onTap = { onDismiss() }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotate ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        rotation += rotate
                        offset = offset.copy(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                        
                        if (scale <= 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        }
                    }
                }
        ) {
            AsyncImage(
                model = imagePath,
                contentDescription = "Full Image",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .alpha(0.3f),
                contentScale = ContentScale.Crop
            )

            AsyncImage(
                model = imagePath,
                contentDescription = "Full Image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        rotationZ = rotation,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )

            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(40.dp)
                    .clickable { onDismiss() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
