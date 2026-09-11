package com.p2p.meshify.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.p2p.meshify.core.ui.designsystem.DxTheme
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.hooks.PremiumHaptics

class FakePremiumHaptics(val calls: MutableList<HapticPattern> = mutableListOf()) : PremiumHaptics(FakeHapticFeedback(), null, true) { override fun perform(pattern: HapticPattern){calls.add(pattern)} }
private class FakeHapticFeedback: HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {} }
@Composable fun DxTestThemeOb(content: @Composable () -> Unit) { val f=FakePremiumHaptics(); CompositionLocalProvider(LocalPremiumHaptics provides f){ DxTheme(dynamicColor=false){content()}} }
@Composable fun DxTestThemeObWithFake(fake: FakePremiumHaptics, content: @Composable () -> Unit){ CompositionLocalProvider(LocalPremiumHaptics provides fake){ DxTheme(dynamicColor=false){content()}} }
