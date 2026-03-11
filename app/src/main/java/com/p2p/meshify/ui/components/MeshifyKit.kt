package com.p2p.meshify.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.R
import com.p2p.meshify.ui.theme.LocalMeshifyMotion
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.MD3EShapes
import com.p2p.meshify.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.util.FileUtils
import java.io.File
import com.p2p.meshify.domain.model.Handshake
import androidx.compose.ui.graphics.asComposePath

fun RoundedPolygon.toNormalizedComposePath(): Path {
    val path = this.toPath().asComposePath()
    val bounds = path.getBounds()
    val matrix = Matrix()
    matrix.translate(-bounds.left, -bounds.top)
    path.transform(matrix)
    return path
}

fun Path.scaleToSize(targetSize: Size): Path {
    val bounds = this.getBounds()
    val matrix = Matrix()
    if (bounds.width > 0 && bounds.height > 0) {
        matrix.scale(targetSize.width / bounds.width, targetSize.height / bounds.height)
    }
    this.transform(matrix)
    return this
}

class MorphPolygonShape(private val polygon: RoundedPolygon) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = polygon.toNormalizedComposePath().scaleToSize(size)
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

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isOnline) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val shape = MD3EShapes.getShape(config.shapeStyle)
                val path = shape.toNormalizedComposePath().scaleToSize(Size(size.toPx(), size.toPx()))
                clip = true
                this.shape = GenericShape { _, _ -> addPath(path) }
                alpha = 0.15f
            }.background(MaterialTheme.colorScheme.primary))
        }
        Surface(
            modifier = Modifier.fillMaxSize(if (isOnline) 0.82f else 1f),
            shape = MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            if (avatarFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarFile).crossfade(true).build(),
                    contentDescription = null,
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

@Composable
fun ExpressiveMorphingFAB(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val config = LocalMeshifyThemeConfig.current
    val shape = remember(config.shapeStyle) { MorphPolygonShape(MD3EShapes.getShape(config.shapeStyle)) }
    FloatingActionButton(onClick = onClick, modifier = modifier.size(64.dp), shape = shape, containerColor = MaterialTheme.colorScheme.primaryContainer, elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)) { Icon(Icons.Default.Add, contentDescription = "New Chat", modifier = Modifier.size(32.dp)) }
}

/**
 * AnimatedMorphingFAB - FAB يتحول بشكل ديناميكي بين 4 أشكال
 *
 * يستخدم Morph API للتحول السلس بين:
 * - Circle (دائرة)
 * - Sunny (نجمة 8 أشعة)
 * - Burst (انفجار)
 * - Clover (نونة 4 أوراق)
 *
 * الحركة:
 * - duration: 2000ms لكل دورة كاملة
 * - Infinite repeat مع Reverse
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedMorphingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    // الأشكال الأربعة المختارة
    val shapes = remember {
        listOf(
            MD3EShapes.Circle,
            MD3EShapes.Sunny,
            MD3EShapes.Burst,
            MD3EShapes.Clover
        )
    }

    // InfiniteTransition للـ morphing المستمر
    val infiniteTransition = rememberInfiniteTransition(label = "fabMorph")

    // التقدم من 0 إلى 1 (دورة كاملة عبر جميع الأشكال)
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabMorphProgress"
    )

    // إنشاء android.graphics.Path قابل لإعادة الاستخدام
    val androidPath = remember { android.graphics.Path() }

    // تحديث الـ Path في كل recomposition بناءً على التقدم
    // يجب حساب الشكل الديناميكي خارج remember لضمان التحديث
    val fromIndex = (morphProgress * (shapes.size - 1)).toInt().coerceIn(0, shapes.size - 2)
    val toIndex = (fromIndex + 1).coerceAtMost(shapes.size - 1)
    val segmentProgress = if (shapes.size > 1) {
        ((morphProgress * (shapes.size - 1)) - fromIndex).coerceIn(0f, 1f)
    } else {
        0f
    }

    val fromShape = shapes[fromIndex]
    val toShape = shapes[toIndex]

    // إنشاء Morph جديد لكل زوج من الأشكال
    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }

    // تحديث الـ Path بالشكل الحالي باستخدام toPath مع re-use
    morph.toPath(segmentProgress, androidPath)
    
    // تحويل android.graphics.Path إلى Compose Path
    val currentShapePath = androidPath.asComposePath()

    // إنشاء Shape ديناميكي
    val shape = remember(currentShapePath) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                // تطبيق transform لتطابق الـ Path مع الـ Size المطلوب
                val bounds = currentShapePath.getBounds()

                // إنشاء Path محوَّل جديد في كل مرة (لا re-use)
                val transformedPath = Path()

                if (bounds.width > 0f && bounds.height > 0f &&
                    !bounds.width.isNaN() && !bounds.height.isNaN() &&
                    bounds.width.isFinite() && bounds.height.isFinite()) {

                    // حساب الـ scale
                    val scaleX = size.width / bounds.width
                    val scaleY = size.height / bounds.height

                    // استخدام Matrix للـ transform بترتيب صحيح
                    val matrix = Matrix()

                    // الخطوة 1: نقل الـ Path إلى المركز
                    matrix.translate(-bounds.left, -bounds.top)

                    // الخطوة 2: تطبيق الـ scale
                    matrix.scale(scaleX, scaleY)

                    // الخطوة 3: إضافة الـ Path الأصلي
                    transformedPath.addPath(currentShapePath)
                    
                    // الخطوة 4: تطبيق الـ transform على الـ Path
                    transformedPath.transform(matrix)
                    
                    // Debug log
                    android.util.Log.d("FAB_DEBUG", "Bounds: $bounds, Size: $size, Scale: $scaleX x $scaleY")
                } else {
                    // Fallback: إضافة الـ Path بدون transform
                    transformedPath.addPath(currentShapePath)
                    
                    android.util.Log.d("FAB_DEBUG", "Fallback! Bounds: $bounds, Size: $size")
                }

                return androidx.compose.ui.graphics.Outline.Generic(transformedPath)
            }
        }
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(shape),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
            hoveredElevation = 10.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "New Chat",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
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
