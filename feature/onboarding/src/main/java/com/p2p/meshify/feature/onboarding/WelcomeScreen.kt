package com.p2p.meshify.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.theme.StatusOnline

/**
 * Main onboarding screen composable.
 * 3 pages: Welcome → How It Works → Permissions
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    currentLang: String,
    onLangChange: (String) -> Unit,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalPremiumHaptics.current

    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f
    ) { 3 }

    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) {
            pagerState.animateScrollToPage(
                page = uiState.currentPage,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage && !uiState.isAnimating) {
            viewModel.goToPage(pagerState.currentPage)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Language chip + Skip
            TopBar(
                currentLang = currentLang,
                onLangMenuToggle = { viewModel.toggleLangMenu() },
                isLangMenuOpen = uiState.isLangMenuOpen,
                onLangSelected = { lang ->
                    haptics.perform(HapticPattern.Pop)
                    viewModel.toggleLangMenu()
                    onLangChange(lang)
                },
                onSkipClick = {
                    haptics.perform(HapticPattern.Cancel)
                    onSkipClick()
                }
            )

            // Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        onLangMenuToggle = { viewModel.toggleLangMenu() },
                        isLangMenuOpen = uiState.isLangMenuOpen,
                        currentLang = currentLang,
                        onLangSelected = { lang ->
                            haptics.perform(HapticPattern.Pop)
                            viewModel.toggleLangMenu()
                            onLangChange(lang)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    1 -> HowItWorksPage(modifier = Modifier.fillMaxSize())

                    2 -> PermissionsOverviewPage(
                        permissions = PermissionDefinitions.getPermissions(),
                        permissionStatuses = emptyMap(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom: Page dots + Next / Get Started button
            BottomNav(
                currentPage = uiState.currentPage,
                totalPages = 3,
                isAnimating = uiState.isAnimating,
                onPageSelected = { pageIndex ->
                    haptics.perform(HapticPattern.Tick)
                    viewModel.goToPage(pageIndex)
                },
                onNextClick = {
                    haptics.perform(HapticPattern.Pop)
                    if (uiState.currentPage < 2) {
                        viewModel.nextPage()
                    } else {
                        onNextClick()
                    }
                }
            )
        }
    }
}

// ============================================================
// TOP BAR
// ============================================================

@Composable
private fun TopBar(
    currentLang: String,
    onLangMenuToggle: () -> Unit,
    isLangMenuOpen: Boolean,
    onLangSelected: (String) -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MeshifyDesignSystem.Spacing.Md,
                vertical = MeshifyDesignSystem.Spacing.Xs
            )
    ) {
        // Language chip
        FilterChip(
            selected = false,
            onClick = onLangMenuToggle,
            label = {
                Text(
                    text = if (currentLang == "ar") stringResource(R.string.ob_lang_ar) else stringResource(R.string.ob_lang_en),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Language,
                    contentDescription = stringResource(R.string.ob_cd_lang_switch),
                    modifier = Modifier.size(16.dp)
                )
            },
            shape = MeshifyDesignSystem.Shapes.Pill
        )

        DropdownMenu(
            expanded = isLangMenuOpen,
            onDismissRequest = onLangMenuToggle,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ob_lang_en)) },
                onClick = { onLangSelected("en") },
                leadingIcon = {
                    if (currentLang == "en") {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ob_lang_ar)) },
                onClick = { onLangSelected("ar") },
                leadingIcon = {
                    if (currentLang == "ar") {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
        // Skip button
        TextButton(
            onClick = onSkipClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text(
                text = stringResource(R.string.ob_btn_skip),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================
// BOTTOM NAV
// ============================================================

@Composable
private fun BottomNav(
    currentPage: Int,
    totalPages: Int,
    isAnimating: Boolean,
    onPageSelected: (Int) -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MeshifyDesignSystem.Spacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
    ) {
        // Page dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalPages) {
                PageDot(
                    isActive = i == currentPage,
                    onClick = { onPageSelected(i) }
                )
            }
        }

        // Button
        Button(
            onClick = onNextClick,
            enabled = !isAnimating,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MeshifyDesignSystem.Shapes.Button
        ) {
            Text(
                text = if (currentPage == 2) {
                    stringResource(R.string.ob_btn_get_started)
                } else {
                    stringResource(R.string.ob_btn_next)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun PageDot(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageIndicatorDesc = stringResource(R.string.ob_cd_page_indicator)
    val animateFloat by animateFloatAsState(
        targetValue = if (isActive) 12f else 8f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "dotSize"
    )

    Box(
        modifier = modifier
            .size(animateFloat.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = pageIndicatorDesc }
    )
}

// ============================================================
// PERMISSION CARD (Slide-up modal)
// ============================================================

@Composable
fun PermissionRequestCard(
    permission: PermissionInfo,
    onAllowClick: () -> Unit,
    onDenyClick: () -> Unit,
    onRequestDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)
        ) + fadeIn(tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200)
        ) + fadeOut(tween(200))
    ) {
        // Dim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRequestDismiss
                )
        ) {
            // Card
            Card(
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = MeshifyDesignSystem.Spacing.Xl),
                shape = MeshifyDesignSystem.Shapes.CardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Icon(
                        imageVector = when (permission.iconType) {
                            PermissionIconType.Wifi -> Icons.Filled.Wifi
                            PermissionIconType.Bluetooth -> Icons.AutoMirrored.Filled.BluetoothSearching
                            PermissionIconType.Notifications -> Icons.Filled.Notifications
                            PermissionIconType.Location -> Icons.Filled.LocationOn
                        },
                        contentDescription = null,
                        modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XXL),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                    Text(
                        text = stringResource(permission.labelRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                    // What happens
                    InfoSection(
                        titleRes = R.string.ob_card_why_title,
                        pointsRes = permission.whatHappensRes,
                        iconTint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                    // If deny
                    InfoSection(
                        titleRes = R.string.ob_card_deny_title,
                        pointsRes = permission.ifDenyRes,
                        iconTint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
                    ) {
                        TextButton(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                onDenyClick()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ob_card_deny),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                onAllowClick()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MeshifyDesignSystem.Shapes.Button
                        ) {
                            Text(
                                text = stringResource(R.string.ob_card_allow),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    titleRes: Int,
    pointsRes: List<Int>,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MeshifyDesignSystem.Shapes.CardMedium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(MeshifyDesignSystem.Spacing.Md)
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = iconTint
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xxs))

        pointsRes.forEach { pointRes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
                Text(
                    text = stringResource(pointRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xxs))
        }
    }
}

// ============================================================
// PERMISSION RESULT CARD
// ============================================================

@Composable
fun PermissionResultCard(
    permission: PermissionInfo,
    result: PermissionRequestResult,
    modifier: Modifier = Modifier
) {
    val (icon, iconTint, statusText) = when (result) {
        PermissionRequestResult.Granted -> Triple(Icons.Default.Check, StatusOnline, R.string.ob_perm_granted)
        PermissionRequestResult.Denied -> Triple(Icons.Default.Close, MaterialTheme.colorScheme.error, R.string.ob_perm_denied)
        PermissionRequestResult.DeniedPermanently -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, R.string.ob_perm_denied_permanent)
    }

    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        ) + fadeIn(tween(200))
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f),
            shape = MeshifyDesignSystem.Shapes.CardLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XXL),
                    tint = iconTint
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(permission.labelRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(statusText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = iconTint
                    )
                }
            }
        }
    }
}

// ============================================================
// SUMMARY DIALOG
// ============================================================

@Composable
fun PermissionSummaryDialog(
    grantedCount: Int,
    totalCount: Int,
    permissionResults: Map<String, PermissionRequestResult>,
    onStartClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current
    var showDetails by remember { mutableStateOf(false) }
    val allGranted = grantedCount == totalCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(MeshifyDesignSystem.Shapes.CardLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = StatusOnline.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(40.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (allGranted) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (allGranted) StatusOnline else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                Text(
                    text = stringResource(R.string.ob_summary_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))

                Text(
                    text = stringResource(R.string.ob_summary_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                if (!allGranted) {
                    Text(
                        text = stringResource(R.string.ob_summary_count, grantedCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xxs))
                    Text(
                        text = stringResource(R.string.ob_summary_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = stringResource(R.string.ob_summary_all_granted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusOnline,
                        textAlign = TextAlign.Center
                    )
                }

                // Expandable details
                AnimatedVisibility(
                    visible = showDetails,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MeshifyDesignSystem.Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xxs)
                    ) {
                        permissionResults.forEach { (id, result) ->
                            val perm = PermissionDefinitions.getPermissions().find { it.id == id } ?: return@forEach
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(perm.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val (statusText, color) = when (result) {
                                    PermissionRequestResult.Granted -> R.string.ob_perm_granted to StatusOnline
                                    PermissionRequestResult.Denied -> R.string.ob_perm_denied to MaterialTheme.colorScheme.error
                                    PermissionRequestResult.DeniedPermanently -> R.string.ob_perm_denied_permanent to MaterialTheme.colorScheme.error
                                }
                                Text(
                                    text = stringResource(statusText),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = color
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        showDetails = !showDetails
                        haptics.perform(HapticPattern.Tick)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.ob_summary_view_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        onStartClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MeshifyDesignSystem.Shapes.Button
                ) {
                    Text(
                        text = stringResource(R.string.ob_summary_start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ============================================================
// PERMISSION DEFINITIONS
// ============================================================

object PermissionDefinitions {

    fun getPermissions(): List<PermissionInfo> {
        val permissions = mutableListOf<PermissionInfo>()

        // 1. Nearby WiFi (Android 13+) or Location (Android < 13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionInfo(
                    id = "nearby_wifi",
                    iconType = PermissionIconType.Wifi,
                    labelRes = R.string.ob_perm_label_nearby,
                    importanceLabelRes = R.string.ob_perm_required,
                    isRequired = true,
                    whatHappensRes = listOf(
                        R.string.ob_perm_nearby_why_1,
                        R.string.ob_perm_nearby_why_2
                    ),
                    ifDenyRes = listOf(
                        R.string.ob_perm_nearby_deny_1,
                        R.string.ob_perm_nearby_deny_2
                    ),
                    androidPermissions = listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                )
            )
        } else {
            permissions.add(
                PermissionInfo(
                    id = "location",
                    iconType = PermissionIconType.Location,
                    labelRes = R.string.ob_perm_label_nearby,
                    importanceLabelRes = R.string.ob_perm_required,
                    isRequired = true,
                    whatHappensRes = listOf(
                        R.string.ob_perm_loc_why_1,
                        R.string.ob_perm_loc_why_2
                    ),
                    ifDenyRes = listOf(
                        R.string.ob_perm_loc_deny_1,
                        R.string.ob_perm_loc_deny_2
                    ),
                    androidPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            )
        }

        // 2. Bluetooth
        permissions.add(
            PermissionInfo(
                id = "bluetooth",
                iconType = PermissionIconType.Bluetooth,
                labelRes = R.string.ob_perm_label_bt,
                importanceLabelRes = R.string.ob_perm_optional,
                isRequired = false,
                whatHappensRes = listOf(
                    R.string.ob_perm_bt_why_1,
                    R.string.ob_perm_bt_why_2
                ),
                ifDenyRes = listOf(
                    R.string.ob_perm_bt_deny_1,
                    R.string.ob_perm_bt_deny_2
                ),
                androidPermissions = listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        )

        // 3. Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionInfo(
                    id = "notifications",
                    iconType = PermissionIconType.Notifications,
                    labelRes = R.string.ob_perm_label_notif,
                    importanceLabelRes = R.string.ob_perm_optional,
                    isRequired = false,
                    whatHappensRes = listOf(
                        R.string.ob_perm_notif_why_1,
                        R.string.ob_perm_notif_why_2
                    ),
                    ifDenyRes = listOf(
                        R.string.ob_perm_notif_deny_1,
                        R.string.ob_perm_notif_deny_2
                    ),
                    androidPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        return permissions
    }
}
