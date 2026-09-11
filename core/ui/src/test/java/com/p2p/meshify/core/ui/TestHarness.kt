package com.p2p.meshify.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.p2p.meshify.core.ui.designsystem.DxTheme
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.hooks.PremiumHaptics

class FakePremiumHaptics(
    val calls: MutableList<HapticPattern> = mutableListOf()
) : PremiumHaptics(FakeHapticFeedback(calls), null, true) {
    override fun perform(pattern: HapticPattern) {
        calls.add(pattern)
    }
}

private class FakeHapticFeedback(private val calls: MutableList<HapticPattern>) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { /* no-op */ }
}

@Composable
fun DxTestTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val fake = FakePremiumHaptics()
    CompositionLocalProvider(LocalPremiumHaptics provides fake) {
        DxTheme(dynamicColor = dynamicColor) {
            content()
        }
    }
}

@Composable
fun DxTestThemeWithFake(
    fake: FakePremiumHaptics,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalPremiumHaptics provides fake) {
        DxTheme(dynamicColor = dynamicColor) {
            content()
        }
    }
}
