package com.p2p.meshify.core.ui.designsystem.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Converts an [androidx.graphics.shapes.RoundedPolygon] (from MaterialShapes)
 * to a Compose [Shape] for clipping or backgrounds.
 *
 * Usage:
 * ```
 * val cookieShape = remember { DxPolygonShape(MaterialShapes.Cookie9Sided) }
 * Box(modifier = Modifier.clip(cookieShape).background(...))
 * ```
 */
class DxPolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()

        val bounds = polygon.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        val scaleX = size.width / boundsWidth
        val scaleY = size.height / boundsHeight
        matrix.scale(scaleX, scaleY)

        matrix.translate(-bounds[0], -bounds[1])

        path.transform(matrix)
        return Outline.Generic(path)
    }
}
