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
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.blur
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MotionDurations
import android.graphics.Matrix

// ============================================================================
// MARK: - MorphPolygonShape (Optimized)
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

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (cachedOutline != null && cachedProgress == progress && cachedRotation == rotationAngle) {
            return cachedOutline!!
        }

        return try {
            androidPath.reset()
            morph.toPath(progress, androidPath)
            val bounds = android.graphics.RectF()
            androidPath.computeBounds(bounds, false)
            
            val scale = minOf(size.width / bounds.width(), size.height / bounds.height()) * 0.95f
            matrix.reset()
            matrix.setTranslate(-bounds.centerX(), -bounds.centerY())
            if (rotationAngle != 0f) matrix.postRotate(rotationAngle)
            matrix.postScale(scale, scale)
            matrix.postTranslate(size.width / 2f, size.height / 2f)
            androidPath.transform(matrix)
            
            val outline = Outline.Generic(androidPath.asComposePath())
            cachedOutline = outline
            cachedProgress = progress
            cachedRotation = rotationAngle
            outline
        } catch (e: Exception) {
            CircleShape.createOutline(size, layoutDirection, density)
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

    // 2. Edge Jitter (High frequency micro-movements)
    val jitter by infiniteTransition.animateFloat(
        initialValue = -0.02f,
        targetValue = 0.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Jitter"
    )

    // 3. Breathing Pulse (Scale)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // 4. Rotation (Slow & Momentum-based)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val morphShape = remember(morph, progressAnim.value, jitter) {
        MorphPolygonShape(morph, (progressAnim.value + jitter).coerceIn(0f, 1f), 0f)
    }

    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                rotationZ = rotation
            }
            // 5. The Halo Effect (Glow)
            .drawBehind {
                val glowSize = size.toPx() * 1.2f
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

// ... rest of the file (ExpressiveMorphingFAB, etc.) remains same or updated similarly ...
