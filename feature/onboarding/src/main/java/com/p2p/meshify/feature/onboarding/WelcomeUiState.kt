package com.p2p.meshify.feature.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * UI state for the onboarding flow.
 * Represents the current page and navigation state.
 */
data class WelcomeUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 5,
    val isLastPage: Boolean = false,
    val isAnimating: Boolean = false
)

/**
 * Data class representing a single onboarding page.
 * Contains all information needed to render a page.
 */
data class OnboardingPageInfo(
    val title: String,
    val subtitle: String,
    val description: String,
    val illustrationType: IllustrationType,
    val showLinks: Boolean = false,
    val showGetStartedButton: Boolean = false
)

/**
 * Sealed class for different illustration types.
 * Allows switching between different visual representations.
 */
sealed class IllustrationType {
    object MeshNetwork : IllustrationType()
    object PrivacyShield : IllustrationType()
    object BleNearby : IllustrationType()
    object P2PDevices : IllustrationType()
    object GetStarted : IllustrationType()
}

/**
 * Page configuration for all onboarding pages.
 * Centralized definition for easy maintenance.
 */
object OnboardingPages {
    fun getPages(): List<OnboardingPageInfo> = listOf(
        OnboardingPageInfo(
            title = "onboarding_welcome_title",
            subtitle = "onboarding_welcome_subtitle",
            description = "onboarding_welcome_desc",
            illustrationType = IllustrationType.MeshNetwork,
            showLinks = true
        ),
        OnboardingPageInfo(
            title = "onboarding_privacy_title",
            subtitle = "onboarding_privacy_subtitle",
            description = "onboarding_privacy_desc",
            illustrationType = IllustrationType.PrivacyShield,
            showLinks = true
        ),
        OnboardingPageInfo(
            title = "onboarding_ble_title",
            subtitle = "onboarding_ble_subtitle",
            description = "onboarding_ble_desc",
            illustrationType = IllustrationType.BleNearby
        ),
        OnboardingPageInfo(
            title = "onboarding_p2p_title",
            subtitle = "onboarding_p2p_subtitle",
            description = "onboarding_p2p_desc",
            illustrationType = IllustrationType.P2PDevices
        ),
        OnboardingPageInfo(
            title = "onboarding_get_started_title",
            subtitle = "onboarding_get_started_subtitle",
            description = "onboarding_get_started_desc",
            illustrationType = IllustrationType.GetStarted,
            showGetStartedButton = true
        )
    )
}
