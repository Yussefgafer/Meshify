package com.p2p.meshify.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

/**
 * A proper Squircle (superellipse) shape implementation.
 * n = 3.0 or 4.0 provides the "smooth" rounded corner look preferred by modern UI.
 */
fun SquircleShape(n: Float = 3.0f) = GenericShape { size, _ ->
    val radius = size.width / 2f
    val path = Path()
    
    // x = r * cos(t)^(2/n)
    // y = r * sin(t)^(2/n)
    // We iterate through 360 degrees
    for (i in 0..360) {
        val angle = Math.toRadians(i.toDouble())
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        
        val x = radius + radius * abs(cos).pow(2.0 / n).let { if (cos < 0) -it else it }.toFloat()
        val y = radius + radius * abs(sin).pow(2.0 / n).let { if (sin < 0) -it else it }.toFloat()
        
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    this.addPath(path)
}

/**
 * An immersive, animated background for onboarding.
 * Features morphing blobs and a noise texture for a high-end feel.
 */
@Composable
fun OnboardingBackground(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "bg_blobs")
    
    // Animation for blob 1
    val blob1Offset by infiniteTransition.animateValue(
        initialValue = Offset(0.2f, 0.2f),
        targetValue = Offset(0.3f, 0.4f),
        typeConverter = Offset.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1"
    )
    
    // Animation for blob 2
    val blob2Offset by infiniteTransition.animateValue(
        initialValue = Offset(0.8f, 0.7f),
        targetValue = Offset(0.7f, 0.5f),
        typeConverter = Offset.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2"
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Canvas(modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.6f)) {
            // Blob 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor, Color.Transparent),
                    center = Offset(size.width * blob1Offset.x, size.height * blob1Offset.y),
                    radius = size.minDimension * 0.8f
                ),
                center = Offset(size.width * blob1Offset.x, size.height * blob1Offset.y),
                radius = size.minDimension * 0.8f
            )
            
            // Blob 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryColor, Color.Transparent),
                    center = Offset(size.width * blob2Offset.x, size.height * blob2Offset.y),
                    radius = size.minDimension * 0.7f
                ),
                center = Offset(size.width * blob2Offset.x, size.height * blob2Offset.y),
                radius = size.minDimension * 0.7f
            )
            
            // Subtle accent blob that moves based on page
            val accentX = when (currentPage) {
                0 -> 0.1f
                1 -> 0.5f
                else -> 0.9f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryColor, Color.Transparent),
                    center = Offset(size.width * accentX, size.height * 0.9f),
                    radius = size.minDimension * 0.5f
                ),
                center = Offset(size.width * accentX, size.height * 0.9f),
                radius = size.minDimension * 0.5f
            )
        }
    }
}

/**
 * Animated page indicator using Squircle shapes.
 */
@Composable
fun SquirclePageIndicator(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isActive = index == currentPage
            
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "width"
            )
            
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.3f,
                animationSpec = tween(300),
                label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .width(width)
                    .height(10.dp)
                    .graphicsLayer {
                        shape = SquircleShape(if (isActive) 3.5f else 3.0f)
                        clip = true
                    }
                    .background(
                        color = if (isActive) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    .clickable { onPageSelected(index) }
            )
        }
    }
}
