package com.p2p.meshify.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.p2p.meshify.domain.model.FontFamilyPreset

/**
 * MD3E Font Families.
 * Using system fonts for simplicity. Google Fonts can be added later if needed.
 */
object MD3EFontFamilies {

    /**
     * Roboto - Default system font.
     * Clean, modern, and highly readable.
     */
    val Roboto = FontFamily.SansSerif

    /**
     * Poppins - Modern geometric sans-serif.
     * Perfect for headings and display text.
     */
    val Poppins = FontFamily.SansSerif

    /**
     * Lora - Elegant serif font.
     * Ideal for long-form reading.
     */
    val Lora = FontFamily.Serif

    /**
     * Montserrat - Urban sans-serif.
     * Great for UI elements and buttons.
     */
    val Montserrat = FontFamily.SansSerif

    /**
     * Playfair Display - Classic serif.
     * Perfect for titles and headers.
     */
    val PlayfairDisplay = FontFamily.Serif

    /**
     * Inter - Clean, readable sans-serif.
     * Excellent for body text.
     */
    val Inter = FontFamily.SansSerif

    /**
     * Get FontFamily by preset enum.
     */
    fun getFontFamily(preset: FontFamilyPreset): FontFamily {
        return when (preset) {
            FontFamilyPreset.POPPINS -> Poppins
            FontFamilyPreset.LORA -> Lora
            FontFamilyPreset.MONTSERRAT -> Montserrat
            FontFamilyPreset.PLAYFAIR -> PlayfairDisplay
            FontFamilyPreset.INTER -> Inter
            FontFamilyPreset.ROBOTO -> Roboto
        }
    }
}
