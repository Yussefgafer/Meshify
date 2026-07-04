package com.p2p.meshify.feature.onboarding

data class WelcomeUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 3,
    val isAnimating: Boolean = false,
    val isLangMenuOpen: Boolean = false
)

data class PermissionInfo(
    val id: String,
    val iconType: PermissionIconType,
    val labelRes: Int,
    val importanceLabelRes: Int,
    val isRequired: Boolean,
    val whatHappensRes: List<Int>,
    val ifDenyRes: List<Int>,
    val androidPermissions: List<String>,
)

enum class PermissionStatus {
    NotAsked, Granted, Denied, DeniedPermanently, Skipped, AlreadyGranted
}

enum class PermissionIconType {
    Wifi, Bluetooth, Notifications, Location
}

sealed class PermissionRequestResult {
    object Granted : PermissionRequestResult()
    object Denied : PermissionRequestResult()
    object DeniedPermanently : PermissionRequestResult()
    object Skipped : PermissionRequestResult()
    object AlreadyGranted : PermissionRequestResult()
}

/**
 * Convert a [PermissionRequestResult] to the corresponding [PermissionStatus].
 */
fun PermissionRequestResult.toPermissionStatus(): PermissionStatus = when (this) {
    PermissionRequestResult.Granted -> PermissionStatus.Granted
    PermissionRequestResult.Denied -> PermissionStatus.Denied
    PermissionRequestResult.DeniedPermanently -> PermissionStatus.DeniedPermanently
    PermissionRequestResult.Skipped -> PermissionStatus.Skipped
    PermissionRequestResult.AlreadyGranted -> PermissionStatus.AlreadyGranted
}
