package com.p2p.meshify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

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
                            // Double tap to zoom in/out
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
                        // Pinch to zoom and rotate
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        rotation += rotate
                        offset = offset.copy(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                        
                        // Reset if zoomed out
                        if (scale <= 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        }
                    }
                }
        ) {
            // Blur background effect (Android 12+)
            AsyncImage(
                model = imagePath,
                contentDescription = "Full Image",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .alpha(0.3f),
                contentScale = ContentScale.Crop
            )

            // Main image with zoom/pan
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

            // Close button
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
