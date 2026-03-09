package com.p2p.meshify.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import com.p2p.meshify.ui.theme.MeshifyDesignSystem
import java.io.File

/**
 * ✅ FIX (Priority 1): Normalized Morphing Engine.
 */
fun RoundedPolygon.toNormalizedComposePath(): Path {
    val path = this.toPath() // android.graphics.Path
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val matrix = Matrix().apply {
        setScale(1f / bounds.width(), 1f / bounds.height())
        postTranslate(-bounds.left, -bounds.top)
    }
    path.transform(matrix)
    return path.asComposePath()
}

fun Path.scaleToSize(targetSize: Size): Path {
    val matrix = Matrix().apply {
        postScale(targetSize.width, targetSize.height)
    }
    val androidPath = this.asAndroidPath()
    val transformed = android.graphics.Path()
    androidPath.transform(matrix, transformed)
    return transformed.asComposePath()
}

class MorphPolygonShape(private val polygon: RoundedPolygon) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = polygon.toNormalizedComposePath().scaleToSize(size)
        return Outline.Generic(path)
    }
}

@Composable
fun PremiumNoiseTexture(modifier: Modifier = Modifier, alpha: Float = 0.03f) {
    val color = MaterialTheme.colorScheme.onSurface
    val noiseBitmap = remember(color) {
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint().apply {
                this.alpha = (alpha * 255).toInt()
                isAntiAlias = true
                colorFilter = android.graphics.PorterDuffColorFilter(color.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            repeat(2000) { canvas.drawCircle((Math.random() * 256).toFloat(), (Math.random() * 256).toFloat(), 1f, paint) }
        }
    }
    Image(bitmap = noiseBitmap.asImageBitmap(), contentDescription = null, modifier = modifier.fillMaxSize(), alpha = alpha, contentScale = ContentScale.FillBounds)
}

@Composable
fun MorphingAvatar(initials: String, avatarHash: String? = null, isOnline: Boolean = false, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = LocalMeshifyThemeConfig.current
    val cleanInitials = initials.filter { it.isLetterOrDigit() }.take(1).uppercase()
    val avatarFile = remember(avatarHash) { avatarHash?.let { hash -> FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) } } }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (isOnline) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val shape = MD3EShapes.getShape(config.shapeStyle)
                val path = shape.toNormalizedComposePath().scaleToSize(Size(size.toPx(), size.toPx()))
                clip = true
                this.shape = GenericShape { _, _ -> addPath(path) }
                alpha = 0.15f
            }.background(MaterialTheme.colorScheme.primary))
        }
        Surface(modifier = Modifier.fillMaxSize(if (isOnline) 0.82f else 1f), shape = MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
            if (avatarFile != null) {
                AsyncImage(model = ImageRequest.Builder(context).data(avatarFile).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) { Text(text = cleanInitials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
            }
        }
        if (isOnline) { Box(Modifier.size(size / 4.5f).align(Alignment.BottomEnd).background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) }
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
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.Transparent) {
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
fun ExpressivePulseHeader(modifier: Modifier = Modifier, size: Dp = 120.dp, content: @Composable BoxScope.() -> Unit) {
    val config = LocalMeshifyThemeConfig.current
    val motion = LocalMeshifyMotion.current
    var isPulsing by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(targetValue = if (isPulsing) 1f else 0f, animationSpec = motion.springSpec, label = "MorphProgress")
    val startShape = remember(config.shapeStyle) { MD3EShapes.getShape(config.shapeStyle) }
    val endShape = remember { MD3EShapes.Circle }
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    LaunchedEffect(Unit) { while (true) { isPulsing = !isPulsing; kotlinx.coroutines.delay(3000) } }
    Box(modifier = modifier.size(size + 24.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size + 20.dp).graphicsLayer {
            val path = morph.toPath(progress).asComposePath().scaleToSize(Size(size.toPx() + 20.dp.toPx(), size.toPx() + 20.dp.toPx()))
            clip = true
            shape = GenericShape { _, _ -> addPath(path) }
            alpha = 0.25f
        }.background(MaterialTheme.colorScheme.primary))
        Surface(modifier = Modifier.size(size), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), tonalElevation = 4.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
    }
}

@Composable
fun RadarPulseMorph(isSearching: Boolean, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    if (!isSearching) {
        Surface(modifier = modifier.size(size), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary.copy(0.1f)) { Icon(Icons.Default.Add, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary) }
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val pulses = listOf(infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), "P1"), infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, 600, easing = LinearEasing), RepeatMode.Restart), "P2"))
    Box(modifier = modifier.size(size * 2.5f), contentAlignment = Alignment.Center) {
        pulses.forEach { p -> Box(Modifier.size(size * 2.5f * p.value).graphicsLayer { alpha = 1f - p.value }.border(2.dp, MaterialTheme.colorScheme.primary.copy(0.5f), androidx.compose.foundation.shape.CircleShape)) }
        Surface(modifier = Modifier.size(size), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary, tonalElevation = 6.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
    }
}

@Composable
fun ExpressiveMorphingFAB(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val config = LocalMeshifyThemeConfig.current
    val shape = remember(config.shapeStyle) { MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)) }
    FloatingActionButton(onClick = onClick, modifier = modifier.size(64.dp), shape = shape, containerColor = MaterialTheme.colorScheme.primaryContainer, elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)) { Icon(Icons.Default.Add, contentDescription = "New Chat", modifier = Modifier.size(32.dp)) }
}

@Composable
fun MeshifySectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp), letterSpacing = 1.sp)
}

@Composable
fun MeshifyPill(text: String, containerColor: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = containerColor, shape = androidx.compose.foundation.shape.CircleShape) { Text(text = text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold) }
}

fun Modifier.meshifyGlass(alpha: Float = 0.1f, blur: Float = 30f, shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)): Modifier = this.graphicsLayer { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) { renderEffect = android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } }.background(Color.White.copy(alpha = alpha), shape).border(1.dp, Color.White.copy(alpha = 0.2f), shape)
