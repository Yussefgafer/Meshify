package com.p2p.meshify.core.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Meshify Unified Design System.
 * Single Source of Truth for Spacing, Shapes, and Elevation.
 */
object MeshifyDesignSystem {

    object Spacing {
        val Xxs = 4.dp
        val Xs = 8.dp
        val Sm = 12.dp
        val Md = 16.dp
        val Lg = 24.dp
        val Xl = 32.dp
        val Xxl = 48.dp
    }

    object Shapes {
        val CardLarge = RoundedCornerShape(28.dp)
        val CardMedium = RoundedCornerShape(20.dp)
        val CardSmall = RoundedCornerShape(16.dp)
        val BubbleMe = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        val BubblePeer = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        val Button = RoundedCornerShape(20.dp)
        val Input = RoundedCornerShape(24.dp)
    }

    object Elevation {
        val Level0 = 0.dp
        val Level1 = 1.dp
        val Level2 = 2.dp
        val Level3 = 4.dp
        val Level4 = 6.dp
        val Level5 = 8.dp
    }

    object Motion {
        fun <T> expressiveSpring() = spring<T>(
            dampingRatio = 0.75f,
            stiffness = 350f
        )
    }
}
