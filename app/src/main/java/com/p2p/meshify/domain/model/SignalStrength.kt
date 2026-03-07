package com.p2p.meshify.domain.model

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

        /**
         * Get morph duration based on signal strength.
         * Stronger signals = faster morphing (more "vitality").
         * 
         * @return Duration in milliseconds for one morph cycle
         */
        fun SignalStrength.getMorphDuration(): Int {
            return when (this) {
                STRONG -> 500   // Very fast
                MEDIUM -> 900   // Medium
                WEAK -> 1500    // Slow
                OFFLINE -> 0    // No animation
            }
        }

        /**
         * Get shape pair for morphing based on signal strength.
         * Returns two shapes to morph between.
         */
        fun SignalStrength.getShapePair(): List<androidx.graphics.shapes.RoundedPolygon> {
            return when (this) {
                STRONG -> listOf(
                    com.p2p.meshify.ui.theme.MD3EShapes.Sunny,
                    com.p2p.meshify.ui.theme.MD3EShapes.Breezy
                )
                MEDIUM -> listOf(
                    com.p2p.meshify.ui.theme.MD3EShapes.Breezy,
                    com.p2p.meshify.ui.theme.MD3EShapes.Circle
                )
                WEAK -> listOf(
                    com.p2p.meshify.ui.theme.MD3EShapes.Circle,
                    com.p2p.meshify.ui.theme.MD3EShapes.Blob
                )
                OFFLINE -> listOf(
                    com.p2p.meshify.ui.theme.MD3EShapes.Circle,
                    com.p2p.meshify.ui.theme.MD3EShapes.Circle
                )
            }
        }
    }
}
