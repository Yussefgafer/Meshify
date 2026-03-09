package com.p2p.meshify.ui.components

import android.graphics.Matrix
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.transform
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.SignalStrength
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.MD3EShapes
import java.io.File
import kotlin.random.Random

/**
 * MeshifyKit - The Standard Design Language for Meshify.
 * Inspired by ViVi Music & MD3 Expressive.
 */

/**
 * Noise Texture Overlay to give a tactile, organic feel to the background.
 */
@Composable
fun NoiseTextureOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.05f
) {
    val noisePattern = remember {
        // Generate a static noise pattern once
        // In a real app, use a shader or a tiled noise image for better performance
        // This is a simple procedural approximation
        true
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val pointCount = (width * height * 0.005f).toInt().coerceAtMost(5000)
        
        // Draw random points for noise
        for (i in 0 until pointCount) {
            val x = Random.nextFloat() * width
            val y = Random.nextFloat() * height
            drawCircle(
                color = Color.Black.copy(alpha = alpha),
                radius = 1f,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 1f,
                center = Offset(Random.nextFloat() * width, Random.nextFloat() * height)
            )
        }
    }
}

/**
 * Converts a RoundedPolygon to a Compose Path for drawing and clipping.
 */
fun RoundedPolygon.toComposePathWithSize(size: Size): Path {
    val androidPath = this.toPath() // Extension from androidx.graphics.shapes
    val matrix = android.graphics.Matrix()
    matrix.setScale(size.width / 2f, size.height / 2f)
    matrix.postTranslate(size.width / 2f, size.height / 2f)
    androidPath.transform(matrix)
    return androidPath.asComposePath()
}

class MorphPolygonShape(private val polygon: RoundedPolygon) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val path = polygon.toComposePathWithSize(size)
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
    
    val avatarFile = remember(avatarHash) {
        avatarHash?.let { hash ->
            FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) }
        }
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (isOnline) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val shape = MD3EShapes.getShape(config.shapeStyle)
                        val path = shape.toComposePathWithSize(Size(size.toPx(), size.toPx()))
                        clip = true
                        this.shape = GenericShape { _, _ -> addPath(path) }
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(0.2f))
                    .padding(4.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(if (isOnline) 0.85f else 1f),
            shape = MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            if (avatarFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        if (isOnline) {
            Box(
                Modifier
                    .size(size / 4)
                    .align(Alignment.BottomEnd)
                    .background(Color.Green, androidx.compose.foundation.shape.CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
fun SignalMorphAvatar(
    initials: String,
    signalStrength: SignalStrength,
    size: Dp = 40.dp
) {
    val color = when(signalStrength) {
        SignalStrength.STRONG -> Color.Green
        SignalStrength.MEDIUM -> Color.Yellow
        SignalStrength.WEAK -> Color.Red
        SignalStrength.OFFLINE -> Color.Gray
    }
    
    Box(modifier = Modifier.size(size)) {
        MorphingAvatar(initials = initials, size = size, isOnline = signalStrength != SignalStrength.OFFLINE)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
        )
    }
}

@Composable
fun MeshifyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = cardShape,
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun MeshifyListItem(
    headline: String,
    supporting: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { leadingContent() }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                if (supporting != null) {
                    Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun ExpressivePulseHeader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalMeshifyThemeConfig.current
    val motion = LocalMeshifyMotion.current
    var isPulsing by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(targetValue = if (isPulsing) 1f else 0f, animationSpec = motion.springSpec, label = "MorphProgress")
    val startShape = remember(config.shapeStyle) { MD3EShapes.getShape(config.shapeStyle) }
    val endShape = remember { MD3EShapes.Circle }
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    
    LaunchedEffect(Unit) {
        while (true) {
            isPulsing = !isPulsing
            kotlinx.coroutines.delay(2500)
        }
    }

    Box(
        modifier = modifier.size(size).graphicsLayer {
            // Fix: Use qualified path
            val morphPath = morph.toPath(progress)
            val matrix = android.graphics.Matrix().apply {
                setScale(size.toPx() / 2f, size.toPx() / 2f)
                postTranslate(size.toPx() / 2f, size.toPx() / 2f)
            }
            morphPath.transform(matrix)
            val composePath = morphPath.asComposePath()
            
            clip = true
            shape = GenericShape { _, _ -> addPath(composePath) }
        }.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun RadarPulseMorph(
    isSearching: Boolean,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "Scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "Alpha"
    )

    Box(
        modifier = modifier.size(size).graphicsLayer {
            if (isSearching) { scaleX = scale; scaleY = scale; this.alpha = alpha }
        }.background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
fun ExpressiveMorphingFAB(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val config = LocalMeshifyThemeConfig.current
    val shape = remember(config.shapeStyle) { MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)) }
    FloatingActionButton(onClick = onClick, modifier = modifier.size(56.dp), shape = shape, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Icon(Icons.Default.Add, contentDescription = "New Chat")
    }
}

@Composable
fun MeshifySectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun MeshifyPill(text: String, containerColor: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = containerColor, shape = androidx.compose.foundation.shape.CircleShape) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
    }
}
