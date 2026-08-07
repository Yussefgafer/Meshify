package com.p2p.meshify.core.ui.designsystem.foundation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes

/**
 * Centralised references to MaterialShapes polygons used across the app.
 * These are for moments — loading indicators, artwork masks, dialogs badges —
 * not for layout containment (which stays on DxShape / RoundedCornerShape).
 *
 * House favourites: Cookie9Sided, Sunny, SoftBurst, Pill, Clover4Leaf.
 */
@ExperimentalMaterial3ExpressiveApi
object DxExpressiveShapes {
    val Cookie9Sided get() = MaterialShapes.Cookie9Sided
    val Cookie12Sided get() = MaterialShapes.Cookie12Sided
    val Sunny get() = MaterialShapes.Sunny
    val SoftBurst get() = MaterialShapes.SoftBurst
    val Pill get() = MaterialShapes.Pill
    val Clover4Leaf get() = MaterialShapes.Clover4Leaf
    val Flower get() = MaterialShapes.Flower
    val Puffy get() = MaterialShapes.Puffy
    val Gem get() = MaterialShapes.Gem
    val Burst get() = MaterialShapes.Burst
    val Square get() = MaterialShapes.Square
    val Diamond get() = MaterialShapes.Diamond
    val ClamShell get() = MaterialShapes.ClamShell
    val Arch get() = MaterialShapes.Arch
    val Circle get() = MaterialShapes.Circle
}
