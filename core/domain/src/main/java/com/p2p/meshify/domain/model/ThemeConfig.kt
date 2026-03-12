package com.p2p.meshify.domain.model

/**
 * MD3E Shape Styles - Central source of truth for shape morphing.
 */
enum class ShapeStyle {
    SUNNY,      // 10-pointed star
    BREEZY,     // 9-pointed star
    PENTAGON,   // 5-sided polygon
    BLOB,       // Organic blob shape
    BURST,      // 8-pointed explosion
    CLOVER,     // 4-leaf clover
    CIRCLE      // Perfect circle
}

/**
 * MD3E Motion Presets - Spring physics configurations.
 */
enum class MotionPreset {
    GENTLE,     // Low stiffness, high damping
    STANDARD,   // Balanced MD3E default
    SNAPPY,     // High stiffness, low damping
    BOUNCY      // Very bouncy, playful
}

/**
 * MD3E Font Families - Google Fonts integration.
 */
enum class FontFamilyPreset {
    ROBOTO,         // Default system font
    POPPINS,        // Modern geometric
    LORA,           // Elegant serif
    MONTSERRAT,     // Urban contemporary
    PLAYFAIR,       // Display serif
    INTER           // Clean UI font
}

/**
 * MD3E Bubble Styles - Chat bubble shapes.
 */
enum class BubbleStyle {
    ROUNDED,        // Classic rounded
    TAILED,         // With speech tail
    SQUARCLES,      // Square-circles
    ORGANIC         // Free-form organic
}
