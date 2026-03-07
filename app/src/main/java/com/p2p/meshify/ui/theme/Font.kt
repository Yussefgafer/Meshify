package com.p2p.meshify.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.p2p.meshify.R

/**
 * Google Fonts Provider for MD3E Typography.
 */
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * MD3E Font Families - Google Fonts integration.
 * All fonts are loaded from Google Fonts for consistency.
 */
object MD3EFontFamilies {
    
    /**
     * Roboto - Default system font.
     * Clean, modern, and highly readable.
     */
    val Roboto = FontFamily.SansSerif
    
    /**
     * Poppins - Modern geometric sans-serif.
     * Excellent for headings and display text.
     */
    val Poppins = FontFamily(
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Light),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
        Font(googleFont = GoogleFont("Poppins"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Black)
    )
    
    /**
     * Lora - Elegant serif font.
     * Perfect for long-form reading and sophisticated UI.
     */
    val Lora = FontFamily(
        Font(googleFont = GoogleFont("Lora"), fontProvider = provider),
        Font(googleFont = GoogleFont("Lora"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(googleFont = GoogleFont("Lora"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
        Font(googleFont = GoogleFont("Lora"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        Font(googleFont = GoogleFont("Lora"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Bold)
    )
    
    /**
     * Montserrat - Urban contemporary sans-serif.
     * Great for modern, professional interfaces.
     */
    val Montserrat = FontFamily(
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Light),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
        Font(googleFont = GoogleFont("Montserrat"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Black)
    )
    
    /**
     * Playfair Display - High-contrast serif for display.
     * Elegant and sophisticated for headlines.
     */
    val PlayfairDisplay = FontFamily(
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
        Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Black)
    )
    
    /**
     * Inter - Clean, optimized UI font.
     * Designed for computer screens with excellent readability.
     */
    val Inter = FontFamily(
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Thin),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Light),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
        Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = androidx.compose.ui.text.font.FontWeight.Black)
    )
    
    /**
     * Get font family by preset enum.
     */
    fun getFontFamily(preset: com.p2p.meshify.domain.model.FontFamilyPreset): FontFamily {
        return when (preset) {
            com.p2p.meshify.domain.model.FontFamilyPreset.ROBOTO -> Roboto
            com.p2p.meshify.domain.model.FontFamilyPreset.POPTINS -> Poppins
            com.p2p.meshify.domain.model.FontFamilyPreset.LORA -> Lora
            com.p2p.meshify.domain.model.FontFamilyPreset.MONTSERRAT -> Montserrat
            com.p2p.meshify.domain.model.FontFamilyPreset.PLAYFAIR -> PlayfairDisplay
            com.p2p.meshify.domain.model.FontFamilyPreset.INTER -> Inter
        }
    }
}
