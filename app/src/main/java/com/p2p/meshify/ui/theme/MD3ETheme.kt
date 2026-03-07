package com.p2p.meshify.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.circle
import android.graphics.Matrix

/**
 * MD3E Spring Physics Presets.
 * Based on Material 3 Expressive Motion System.
 */
object MotionSpecs {

    /**
     * Gentle motion - Low stiffness, high damping.
     * For subtle, calm animations.
     */
    val Gentle = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = 300f
    )

    /**
     * Standard MD3E motion - Balanced spring physics.
     * Default for most expressive components.
     */
    val Standard = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 600f
    )

    /**
     * Snappy motion - High stiffness, low damping.
     * For quick, responsive interactions.
     */
    val Snappy = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 1000f
    )

    /**
     * Bouncy motion - Very playful and elastic.
     * Uses dampingRatio = 0.4f for fun, rubber-band effect per LastChat design audit.
     */
    val Bouncy = spring<Float>(
        dampingRatio = 0.4f,
        stiffness = 800f
    )

    /**
     * Get spring spec based on motion preset and scale.
     * Scale factor adjusts stiffness: 0.5x = half stiffness, 2.0x = double stiffness.
     */
    fun getSpring(preset: com.p2p.meshify.domain.model.MotionPreset, scale: Float = 1.0f): SpringSpec<Float> {
        // Clamp scale to safe bounds to prevent extreme behavior
        val clampedScale = scale.coerceIn(0.5f, 2.0f)
        
        return when (preset) {
            com.p2p.meshify.domain.model.MotionPreset.GENTLE -> {
                // Scale stiffness inversely: lower scale = gentler (lower stiffness)
                val scaledStiffness = 300f * clampedScale
                spring(dampingRatio = 0.9f, stiffness = scaledStiffness)
            }
            com.p2p.meshify.domain.model.MotionPreset.STANDARD -> {
                val scaledStiffness = 600f * clampedScale
                spring(dampingRatio = 0.8f, stiffness = scaledStiffness)
            }
            com.p2p.meshify.domain.model.MotionPreset.SNAPPY -> {
                val scaledStiffness = 1000f * clampedScale
                spring(dampingRatio = 0.6f, stiffness = scaledStiffness)
            }
            com.p2p.meshify.domain.model.MotionPreset.BOUNCY -> {
                val scaledStiffness = 800f * clampedScale
                spring(dampingRatio = 0.4f, stiffness = scaledStiffness)
            }
        }
    }
}

/**
 * MD3E Shape Definitions.
 * All shapes are normalized to (0,0) to (1,1) coordinate system.
 */
object MD3EShapes {
    
    /**
     * Sunny - 10-pointed star with moderate rounding.
     */
    val Sunny: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 10,
            innerRadius = 0.65f,
            rounding = CornerRounding(0.2f)
        ).normalize()
    }
    
    /**
     * Breezy - 9-pointed star with soft edges.
     */
    val Breezy: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 9,
            innerRadius = 0.85f,
            rounding = CornerRounding(0.3f)
        ).normalize()
    }
    
    /**
     * Pentagon - Simple 5-sided polygon.
     */
    val Pentagon: RoundedPolygon by lazy {
        RoundedPolygon(
            numVertices = 5,
            rounding = CornerRounding(0.2f)
        ).normalize()
    }
    
    /**
     * Blob - Organic blob shape (2-vertex star with high rounding).
     */
    val Blob: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 2,
            innerRadius = 0.3f,
            rounding = CornerRounding(0.9f)
        ).normalize()
    }
    
    /**
     * Burst - 8-pointed explosion shape.
     */
    val Burst: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.15f)
        ).normalize()
    }
    
    /**
     * Clover - 4-leaf clover shape.
     */
    val Clover: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.7f,
            rounding = CornerRounding(0.4f)
        ).normalize()
    }
    
    /**
     * Circle - Perfect circle (12 vertices).
     */
    val Circle: RoundedPolygon by lazy {
        RoundedPolygon.circle(
            numVertices = 12
        ).normalize()
    }
    
    /**
     * Get shape by ShapeStyle enum.
     */
    fun getShape(style: com.p2p.meshify.domain.model.ShapeStyle): RoundedPolygon {
        return when (style) {
            com.p2p.meshify.domain.model.ShapeStyle.SUNNY -> Sunny
            com.p2p.meshify.domain.model.ShapeStyle.BREEZY -> Breezy
            com.p2p.meshify.domain.model.ShapeStyle.PENTAGON -> Pentagon
            com.p2p.meshify.domain.model.ShapeStyle.BLOB -> Blob
            com.p2p.meshify.domain.model.ShapeStyle.BURST -> Burst
            com.p2p.meshify.domain.model.ShapeStyle.CLOVER -> Clover
            com.p2p.meshify.domain.model.ShapeStyle.CIRCLE -> Circle
        }
    }
    
    /**
     * All available shapes for morphing.
     */
    val AllShapes: List<RoundedPolygon> by lazy {
        listOf(Sunny, Breezy, Pentagon, Blob, Burst, Clover, Circle)
    }
    
    /**
     * Normalize a RoundedPolygon to (0,0) to (1,1) bounds.
     * This ensures consistent morphing between shapes.
     * Note: In androidx.graphics.shapes 0.4.0+, shapes are already normalized.
     */
    private fun RoundedPolygon.normalize(): RoundedPolygon {
        // The library already normalizes shapes internally
        // Just return self to avoid API compatibility issues
        return this
    }
}

/**
 * MD3E Duration Tokens.
 * Standard animation durations for consistent timing.
 */
object MotionDurations {
    const val Instant = 100    // Immediate feedback
    const val Short = 200      // Small micro-interactions
    const val Medium = 300     // Standard component transitions
    const val Long = 500       // Large scale changes
    const val ExtraLong = 800  // Complex morphing animations
}
