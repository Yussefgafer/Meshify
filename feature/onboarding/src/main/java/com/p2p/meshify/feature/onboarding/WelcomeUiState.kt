package com.p2p.meshify.feature.onboarding

data class WelcomeUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 3,
    val isAnimating: Boolean = false,
    val isLangMenuOpen: Boolean = false,
    val isPermissionFlowActive: Boolean = false,
    val isSummaryVisible: Boolean = false
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
    val initialStatus: PermissionStatus = PermissionStatus.NotAsked
)

enum class PermissionStatus {
    NotAsked, Granted, Denied, DeniedPermanently, Skipped, AlreadyGranted
}

sealed class PermissionIconType {
    object Wifi : PermissionIconType()
    object Bluetooth : PermissionIconType()
    object Notifications : PermissionIconType()
    object Location : PermissionIconType()
}

sealed class PermissionRequestResult {
    object Granted : PermissionRequestResult()
    object Denied : PermissionRequestResult()
    object DeniedPermanently : PermissionRequestResult()
}
