package com.p2p.meshify.domain.model

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon

/**
 * MD3E Signal Strength Enum.
 * Represents the RSSI-based signal quality for peer devices in Discovery screen.
 *
 * Used by SignalMorphAvatar to determine:
 * - Shape morphing speed (stronger = faster)
 * - Shape selection (stronger = more complex shapes)
 * - Color treatment
 */
enum class SignalStrength {
    /**
     * Excellent signal (RSSI > -50 dBm)
     * - Shape: Sunny ↔ Breezy (complex, vibrant)
     * - Speed: Very fast (500ms)
     * - Color: Primary/Strong teal
     */
    STRONG,

    /**
     * Good signal (RSSI -50 to -70 dBm)
     * - Shape: Breezy ↔ Circle (moderate complexity)
     * - Speed: Medium (900ms)
     * - Color: Secondary/Muted teal
     */
    MEDIUM,

    /**
     * Weak signal (RSSI < -70 dBm)
     * - Shape: Circle ↔ Blob (simple, calm)
     * - Speed: Slow (1500ms)
     * - Color: Gray/desaturated
     */
    WEAK,

    /**
     * Offline/Disconnected
     * - Shape: Circle (static, no morphing)
     * - Speed: No animation
     * - Color: Gray overlay
     */
    OFFLINE;

    companion object {
        /**
         * Convert RSSI (dBm) to SignalStrength.
         * RSSI values are typically negative, closer to 0 = stronger signal.
         *
         * @param rssi The signal strength in dBm (e.g., -42, -65, -80)
         * @return Corresponding SignalStrength enum value
         */
        fun fromRssi(rssi: Int): SignalStrength {
            return when {
                rssi > -50 -> STRONG      // Excellent signal
                rssi in -70..-50 -> MEDIUM // Good signal
                rssi < -70 -> WEAK         // Weak signal
                else -> OFFLINE
            }
        }
    }
}

/**
 * Get morph duration based on signal strength.
 * Stronger signals = faster morphing (more "vitality").
 *
 * @return Duration in milliseconds for one morph cycle
 */
fun SignalStrength.getMorphDuration(): Int {
    return when (this) {
        SignalStrength.STRONG -> 500   // Very fast
        SignalStrength.MEDIUM -> 900   // Medium
        SignalStrength.WEAK -> 1500    // Slow
        SignalStrength.OFFLINE -> 0    // No animation
    }
}

/**
 * Get shape pair for morphing based on signal strength.
 * Returns two shapes to morph between.
 *
 * Note: This uses simple shapes from androidx.graphics.shapes.
 * For complex MD3E shapes (Sunny, Breezy, etc.), use the UI layer implementation.
 */
fun SignalStrength.getShapePair(): List<RoundedPolygon> {
    return when (this) {
        SignalStrength.STRONG -> listOf(
            RoundedPolygon(numVertices = 10, radius = 1f),
            RoundedPolygon(numVertices = 9, radius = 1f)
        )
        SignalStrength.MEDIUM -> listOf(
            RoundedPolygon(numVertices = 9, radius = 1f),
            RoundedPolygon(numVertices = 6, radius = 1f)
        )
        SignalStrength.WEAK -> listOf(
            RoundedPolygon(numVertices = 6, radius = 1f),
            RoundedPolygon(numVertices = 4, radius = 1f)
        )
        SignalStrength.OFFLINE -> {
            val circle = RoundedPolygon(numVertices = 16, radius = 1f)
            listOf(circle, circle)
        }
    }
}
