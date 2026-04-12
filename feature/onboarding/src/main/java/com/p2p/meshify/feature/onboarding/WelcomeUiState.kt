package com.p2p.meshify.feature.onboarding

/**
 * UI state for the onboarding flow.
 * Tracks current page, language, animation state, and permission progress.
 */
data class WelcomeUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 3,
    val isAnimating: Boolean = false,
    val isLangMenuOpen: Boolean = false,
    val isPermissionFlowActive: Boolean = false,
    val isSummaryVisible: Boolean = false
)

/**
 * Represents a single step in the "How It Works" page.
 */
data class HowItWorksStep(
    val titleRes: Int,
    val descRes: Int,
    val illustrationType: IllustrationType
)

/**
 * Data class representing a permission that needs explanation + request.
 */
data class PermissionInfo(
    val id: String,
    val iconType: PermissionIconType,
    val labelRes: Int,
    val importanceLabelRes: Int, // "Required" or "Optional"
    val isRequired: Boolean,
    val whatHappensRes: List<Int>,
    val ifDenyRes: List<Int>,
    val androidPermissions: List<String>, // The actual Android permissions to request
    val initialStatus: PermissionStatus = PermissionStatus.NotAsked
)

/**
 * Status of a single permission after request.
 */
enum class PermissionStatus {
    NotAsked,
    Granted,
    Denied,
    DeniedPermanently,
    Skipped,
    AlreadyGranted
}

/**
 * Icon types for permission cards (avoids Android dependency in domain layer).
 */
sealed class PermissionIconType {
    object Wifi : PermissionIconType()
    object Bluetooth : PermissionIconType()
    object Notifications : PermissionIconType()
    object Location : PermissionIconType()
}

/**
 * The result of requesting a single permission from Android.
 */
sealed class PermissionRequestResult {
    object Granted : PermissionRequestResult()
    object Denied : PermissionRequestResult()
    object DeniedPermanently : PermissionRequestResult()
}
