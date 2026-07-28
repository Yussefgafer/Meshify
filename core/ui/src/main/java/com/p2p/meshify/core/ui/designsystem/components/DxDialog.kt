package com.p2p.meshify.core.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.p2p.meshify.core.ui.designsystem.foundation.DxShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/**
 * Expressive dialog following the Koda recipe:
 * - [AlertDialog] with `surfaceContainerHigh` background, 32dp corners
 * - 64dp icon box clipped to an optional [MaterialShapes] polygon
 * - Spring [scaleIn] entrance via [AnimatedVisibility]
 *
 * @param visible Whether the dialog should be shown.
 * @param onDismiss Called when the dialog is dismissed.
 * @param icon Icon vector to display in the badge.
 * @param iconTint Tint for the icon (defaults to [MaterialTheme.colorScheme.primary]).
 * @param iconBackground Background colour for the icon box (defaults to primary at 15%).
 * @param polygon Optional [RoundedPolygon] (e.g. [MaterialShapes.Sunny]) to clip the icon box.
 *   When null the icon box uses a 28dp rounded corner.
 * @param badgeSize Size of the icon badge (default 64dp).
 * @param title Dialog title content.
 * @param text Dialog body content.
 * @param confirmButton Confirm button composable.
 * @param dismissButton Dismiss button composable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DxDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = iconTint.copy(alpha = 0.15f),
    polygon: RoundedPolygon? = null,
    badgeSize: Dp = 64.dp,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable (() -> Unit)? = null
) {
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) dialogVisible = true
        else dialogVisible = false
    }

    AnimatedVisibility(
        visible = dialogVisible && visible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = DxShape.Dialog,
            icon = {
                val shape = polygon?.let { remember { DxPolygonShape(it) } }
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .then(
                            if (shape != null) Modifier.clip(shape)
                            else Modifier.clip(DxShape.Pill)
                        )
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton
        )
    }
}
