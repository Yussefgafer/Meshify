package com.p2p.meshify.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WelcomeViewModel].
 *
 * Scope:
 *   Pure JVM — WelcomeViewModel has no constructor dependencies and only exposes
 *   `toggleLangMenu()` (flipping `isLangMenuOpen` in the uiState). No Hilt, no
 *   coroutines, no Android context, so the test stays a plain JUnit4 assertion.
 *
 * What's tested:
 *   - `isLangMenuOpen` defaults to false.
 *   - `toggleLangMenu` flips the flag true -> false -> true.
 */
class WelcomeViewModelTest {

    @Test
    fun `uiState — isLangMenuOpen defaults to false`() {
        val vm = WelcomeViewModel()
        assertFalse(vm.uiState.value.isLangMenuOpen)
    }

    @Test
    fun `toggleLangMenu — flips the menu flag on each call`() {
        val vm = WelcomeViewModel()

        vm.toggleLangMenu()
        assertTrue(vm.uiState.value.isLangMenuOpen)

        vm.toggleLangMenu()
        assertFalse(vm.uiState.value.isLangMenuOpen)

        vm.toggleLangMenu()
        assertTrue(vm.uiState.value.isLangMenuOpen)
    }
}
