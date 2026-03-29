package com.p2p.meshify.core.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.ui.R
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.core.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.core.ui.theme.MD3EShapes
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.Handshake
import java.io.File

/**
 * Robustly transforms a RoundedPolygon Path to fit and center within a given Size.
 * Uses the mathematically correct order: Translate to target center -> Scale -> Translate from source center.
 */
fun android.graphics.Path.toCenteredComposePath(size: Size, scaleFactor: Float = 0.9f): Path {
    val path = this.asComposePath()
    val bounds = path.getBounds()
    val matrix = Matrix()

    // Calculate scale to fit while maintaining aspect ratio
    val scale = minOf(size.width / bounds.width, size.height / bounds.height) * scaleFactor

    // Target center
    val targetCenterX = size.width / 2f
    val targetCenterY = size.height / 2f

    // Source center (of the polygon's own bounds)
    val sourceCenterX = bounds.left + bounds.width / 2f
    val sourceCenterY = bounds.top + bounds.height / 2f

    // Transformation sequence (Applied in reverse order in post-concat Matrix):
    // 1. Move source center to origin (0,0)
    // 2. Scale
    // 3. Move origin to target center
    matrix.translate(targetCenterX, targetCenterY)
    matrix.scale(scale, scale)
    matrix.translate(-sourceCenterX, -sourceCenterY)

    path.transform(matrix)
    return path
}

/**
 * Optimized Shape for MD3E Morphing.
 */
class MorphingPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val scaleFactor: Float = 0.85f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        // Generate the path at the current morph progress and center it
        val path = morph.toPath(progress).toCenteredComposePath(size, scaleFactor)
        return Outline.Generic(path)
    }
}

@Composable
fun MorphingAvatar(
    initials: String,
    avatarHash: String? = null,
    isOnline: Boolean = false,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalMeshifyThemeConfig.current
    val cleanInitials = initials.filter { it.isLetterOrDigit() }.take(1).uppercase()
    val avatarFile = remember(avatarHash) {
        avatarHash?.let { hash ->
            FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) }
        }
    }

    val polygon = remember(config.shapeStyle) { MD3EShapes.getShape(config.shapeStyle) }
    val avatarShape = remember(polygon) {
        GenericShape { targetSize, _ ->
            addPath(polygon.toPath().toCenteredComposePath(targetSize, scaleFactor = 0.95f))
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isOnline) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                clip = true
                shape = avatarShape
                alpha = 0.15f
            }.background(MaterialTheme.colorScheme.primary))
        }
        Surface(
            modifier = Modifier.fillMaxSize(if (isOnline) 0.82f else 1f),
            shape = avatarShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            if (avatarFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarFile).crossfade(true).build(),
                    contentDescription = stringResource(R.string.avatar_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = cleanInitials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (isOnline) {
            Box(
                Modifier
                    .size(size / 4.5f)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFF4CAF50), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun MeshifyCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = MeshifyDesignSystem.Shapes.CardLarge, color = containerColor, tonalElevation = MeshifyDesignSystem.Elevation.Level2) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md), content = content)
    }
}

@Composable
fun MeshifyListItem(headline: String, supporting: String? = null, leadingContent: @Composable (() -> Unit)? = null, trailingContent: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "item_scale"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        color = Color.Transparent,
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Row(modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md, vertical = MeshifyDesignSystem.Spacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            if (leadingContent != null) { Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { leadingContent() }; Spacer(Modifier.width(MeshifyDesignSystem.Spacing.Md)) }
            Column(Modifier.weight(1f)) {
                Text(text = headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                if (supporting != null) { Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            if (trailingContent != null) { Spacer(Modifier.width(MeshifyDesignSystem.Spacing.Xs)); trailingContent() }
        }
    }
}

@Composable
fun RadarPulseMorph(isSearching: Boolean, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    if (!isSearching) {
        Surface(modifier = modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(0.1f)) { Icon(Icons.Default.Add, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary) }
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val pulses = listOf(infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), "P1"), infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, 600, easing = LinearEasing), RepeatMode.Restart), "P2"))
    Box(modifier = modifier.size(size * 2.5f), contentAlignment = Alignment.Center) {
        pulses.forEach { p -> Box(Modifier.size(size * 2.5f * p.value).graphicsLayer { alpha = 1f - p.value }.border(2.dp, MaterialTheme.colorScheme.primary.copy(0.5f), CircleShape)) }
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primary, tonalElevation = 6.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
    }
}

/**
 * Animated Morphing FAB (Material 3 Expressive).
 * Fixed matrix centering and naming to resolve visual glitches and build failures.
 */
@Composable
fun AnimatedMorphingFAB(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val config = LocalMeshifyThemeConfig.current
    val haptics = LocalPremiumHaptics.current

    val targetPolygon = remember(config.shapeStyle) { MD3EShapes.getShape(config.shapeStyle) }
    var previousPolygon by remember { mutableStateOf(targetPolygon) }
    var currentPolygon by remember { mutableStateOf(targetPolygon) }

    LaunchedEffect(config.shapeStyle) {
        previousPolygon = currentPolygon
        currentPolygon = targetPolygon
    }

    val morphProgress = remember { Animatable(0f) }
    LaunchedEffect(currentPolygon) {
        morphProgress.snapTo(0f)
        morphProgress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }

    val morph = remember(previousPolygon, currentPolygon) {
        Morph(previousPolygon, currentPolygon)
    }

    // Using a custom Shape class for better performance and clean matrix logic
    val animatedShape = MorphingPolygonShape(morph, morphProgress.value, scaleFactor = 0.82f)

    FloatingActionButton(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        modifier = modifier
            .size(64.dp)
            .navigationBarsPadding(),
        shape = animatedShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "New Chat",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun MeshifySectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp), letterSpacing = 1.sp)
}

@Composable
fun MeshifyPill(text: String, containerColor: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = containerColor, shape = CircleShape) { Text(text = text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold) }
}

@Composable
fun PremiumNoiseTexture(modifier: Modifier = Modifier, alpha: Float = 0.03f) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}

@Composable
fun ExpressivePulseHeader(modifier: Modifier = Modifier, size: Dp = 120.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}
