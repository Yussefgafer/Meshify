package com.p2p.meshify.core.ui.designsystem.foundation

import androidx.compose.ui.unit.dp

/**
 * MD3E elevation tokens — matches md.sys.elevation.level{0-5}.
 * Koda uses tonal surfaces (surfaceContainer family) for depth,
 * not shadows. Elevation is reserved for overlays and press feedback.
 */
object DxElevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 8.dp
    val Level5 = 12.dp
}
