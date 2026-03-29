package com.p2p.meshify.core.ui.hooks

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.p2p.meshify.domain.repository.ISettingsRepository

/**
 * Premium haptic feedback patterns for a tactile, immersive experience.
 * Ported from LastChat for Meshify.
 */
sealed class HapticPattern {
    data object Tick : HapticPattern()
    data object Pop : HapticPattern()
    data object Thud : HapticPattern()
    data object Buildup : HapticPattern()
    data object Success : HapticPattern()
    data object Error : HapticPattern()
    data object DragStart : HapticPattern()
    data object DragEnd : HapticPattern()
    data object Send : HapticPattern()
    data object ScrollEdge : HapticPattern()
    data object Selection : HapticPattern()
    data object Cancel : HapticPattern()
}

class PremiumHaptics(
    private val hapticFeedback: HapticFeedback,
    private val vibrator: Vibrator?,
    private val enabled: Boolean
) {
    private fun safeVibrate(effect: () -> Unit, fallback: HapticFeedbackType) {
        if (!enabled) return
        try {
            if (vibrator?.hasVibrator() == true) {
                effect()
            } else {
                hapticFeedback.performHapticFeedback(fallback)
            }
        } catch (e: Exception) {
            Log.w("PremiumHaptics", "Vibration failed, using fallback", e)
            try {
                hapticFeedback.performHapticFeedback(fallback)
            } catch (e2: Exception) {
                Log.w("PremiumHaptics", "Fallback haptic also failed", e2)
            }
        }
    }

    fun perform(pattern: HapticPattern) {
        if (!enabled) return

        when (pattern) {
            HapticPattern.Tick -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) },
                        HapticFeedbackType.TextHandleMove
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            HapticPattern.Pop -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.Thud -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)) },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.Buildup -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeVibrate(
                        {
                            val effect = VibrationEffect.createWaveform(
                                longArrayOf(0, 20, 30, 40),
                                intArrayOf(0, 80, 150, 255),
                                -1
                            )
                            vibrator?.vibrate(effect)
                        },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.Success -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)) },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.Error -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeVibrate(
                        {
                            val effect = VibrationEffect.createWaveform(
                                longArrayOf(0, 30, 50, 30),
                                intArrayOf(0, 200, 0, 200),
                                -1
                            )
                            vibrator?.vibrate(effect)
                        },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.DragStart -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) },
                        HapticFeedbackType.TextHandleMove
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            HapticPattern.DragEnd -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)) },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.Send -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeVibrate(
                        {
                            val effect = VibrationEffect.createOneShot(40, 220)
                            vibrator?.vibrate(effect)
                        },
                        HapticFeedbackType.LongPress
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            HapticPattern.ScrollEdge -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeVibrate(
                        {
                            val effect = VibrationEffect.createOneShot(15, 60)
                            vibrator?.vibrate(effect)
                        },
                        HapticFeedbackType.TextHandleMove
                    )
                }
            }
            HapticPattern.Selection -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    safeVibrate(
                        { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)) },
                        HapticFeedbackType.TextHandleMove
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            HapticPattern.Cancel -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeVibrate(
                        {
                            val effect = VibrationEffect.createOneShot(25, 100)
                            vibrator?.vibrate(effect)
                        },
                        HapticFeedbackType.TextHandleMove
                    )
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
}

val LocalPremiumHaptics = staticCompositionLocalOf<PremiumHaptics> {
    error("No PremiumHaptics provided")
}

@Composable
fun rememberPremiumHaptics(settingsRepository: ISettingsRepository): PremiumHaptics {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val isEnabled by settingsRepository.hapticFeedbackEnabled.collectAsState(initial = true)

    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            Log.w("PremiumHaptics", "Failed to get vibrator service", e)
            null
        }
    }

    return remember(hapticFeedback, vibrator, isEnabled) {
        PremiumHaptics(hapticFeedback, vibrator, isEnabled)
    }
}
