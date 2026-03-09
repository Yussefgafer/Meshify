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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
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
import com.p2p.meshify.ui.hooks.LocalPremiumHaptics
import android.graphics.Matrix
import kotlinx.coroutines.delay

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

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (cachedOutline != null && cachedProgress == progress && cachedRotation == rotationAngle) return cachedOutline!!
        return try {
            androidPath.reset()
            morph.toPath(progress, androidPath)
            val bounds = android.graphics.RectF()
            @Suppress("DEPRECATION")
            androidPath.computeBounds(bounds, false)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val scale = minOf(size.width / bounds.width(), size.height / bounds.height()) * 0.9f
                matrix.reset()
                matrix.setTranslate(-bounds.centerX(), -bounds.centerY())
                if (rotationAngle != 0f) matrix.postRotate(rotationAngle)
                matrix.postScale(scale, scale)
                matrix.postTranslate(size.width / 2f, size.height / 2f)
                androidPath.transform(matrix)
            }
            val outline = Outline.Generic(androidPath.asComposePath())
            cachedOutline = outline; cachedProgress = progress; cachedRotation = rotationAngle
            outline
        } catch (e: Exception) { CircleShape.createOutline(size, layoutDirection, density) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveMorphingFAB(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 64.dp, shapes: List<RoundedPolygon> = MD3EShapes.AllShapes) {
    val haptic = LocalHapticFeedback.current
    val shapesCount = shapes.size
    var currentShapeIndex by remember { mutableIntStateOf(0) }
    val nextShapeIndex = (currentShapeIndex + 1) % shapesCount
    val morph = remember(currentShapeIndex, nextShapeIndex) { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    val infiniteTransition = rememberInfiniteTransition(label = "FAB")
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(currentShapeIndex) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow))
        currentShapeIndex = nextShapeIndex
    }
    val jitter by infiniteTransition.animateFloat(-0.005f, 0.005f, infiniteRepeatable(tween(200), RepeatMode.Reverse), "Jitter")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(15000, easing = LinearEasing)), "Rotation")
    val morphShape = remember(morph, progressAnim.value, jitter) { MorphPolygonShape(morph, (progressAnim.value + jitter).coerceIn(0f, 1f)) }
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { scale.animateTo(if (isPressed) 0.97f else 1f, spring(0.4f, 800f)) }
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    Surface(
        modifier = modifier.padding(16.dp).size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = rotation }
            .drawBehind {
                val glowSize = size.toPx() * 1.15f
                drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), center, glowSize / 2), glowSize / 2)
            }
            .clip(morphShape).clickable(interactionSource, null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        color = MaterialTheme.colorScheme.primary, tonalElevation = 4.dp, shadowElevation = 6.dp
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Add", Modifier.size(28.dp).graphicsLayer { rotationZ = -rotation }, tint = MaterialTheme.colorScheme.onPrimary) } }
}

@Composable
fun NoiseTextureOverlay(modifier: Modifier = Modifier, alpha: Float = 0.02f) {
    val noiseBitmap = remember {
        android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(64 * 64); val random = kotlin.random.Random(42)
            for (i in pixels.indices) { val gray = random.nextInt(256); pixels[i] = android.graphics.Color.argb((255 * alpha).toInt(), gray, gray, gray) }
            setPixels(pixels, 0, 64, 0, 0, 64, 64)
        }
    }
    Box(modifier.fillMaxSize()) { androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawContext.canvas.nativeCanvas.drawPaint(android.graphics.Paint().apply { shader = android.graphics.BitmapShader(noiseBitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT) }) } }
}

@Composable
fun RadarPulseMorph(isSearching: Boolean, modifier: Modifier = Modifier, size: Dp = 80.dp) {
    val infiniteTransition = rememberInfiniteTransition("Radar")
    val shapes = MD3EShapes.AllShapes; val shapesCount = shapes.size
    val morphFactor by infiniteTransition.animateFloat(0f, shapesCount.toFloat(), infiniteRepeatable(tween(if (isSearching) 800 * shapesCount else 1500 * shapesCount, easing = LinearEasing)), "Morph")
    val shapeIndex = (morphFactor.toInt() % shapesCount); val progress = morphFactor - morphFactor.toInt()
    val jitter by infiniteTransition.animateFloat(-0.005f, 0.005f, infiniteRepeatable(tween(150), RepeatMode.Reverse), "Jitter")
    val morph = remember(shapeIndex) { Morph(shapes[shapeIndex], shapes[(shapeIndex + 1) % shapesCount]) }
    val morphShape = remember(morph, progress, jitter) { MorphPolygonShape(morph, (progress + jitter).coerceIn(0f, 1f)) }
    val pulseScale by infiniteTransition.animateFloat(1f, 1.08f, infiniteRepeatable(tween(if (isSearching) 600 else 1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "Pulse")
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * pulseScale * 1.2f).drawBehind { drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), center, size.toPx() * 0.6f), size.toPx() * 0.6f) })
        Surface(Modifier.size(size), shape = morphShape, color = MaterialTheme.colorScheme.primary) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.4f)) } }
    }
}

@Composable
fun ExpressivePulseHeader(modifier: Modifier = Modifier, size: Dp = 120.dp, shapes: List<RoundedPolygon> = MD3EShapes.AllShapes, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current; val shapesCount = shapes.size
    var currentShapeIndex by remember { mutableIntStateOf(0) }; val nextShapeIndex = (currentShapeIndex + 1) % shapesCount
    val morph = remember(currentShapeIndex, nextShapeIndex) { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
    val infiniteTransition = rememberInfiniteTransition("Pulse")
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(currentShapeIndex) { progressAnim.snapTo(0f); progressAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)); currentShapeIndex = nextShapeIndex }
    val jitter by infiniteTransition.animateFloat(-0.005f, 0.005f, infiniteRepeatable(tween(250), RepeatMode.Reverse), "Jitter")
    val pulseScale by infiniteTransition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "Scale")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(25000, easing = LinearEasing)), "Rotation")
    val morphShape = remember(morph, progressAnim.value, jitter) { MorphPolygonShape(morph, (progressAnim.value + jitter).coerceIn(0f, 1f)) }
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    Box(modifier = modifier.size(size).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; rotationZ = rotation }
        .drawBehind { val glowSize = size.toPx() * 1.1f; drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), center, glowSize / 2), glowSize / 2) }
        .clip(morphShape).background(MaterialTheme.colorScheme.primaryContainer.copy(0.6f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { rotationZ = -rotation }) { content() }
    }
}

@Composable
fun MorphingAvatar(initials: String, isOnline: Boolean, modifier: Modifier = Modifier, size: Dp = 52.dp, containerColor: Color = MaterialTheme.colorScheme.primaryContainer, onContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    val avatarShapes = remember { listOf(MD3EShapes.Blob, MD3EShapes.Circle, MD3EShapes.Clover) }
    if (isOnline) {
        var currentShapeIndex by remember { mutableIntStateOf(0) }; val nextShapeIndex = (currentShapeIndex + 1) % avatarShapes.size
        val morph = remember(currentShapeIndex) { Morph(avatarShapes[currentShapeIndex], avatarShapes[nextShapeIndex]) }
        val infiniteTransition = rememberInfiniteTransition("Avatar")
        val progressAnim = remember { Animatable(0f) }
        LaunchedEffect(currentShapeIndex) { progressAnim.snapTo(0f); progressAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)); currentShapeIndex = nextShapeIndex }
        val jitter by infiniteTransition.animateFloat(-0.005f, 0.005f, infiniteRepeatable(tween(300), RepeatMode.Reverse), "Jitter")
        val morphShape = remember(morph, progressAnim.value, jitter) { MorphPolygonShape(morph, (progressAnim.value + jitter).coerceIn(0f, 1f)) }
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(Modifier.size(size), shape = morphShape, color = containerColor.copy(0.8f)) { Box(contentAlignment = Alignment.Center) { Text(initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor, fontWeight = FontWeight.Bold) } }
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.27f).clip(CircleShape).background(Color(0xFF4CAF50)))
        }
    } else { Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) { Surface(Modifier.size(size), shape = CircleShape, color = containerColor.copy(0.6f)) { Box(contentAlignment = Alignment.Center) { Text(initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor.copy(0.7f), fontWeight = FontWeight.Bold) } } } }
}

@Composable
fun SignalMorphAvatar(initials: String, signalStrength: com.p2p.meshify.domain.model.SignalStrength, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val shapes = when (signalStrength) {
        com.p2p.meshify.domain.model.SignalStrength.STRONG -> listOf(MD3EShapes.Sunny, MD3EShapes.Breezy)
        com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> listOf(MD3EShapes.Breezy, MD3EShapes.Circle)
        com.p2p.meshify.domain.model.SignalStrength.WEAK -> listOf(MD3EShapes.Circle, MD3EShapes.Blob)
        else -> listOf(MD3EShapes.Circle, MD3EShapes.Circle)
    }
    if (signalStrength == com.p2p.meshify.domain.model.SignalStrength.OFFLINE) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) { Surface(Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) { Box(contentAlignment = Alignment.Center) { Text(initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), fontWeight = FontWeight.Bold) } } }
    } else {
        var currentShapeIndex by remember { mutableIntStateOf(0) }; val nextShapeIndex = (currentShapeIndex + 1) % shapes.size
        val morph = remember(currentShapeIndex, shapes) { Morph(shapes[currentShapeIndex], shapes[nextShapeIndex]) }
        val progressAnim = remember { Animatable(0f) }
        LaunchedEffect(currentShapeIndex, signalStrength) { progressAnim.snapTo(0f); progressAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)); currentShapeIndex = nextShapeIndex }
        val morphShape = remember(morph, progressAnim.value) { MorphPolygonShape(morph, progressAnim.value) }
        val containerColor = when (signalStrength) { com.p2p.meshify.domain.model.SignalStrength.STRONG -> MaterialTheme.colorScheme.primaryContainer; com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
        val onContainerColor = when (signalStrength) { com.p2p.meshify.domain.model.SignalStrength.STRONG -> MaterialTheme.colorScheme.onPrimaryContainer; com.p2p.meshify.domain.model.SignalStrength.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f) }
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) { Surface(Modifier.size(size), shape = morphShape, color = containerColor) { Box(contentAlignment = Alignment.Center) { Text(initials.uppercase(), style = MaterialTheme.typography.titleLarge, color = onContainerColor, fontWeight = FontWeight.Bold) } } }
    }
}

@Composable
fun FullImageViewer(imagePath: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }; var offset by remember { mutableStateOf(Offset.Zero) }; var rotation by remember { mutableStateOf(0f) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onDoubleTap = { t -> val os = scale; scale = if (os > 1f) 1f else 2.5f; offset = if (scale > 1f) t.copy(x = -t.x * (scale - 1), y = -t.y * (scale - 1)) else Offset.Zero }, onTap = { onDismiss() }) }.pointerInput(Unit) { detectTransformGestures { _, p, z, r -> scale = (scale * z).coerceIn(1f, 5f); rotation += r; offset = offset.copy(x = offset.x + p.x, y = offset.y + p.y); if (scale <= 1f) { scale = 1f; offset = Offset.Zero; rotation = 0f } } }) {
            AsyncImage(imagePath, "BG", Modifier.fillMaxSize().blur(20.dp).alpha(0.3f), contentScale = ContentScale.Crop)
            AsyncImage(imagePath, "Full", Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
            Surface(color = Color.Black.copy(0.5f), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(40.dp).clickable { onDismiss() }) { Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var countdown by remember { mutableIntStateOf(5) }; val isEnabled by remember { derivedStateOf { countdown <= 0 } }
    LaunchedEffect(Unit) { while (countdown > 0) { delay(1000); countdown-- } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { Text(text); if (!isEnabled) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator({ (5 - countdown) / 5f }, Modifier.fillMaxWidth(), MaterialTheme.colorScheme.error) } } }, confirmButton = { Button(onConfirm, enabled = isEnabled, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error, disabledContainerColor = MaterialTheme.colorScheme.error.copy(0.3f))) { Text(if (isEnabled) "Delete" else "Wait ($countdown)") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun ExpressiveCard(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, shape: RoundedCornerShape? = null, springSpec: SpringSpec<Float>? = null, densityScale: Float? = null, content: @Composable ColumnScope.() -> Unit) {
    val haptic = LocalPremiumHaptics.current; val motion = LocalMeshifyMotion.current; val themeConfig = LocalMeshifyThemeConfig.current
    val effectiveSpring = springSpec ?: motion.springSpec; val effectiveDensity = densityScale ?: themeConfig.visualDensity
    val effectiveShape = shape ?: RoundedCornerShape((28.dp * effectiveDensity).coerceIn(20.dp, 36.dp))
    val scale = remember { Animatable(1f) }; val interactionSource = remember { MutableInteractionSource() }; val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { scale.animateTo(if (isPressed) 0.97f else 1f, effectiveSpring) }
    Card(modifier.then(if (onClick != null) Modifier.clickable(interactionSource, null) { haptic.perform(com.p2p.meshify.ui.hooks.HapticPattern.Pop); onClick() } else Modifier).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, effectiveShape, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow), content = content)
}
