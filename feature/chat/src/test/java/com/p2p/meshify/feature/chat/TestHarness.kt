package com.p2p.meshify.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.p2p.meshify.core.ui.designsystem.DxTheme
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.hooks.PremiumHaptics

class FakePremiumHaptics(val calls: MutableList<HapticPattern> = mutableListOf()) : PremiumHaptics(FakeHapticFeedback(), null, true) {
    override fun perform(pattern: HapticPattern) { calls.add(pattern) }
}
private class FakeHapticFeedback : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {} }
@Composable fun DxTestThemeWithFake(fake: FakePremiumHaptics, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPremiumHaptics provides fake) { DxTheme(dynamicColor=false) { content() } }
}
@Composable fun DxTestThemeChat(content: @Composable () -> Unit) {
    val fake = FakePremiumHaptics()
    CompositionLocalProvider(LocalPremiumHaptics provides fake) { DxTheme(dynamicColor=false) { content() } }
}

@Suppress("UNCHECKED_CAST")
fun <T> injectChatState(vm: Any, fieldName: String, value: T) {
    try {
        val f = vm.javaClass.getDeclaredField(fieldName)
        f.isAccessible = true
        (f.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<T>).value = value
    } catch (e: Exception) {
        throw IllegalStateException("test state injection failed — was $fieldName renamed?", e)
    }
}
