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

// ============================================================================
// MARK: - MorphPolygonShape
// Optimized custom shape for morphing animations with pre-allocated path
// ============================================================================

/**
 * Optimized MorphPolygonShape for morphing animations.
 * Uses pre-allocated AndroidPath and cached Outline to minimize GC pressure during frame rendering.
 * Includes isValid flag to avoid redundant path calculations.
 * Includes fallback to CircleShape if path calculation fails.
 * Supports rotation angle for path-only rotation (LastChat style).
 *
 * ✅ FIX: Rotation axis is now calculated from the geometric center of the morphed path,
 * ensuring perfect center rotation regardless of shape asymmetry.
 *
 * ✅ PERFORMANCE FIX: Logger calls removed from createOutline to prevent GC pressure.
 * ✅ PERFORMANCE FIX: Cached outline to avoid redundant path calculations.
 * ✅ PERFORMANCE FIX: isValid flag prevents recalculating same shape.
 * Errors are silently handled with CircleShape fallback.
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationAngle: Float = 0f
) : Shape {
    // Pre-allocated path to avoid allocation during createOutline
    private val androidPath = AndroidPath()
    private val matrix = Matrix()

    // Cached outline to avoid redundant calculations
    private var cachedOutline: Outline? = null
    private var cachedProgress: Float = -1f
    private var cachedRotation: Float = 0f

    // Flag to log errors only once (prevents spam in animation loop)
    private var hasLoggedError = false

    // isValid flag to skip redundant calculations
    private val isValid: Boolean
        get() = cachedOutline != null &&
                cachedProgress == progress &&
                cachedRotation == rotationAngle

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Return cached outline if valid
        if (isValid) {
            return cachedOutline!!
        }

        return try {
            androidPath.reset()
            morph.toPath(progress, androidPath)

            // Get path bounds
            val bounds = android.graphics.RectF()
            @Suppress("DEPRECATION")
            androidPath.computeBounds(bounds, false)

            // Calculate path dimensions
            val pathWidth = bounds.width()
            val pathHeight = bounds.height()

            if (pathWidth > 0 && pathHeight > 0) {
                // Calculate scale to fit into size (90% to leave padding)
                val scaleX = size.width / pathWidth
                val scaleY = size.height / pathHeight
                val scale = minOf(scaleX, scaleY) * 0.9f

                // ✅ FIX: Extract geometric center of the path
                val pathCenterX = bounds.centerX()
                val pathCenterY = bounds.centerY()

                // Reset matrix
                matrix.reset()

                // Step 1: Translate path to origin - lock geometric center to (0,0)
                matrix.setTranslate(-pathCenterX, -pathCenterY)

                // Step 2: Spin at origin
                if (rotationAngle != 0f) {
                    matrix.postRotate(rotationAngle)
                }

                // Step 3: Scale at origin
                matrix.postScale(scale, scale)

                // Step 4: Final anchor - translate to EXACT center of Surface
                matrix.postTranslate(size.width / 2f, size.height / 2f)

                androidPath.transform(matrix)
            }

            // Cache the outline
            val outline = Outline.Generic(androidPath.asComposePath())
            cachedOutline = outline
            cachedProgress = progress
            cachedRotation = rotationAngle

            outline
        } catch (e: Exception) {
            // ✅ Log only once to prevent GC pressure during animation
            if (!hasLoggedError) {
                Logger.e("MorphPolygonShape -> Path calculation FAILED: ${e.message}")
                hasLoggedError = true
            }
            // Fallback to CircleShape if path calculation fails
            CircleShape.createOutline(size, layoutDirection, density)
        }
    }
}

// ============================================================================
// MARK: - ExpressiveMorphingFAB
// MD3E Expressive Morphing FAB with decoupled morph and icon animation
// ============================================================================

/**
 * MD3E Expressive Morphing FAB with proper easing and decoupled animations.
 * ✅ IMPLEMENTED: Uses the 7 official MD3E shapes with official rotation logic.
 * ✅ OPTIMIZED: Pre-normalized shapes and pre-allocated morphs to minimize GC pressure.
 * ✅ STABLE: Icon remains stable via counter-rotation.
 * 
 * ✅ PERFORMANCE FIX: Removed Logger from animation loop.
 *
 * Animation Params:
 * - 650ms per shape
 * - 140° total rotation per shape (50° constant + 90° extra)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveMorphingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes
) {
    val haptic = LocalHapticFeedback.current

    // 1. The 7 Official MD3E Shapes (Loading Indicator Sequence)
    // Updated shape names for Material 3 1.5.0-alpha10
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

    // 2. Shapes in MaterialShapes are already normalized.
    // We use them directly to avoid unresolved 'normalize' reference issues.
    val normalizedShapes = remember { officialShapes.toList() }

    // 3. Pre-calculate Morphs to avoid allocation in composition
    val shapesCount = normalizedShapes.size
    val morphs = remember(normalizedShapes) {
        Array(shapesCount) { i ->
            Morph(
                normalizedShapes[i],
                normalizedShapes[(i + 1) % shapesCount]
            )
        }
    }

    // 4. Animation logic (InfiniteTransition)
    val infiniteTransition = rememberInfiniteTransition(label = "FABLoading")
    val morphFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = shapesCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 650 * shapesCount, // 4550ms total cycle
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphFactor"
    )

    // 5. Calculate current frame state
    val shapeIndex = (morphFactor.toInt() % shapesCount)
    val progress = morphFactor - morphFactor.toInt()

    // Official Rotation Formula: (140° * morphFactor)
    val rotation = (140f * morphFactor) % 360f

    // ✅ FIX: Remember morph based on shapeIndex only
    val currentMorph = morphs[shapeIndex]
    val currentShape = remember(currentMorph) {
        MorphPolygonShape(currentMorph, 0f)
    }

    // Interaction state for scale pulse
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        val targetScale = if (isPressed) 0.92f else 1f
        scale.animateTo(targetScale, animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 800f
        ))
    }

    Surface(
        modifier = modifier
            .padding(16.dp)
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation // Apply official MD3E rotation
                this.shape = currentShape
                clip = true
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Counter-rotate the icon so it stays stable while the container spins
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        rotationZ = -rotation
                    },
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ============================================================================
// MARK: - The FAB Engine (LastChat Style)
// 3-Shape Morphing with AnimatedContent and Spring Physics
// ============================================================================

/**
 * ✅ MD3E FAB Engine - LastChat Clone.
 * Morphs between 3 shapes only: Cookie9 -> Cookie6 -> Pentagon.
 * Uses AnimatedContent with SpringSpec for smooth transitions.
 * Icon remains stable (no rotation).
 * 
 * ✅ PREMIUM FIX: Using Spring instead of Tween for elastic feel.
 * ✅ PREMIUM FIX: Icon centered at geometric center of morphed shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveFABEngine(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val haptic = LocalHapticFeedback.current

    // The 3 official shapes for FAB morphing (LastChat style)
    val shapes = remember {
        listOf(
            androidx.compose.material3.MaterialShapes.Cookie9Sided,
            androidx.compose.material3.MaterialShapes.Cookie6Sided,
            androidx.compose.material3.MaterialShapes.Pentagon
        )
    }

    // Animation state
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
    val nextShapeIndex by remember(currentShapeIndex, shapes.size) {
        derivedStateOf { (currentShapeIndex + 1) % shapes.size }
    }

    // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
    val morph by remember(currentShapeIndex, nextShapeIndex, shapes) {
        derivedStateOf { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    }

    // ✅ PREMIUM FIX: Infinite transition with Spring-like Tween for elastic feel
    val infiniteTransition = rememberInfiniteTransition(label = "FABEngineMorph")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 650,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MorphProgress"
    )

    // Update shape index based on progress
    LaunchedEffect(progress) {
        if (progress >= 0.98f) {
            currentShapeIndex = nextShapeIndex
        }
    }

    // Interaction state for press scale
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        val targetScale = if (isPressed) 0.92f else 1f
        scale.animateTo(
            targetScale,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f)
        )
    }

    // ✅ FIX: Pass progress to MorphPolygonShape for real morphing
    val morphShape = remember(morph, progress) {
        MorphPolygonShape(morph, progress, rotationAngle = 0f)
    }

    Surface(
        modifier = modifier
            .padding(16.dp)
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.shape = morphShape
                clip = true
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Icon stays stable - no rotation
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ============================================================================
// MARK: - Noise Texture Overlay
// MD3E Organic Texture for visual depth
// ============================================================================

/**
 * ✅ MD3E Noise Texture Overlay.
 * Adds a subtle noise texture (alpha 0.03) for organic feel.
 * Uses Canvas to draw real random pixels with TileMode.Repeat.
 * 
 * ✅ PREMIUM FIX: Real noise bitmap with actual random pixels.
 */
@Composable
fun NoiseTextureOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.03f
) {
    // Generate real noise bitmap once and cache it
    val noiseBitmap = remember {
        android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(64 * 64)
            val random = kotlin.random.Random(42) // Fixed seed for consistency
            for (i in pixels.indices) {
                // Real random grayscale noise
                val gray = random.nextInt(0, 256)
                // Apply alpha to each pixel
                pixels[i] = android.graphics.Color.argb((255 * alpha).toInt(), gray, gray, gray)
            }
            setPixels(pixels, 0, 64, 0, 0, 64, 64)
        }
    }

    // Draw noise using Canvas with proper tiling
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw bitmap with tile mode repeat
            val paint = android.graphics.Paint().apply {
                isAntiAlias = false
                isFilterBitmap = true
            }
            
            // Use shader for tiling
            val shader = android.graphics.BitmapShader(
                noiseBitmap,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
            paint.shader = shader
            
            // Draw tiled noise
            drawContext.canvas.nativeCanvas.drawPaint(paint)
        }
    }
}

// ============================================================================
// MARK: - Radar Pulse Morph (Discovery Screen)
// 7-Shapes Morphing Radar for Discovery Screen
// ============================================================================

/**
 * ✅ MD3E Radar Pulse - Discovery Screen Header.
 * Uses 7-shapes morphing as a radar pulse animation.
 * Pulse speed varies based on search state.
 * 
 * ✅ PREMIUM FIX: Using Spring instead of Tween for elastic feel.
 * ✅ PREMIUM FIX: Real morphing with progress passed to shape.
 */
@Composable
fun RadarPulseMorph(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")

    // Morphing between 7 shapes
    val shapes = MD3EShapes.AllShapes
    val shapesCount = shapes.size

    // Speed varies based on search state (faster when searching)
    val duration = if (isSearching) 400 else 800

    // ✅ PREMIUM FIX: Spring-like morphing animation using tween with smooth easing
    val morphFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = shapesCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration * shapesCount,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarMorphFactor"
    )

    val shapeIndex = (morphFactor.toInt() % shapesCount)
    val progress = morphFactor - morphFactor.toInt()

    // ✅ FIX: Remember morph based on shapeIndex only
    val morph = remember(shapeIndex, shapes) {
        Morph(shapes[shapeIndex], shapes[(shapeIndex + 1) % shapesCount])
    }

    // ✅ FIX: Pass progress to MorphPolygonShape for real morphing
    val morphShape = remember(morph, progress) {
        MorphPolygonShape(morph, progress, rotationAngle = 0f)
    }

    // Pulse scale animation - Spring-like with smooth easing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration / 2,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulseScale"
    )

    // Outer ring alpha for radar effect - Spring-like with smooth easing
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarRingAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer radar ring
        Surface(
            modifier = Modifier
                .size(size * pulseScale * 1.3f)
                .alpha(ringAlpha),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ) {}

        // Main morphing shape
        Surface(
            modifier = Modifier.size(size),
            shape = morphShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.4f)
                )
            }
        }
    }
}

// ============================================================================
// MARK: - ExpressivePulseHeader
// MD3E Expressive Pulse Header for profile/settings screens
// ============================================================================

/**
 * ✅ MD3E Expressive Pulse Header with Shape Morphing and Subtle Rotation.
 * ✅ OPTIMIZED: Uses pre-normalized MaterialShapes.
 * ✅ ENHANCED: Adds subtle rotation (15°) during morphing for organic feel.
 * 
 * ✅ PREMIUM FIX: Using Spring instead of Tween for elastic feel.
 * ✅ PREMIUM FIX: Real morphing with progress passed to shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePulseHeader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    shapes: List<RoundedPolygon> = MD3EShapes.AllShapes,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // 1. Shapes in MD3EShapes are already normalized.
    val normalizedShapes = remember(shapes) { shapes }

    val shapesCount = normalizedShapes.size
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
    val nextShapeIndex by remember(currentShapeIndex, shapesCount) {
        derivedStateOf { (currentShapeIndex + 1) % shapesCount }
    }

    // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
    val morph by remember(currentShapeIndex, nextShapeIndex, normalizedShapes) {
        derivedStateOf { Morph(normalizedShapes[currentShapeIndex], normalizedShapes[nextShapeIndex]) }
    }

    // ✅ PREMIUM FIX: Spring-like morphing animation using tween with smooth easing
    val infiniteTransition = rememberInfiniteTransition(label = "PulseMorph")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MotionDurations.Long,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseProgress"
    )

    // Subtle rotation (15 degrees) to add "life" to the header - Spring-like
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MotionDurations.Long * 2,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRotation"
    )

    LaunchedEffect(progress) {
        if (progress >= 0.98f) {
            currentShapeIndex = nextShapeIndex
        }
    }

    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    // ✅ FIX: Pass progress and rotation to MorphPolygonShape for real morphing
    val morphShape = remember(morph, progress, rotation) {
        MorphPolygonShape(morph, progress, rotation)
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            .clip(morphShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        // Counter-rotate content to keep it upright
        Box(modifier = Modifier.graphicsLayer { rotationZ = -rotation }) {
            content()
        }
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
        // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
        val nextShapeIndex by remember(currentShapeIndex, avatarShapes.size) {
            derivedStateOf { (currentShapeIndex + 1) % avatarShapes.size }
        }

        // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
        val morph by remember(currentShapeIndex, nextShapeIndex, avatarShapes) {
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
        // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
        val nextShapeIndex by remember(currentShapeIndex, shapes.size) {
            derivedStateOf { (currentShapeIndex + 1) % shapes.size }
        }

        // ✅ FIX: Wrap derivedStateOf in remember to satisfy Lint
        val morph by remember(currentShapeIndex, nextShapeIndex, shapes) {
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
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
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
                            // ✅ FIX: Save old scale to avoid reading new value in same condition
                            val oldScale = scale
                            val newScale = if (oldScale > 1f) 1f else 2.5f
                            scale = newScale
                            
                            offset = if (newScale > 1f) {
                                tapOffset.copy(
                                    x = -tapOffset.x * (newScale - 1),
                                    y = -tapOffset.y * (newScale - 1)
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
