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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MotionDurations
import android.graphics.Matrix
import kotlinx.coroutines.delay

// ============================================================================
// MARK: - MorphPolygonShape
// Optimized custom shape for morphing animations with pre-allocated path
// ============================================================================

class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationAngle: Float = 0f
) : Shape {
    private val androidPath = AndroidPath()
    private val matrix = Matrix()
    private var cachedOutline: Outline? = null
    private var cachedProgress: Float = -1f
    private var cachedRotation: Float = 0f
    private var hasLoggedError = false

    private val isValid: Boolean
        get() = cachedOutline != null &&
                cachedProgress == progress &&
                cachedRotation == rotationAngle

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (isValid) return cachedOutline!!

        return try {
            androidPath.reset()
            morph.toPath(progress, androidPath)
            val bounds = android.graphics.RectF()
            @Suppress("DEPRECATION")
            androidPath.computeBounds(bounds, false)
            val pathWidth = bounds.width()
            val pathHeight = bounds.height()

            if (pathWidth > 0 && pathHeight > 0) {
                val scaleX = size.width / pathWidth
                val scaleY = size.height / pathHeight
                val scale = minOf(scaleX, scaleY) * 0.9f
                val pathCenterX = bounds.centerX()
                val pathCenterY = bounds.centerY()
                matrix.reset()
                matrix.setTranslate(-pathCenterX, -pathCenterY)
                if (rotationAngle != 0f) matrix.postRotate(rotationAngle)
                matrix.postScale(scale, scale)
                matrix.postTranslate(size.width / 2f, size.height / 2f)
                androidPath.transform(matrix)
            }
            val outline = Outline.Generic(androidPath.asComposePath())
            cachedOutline = outline
            cachedProgress = progress
            cachedRotation = rotationAngle
            outline
        } catch (e: Exception) {
            if (!hasLoggedError) {
                Logger.e("MorphPolygonShape -> Path calculation FAILED: ${e.message}")
                hasLoggedError = true
            }
            CircleShape.createOutline(size, layoutDirection, density)
        }
    }
}

// ============================================================================
// MARK: - ExpressiveMorphingFAB
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveMorphingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes
) {
    val haptic = LocalHapticFeedback.current
    val officialShapes = remember {
        arrayOf<RoundedPolygon>(
            androidx.compose.material3.MaterialShapes.SoftBurst,
            androidx.compose.material3.MaterialShapes.Cookie9Sided,
            androidx.compose.material3.MaterialShapes.Pentagon,
            androidx.compose.material3.MaterialShapes.Pill,
            androidx.compose.material3.MaterialShapes.Sunny,
            androidx.compose.material3.MaterialShapes.Cookie4Sided,
            androidx.compose.material3.MaterialShapes.Oval
        )
    }
    val normalizedShapes = remember { officialShapes.toList() }
    val shapesCount = normalizedShapes.size
    val morphs = remember(normalizedShapes) {
        Array(shapesCount) { i ->
            Morph(normalizedShapes[i], normalizedShapes[(i + 1) % shapesCount])
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "FABLoading")
    val morphFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = shapesCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650 * shapesCount, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphFactor"
    )
    val shapeIndex = (morphFactor.toInt() % shapesCount)
    val progress = morphFactor - morphFactor.toInt()
    val rotation = (140f * morphFactor) % 360f
    val currentMorph = morphs[shapeIndex]
    val currentShape = remember(currentMorph, progress) { MorphPolygonShape(currentMorph, progress) }
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        val targetScale = if (isPressed) 0.92f else 1f
        scale.animateTo(targetScale, animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f))
    }

    Surface(
        modifier = modifier
            .padding(16.dp)
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation
                this.shape = currentShape
                clip = true
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = -rotation },
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ============================================================================
// MARK: - Noise Texture Overlay
// ============================================================================

@Composable
fun NoiseTextureOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.03f
) {
    val noiseBitmap = remember {
        android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(64 * 64)
            val random = kotlin.random.Random(42)
            for (i in pixels.indices) {
                val gray = random.nextInt(0, 256)
                pixels[i] = android.graphics.Color.argb((255 * alpha).toInt(), gray, gray, gray)
            }
            setPixels(pixels, 0, 64, 0, 0, 64, 64)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = false
                isFilterBitmap = true
            }
            val shader = android.graphics.BitmapShader(noiseBitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            paint.shader = shader
            drawContext.canvas.nativeCanvas.drawPaint(paint)
        }
    }
}

// ============================================================================
// MARK: - Radar Pulse Morph
// ============================================================================

@Composable
fun RadarPulseMorph(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val shapes = MD3EShapes.AllShapes
    val shapesCount = shapes.size
    val duration = if (isSearching) 400 else 800
    val morphFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = shapesCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration * shapesCount, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarMorphFactor"
    )
    val shapeIndex = (morphFactor.toInt() % shapesCount)
    val progress = morphFactor - morphFactor.toInt()
    val morph = remember(shapeIndex, shapes) { Morph(shapes[shapeIndex], shapes[(shapeIndex + 1) % shapesCount]) }
    val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress, rotationAngle = 0f) }
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulseScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarRingAlpha"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(size * pulseScale * 1.3f).alpha(ringAlpha), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {}
        Surface(modifier = Modifier.size(size), shape = morphShape, color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.4f))
            }
        }
    }
}

// ============================================================================
// MARK: - ExpressivePulseHeader (The VOID Edition)
// ============================================================================

@Composable
fun ExpressivePulseHeader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shapesCount = shapes.size
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex = (currentShapeIndex + 1) % shapesCount
    
    val morph = remember(currentShapeIndex, nextShapeIndex) {
        Morph(shapes[currentShapeIndex], shapes[nextShapeIndex])
    }

    val infiniteTransition = rememberInfiniteTransition(label = "VoidPulse")
    
    // 1. Smooth Morph Progress (Using Spring for organic feel)
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(currentShapeIndex) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
        currentShapeIndex = nextShapeIndex
    }

    // 2. Edge Jitter (High frequency micro-movements for fluid look)
    val jitter by infiniteTransition.animateFloat(
        initialValue = -0.015f,
        targetValue = 0.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Jitter"
    )

    // 3. Breathing Pulse (Scale)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // 4. Rotation (Slow & Momentum-based)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val morphShape = remember(morph, progressAnim.value, jitter) {
        MorphPolygonShape(morph, (progressAnim.value + jitter).coerceIn(0f, 1f), 0f)
    }

    // Dynamic Colors from MaterialTheme
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                rotationZ = rotation
            }
            // 5. The Halo Effect (Glow) - Dynamic Color
            .drawBehind {
                val glowSize = size.toPx() * 1.25f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = glowSize / 2
                    ),
                    radius = glowSize / 2
                )
            }
            .clip(morphShape)
            .background(containerColor)
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        contentAlignment = Alignment.Center
    ) {
        // Counter-rotate content to keep it upright
        Box(modifier = Modifier.graphicsLayer { rotationZ = -rotation }) {
            content()
        }
    }
}

// ============================================================================
// MARK: - MorphingAvatar
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
    val avatarShapes = remember { listOf(MD3EShapes.Blob, MD3EShapes.Circle, MD3EShapes.Clover) }
    if (isOnline) {
        var currentShapeIndex by remember { mutableIntStateOf(0) }
        val nextShapeIndex by remember(currentShapeIndex, avatarShapes.size) { derivedStateOf { (currentShapeIndex + 1) % avatarShapes.size } }
        val morph by remember(currentShapeIndex, nextShapeIndex, avatarShapes) { derivedStateOf { Morph(avatarShapes[currentShapeIndex], avatarShapes[nextShapeIndex]) } }
        val infiniteTransition = rememberInfiniteTransition(label = "AvatarMorph")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(MotionDurations.Long, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
            label = "MorphProgress"
        )
        LaunchedEffect(progress) { if (progress >= 0.98f) currentShapeIndex = nextShapeIndex }
        val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress) }
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(size), shape = morphShape, color = containerColor) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.27f).clip(CircleShape).background(Color(0xFF4CAF50)))
        }
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(size), shape = CircleShape, color = containerColor.copy(alpha = 0.6f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// MARK: - SignalMorphAvatar
// ============================================================================

@Composable
fun SignalMorphAvatar(
    initials: String,
    signalStrength: com.p2p.meshify.domain.model.SignalStrength,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    val shapes = when (signalStrength) {
        com.p2p.meshify.domain.model.SignalStrength.STRONG -> listOf(MD3EShapes.Sunny, MD3EShapes.Breezy)
        com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> listOf(MD3EShapes.Breezy, MD3EShapes.Circle)
        com.p2p.meshify.domain.model.SignalStrength.WEAK -> listOf(MD3EShapes.Circle, MD3EShapes.Blob)
        com.p2p.meshify.domain.model.SignalStrength.OFFLINE -> listOf(MD3EShapes.Circle, MD3EShapes.Circle)
    }
    val morphDuration = when (signalStrength) {
        com.p2p.meshify.domain.model.SignalStrength.STRONG -> 500
        com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> 900
        com.p2p.meshify.domain.model.SignalStrength.WEAK -> 1500
        com.p2p.meshify.domain.model.SignalStrength.OFFLINE -> 0
    }
    if (signalStrength == com.p2p.meshify.domain.model.SignalStrength.OFFLINE) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        var currentShapeIndex by remember { mutableIntStateOf(0) }
        val nextShapeIndex by remember(currentShapeIndex, shapes.size) { derivedStateOf { (currentShapeIndex + 1) % shapes.size } }
        val morph by remember(currentShapeIndex, nextShapeIndex, shapes) { derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) } }
        val infiniteTransition = rememberInfiniteTransition(label = "SignalMorph")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(morphDuration, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
            label = "SignalMorphProgress"
        )
        LaunchedEffect(progress) { if (progress >= 0.98f) currentShapeIndex = nextShapeIndex }
        val morphShape = remember(morph, progress) { MorphPolygonShape(morph, progress) }
        val containerColor = when (signalStrength) {
            com.p2p.meshify.domain.model.SignalStrength.STRONG -> MaterialTheme.colorScheme.primaryContainer
            com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val onContainerColor = when (signalStrength) {
            com.p2p.meshify.domain.model.SignalStrength.STRONG -> MaterialTheme.colorScheme.onPrimaryContainer
            com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        }
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(size), shape = morphShape, color = containerColor) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// MARK: - FullImageViewer
// ============================================================================

@Composable
fun FullImageViewer(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val oldScale = scale
                            val newScale = if (oldScale > 1f) 1f else 2.5f
                            scale = newScale
                            offset = if (newScale > 1f) tapOffset.copy(x = -tapOffset.x * (newScale - 1), y = -tapOffset.y * (newScale - 1)) else Offset.Zero
                        },
                        onTap = { onDismiss() }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotate ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        rotation += rotate
                        offset = offset.copy(x = offset.x + pan.x, y = offset.y + pan.y)
                        if (scale <= 1f) { scale = 1f; offset = Offset.Zero; rotation = 0f }
                    }
                }
        ) {
            AsyncImage(model = imagePath, contentDescription = "Full Image", modifier = Modifier.fillMaxSize().blur(20.dp).alpha(0.3f), contentScale = ContentScale.Crop)
            AsyncImage(model = imagePath, contentDescription = "Full Image", modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(40.dp).clickable { onDismiss() }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

/**
 * ✅ MD3E Delayed Delete Confirmation Dialog.
 */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }
    val isEnabled by remember { derivedStateOf { countdown <= 0 } }
    LaunchedEffect(Unit) { while (countdown > 0) { delay(1000); countdown-- } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                if (!isEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (5 - countdown) / 5f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = isEnabled, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))) {
                Text(if (isEnabled) "Delete" else "Wait ($countdown)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
        modifier = modifier.then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }) else Modifier).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = effectiveShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = content
    )
}
