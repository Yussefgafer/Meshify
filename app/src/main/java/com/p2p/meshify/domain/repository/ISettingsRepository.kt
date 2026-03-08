package com.p2p.meshify.domain.repository

import androidx.compose.ui.graphics.Color
import com.p2p.meshify.domain.model.BubbleStyle
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
import kotlinx.coroutines.flow.Flow

/**
 * Validated Theme Modes.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Domain interface for user preferences and identity.
 * Extended for MD3E - Central Source of Truth for all design variables.
 */
interface ISettingsRepository {
    val displayName: Flow<String>
    val themeMode: Flow<ThemeMode>
    val dynamicColorEnabled: Flow<Boolean>
    val hapticFeedbackEnabled: Flow<Boolean>
    val isNetworkVisible: Flow<Boolean>

    // MD3E Settings - Shape Morphing
    val shapeStyle: Flow<ShapeStyle>

    // MD3E Settings - Motion System
    val motionPreset: Flow<MotionPreset>
    val motionScale: Flow<Float>

    // MD3E Settings - Typography
    val fontFamilyPreset: Flow<FontFamilyPreset>
    val customFontUri: Flow<String?>

    // MD3E Settings - Chat Bubbles
    val bubbleStyle: Flow<BubbleStyle>

    // MD3E Settings - Visual Density
    val visualDensity: Flow<Float>

    // MD3E Settings - Seed Color (for static theming when dynamic color is off)
    val seedColor: Flow<Int>

    suspend fun getDeviceId(): String
    suspend fun updateDisplayName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setNetworkVisibility(visible: Boolean)

    // MD3E Setting Mutators
    suspend fun setShapeStyle(style: ShapeStyle)
    suspend fun setMotionPreset(preset: MotionPreset)
    suspend fun setMotionScale(scale: Float)
    suspend fun setFontFamilyPreset(family: FontFamilyPreset)
    suspend fun setCustomFontUri(uri: String?)
    suspend fun setBubbleStyle(style: BubbleStyle)
    suspend fun setVisualDensity(density: Float)
    suspend fun setSeedColor(color: Int)

    fun getAppVersion(): String
}
