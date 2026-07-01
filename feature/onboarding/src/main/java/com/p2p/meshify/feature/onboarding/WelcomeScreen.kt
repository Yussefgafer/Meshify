package com.p2p.meshify.feature.onboarding

import android.Manifest
import android.os.Build
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    currentLang: String,
    onLangChange: (String) -> Unit,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit,
    permissionStatuses: Map<String, PermissionStatus> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalPremiumHaptics.current
    val pagerState = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f) { 3 }

    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) pagerState.animateScrollToPage(page = uiState.currentPage)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != uiState.currentPage && !uiState.isAnimating) viewModel.goToPage(page)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(
                currentLang = currentLang, onLangMenuToggle = { viewModel.toggleLangMenu() },
                isLangMenuOpen = uiState.isLangMenuOpen, onLangSelected = { onLangChange(it) }, onSkipClick = { onSkipClick() }
            )

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = MeshifyDesignSystem.Spacing.Md)) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomePage(modifier = Modifier.fillMaxSize())
                        1 -> HowItWorksPage(modifier = Modifier.fillMaxSize())
                        2 -> PermissionsOverviewPage(permissions = PermissionDefinitions.getPermissions(), permissionStatuses = permissionStatuses, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            BottomNav(
                currentPage = uiState.currentPage, totalPages = 3, isAnimating = uiState.isAnimating,
                onPageSelected = { haptics.perform(HapticPattern.Tick); viewModel.goToPage(it) },
                onNextClick = { haptics.perform(HapticPattern.Pop); if (uiState.currentPage < 2) viewModel.nextPage() else onNextClick() }
            )
        }
    }
}

@Composable
private fun TopBar(currentLang: String, onLangMenuToggle: () -> Unit, isLangMenuOpen: Boolean, onLangSelected: (String) -> Unit, onSkipClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = MeshifyDesignSystem.Spacing.Md, vertical = MeshifyDesignSystem.Spacing.Xs)) {
        Surface(
            onClick = onLangMenuToggle, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MeshifyDesignSystem.Shapes.Pill, modifier = Modifier.height(40.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)) {
                Icon(Icons.Default.Language, contentDescription = stringResource(R.string.ob_cd_lang_switch), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text = if (currentLang == "ar") stringResource(R.string.ob_lang_ar) else stringResource(R.string.ob_lang_en), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        DropdownMenu(expanded = isLangMenuOpen, onDismissRequest = onLangMenuToggle, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.ob_lang_en)) }, onClick = { onLangSelected("en") }, leadingIcon = { if (currentLang == "en") Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) })
            DropdownMenuItem(text = { Text(stringResource(R.string.ob_lang_ar)) }, onClick = { onLangSelected("ar") }, leadingIcon = { if (currentLang == "ar") Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) })
        }

        TextButton(onClick = onSkipClick, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(text = stringResource(R.string.ob_btn_skip), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomNav(currentPage: Int, totalPages: Int, isAnimating: Boolean, onPageSelected: (Int) -> Unit, onNextClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(MeshifyDesignSystem.Spacing.Lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Lg)) {
        val pageIndicatorDesc = stringResource(R.string.ob_cd_page_indicator)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(totalPages) { index ->
                val isActive = index == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 10.dp, 10.dp)
                        .background(color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onPageSelected(index) })
                        .semantics { contentDescription = pageIndicatorDesc }
                )
            }
        }

        Surface(
            onClick = onNextClick, enabled = !isAnimating, color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary, shape = MeshifyDesignSystem.Shapes.Button,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (currentPage == 2) stringResource(R.string.ob_btn_get_started) else stringResource(R.string.ob_btn_next),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionRequestCard(permission: PermissionInfo, onAllowClick: () -> Unit, onDenyClick: () -> Unit, onRequestDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalPremiumHaptics.current

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRequestDismiss)
    ) {
            Surface(
                modifier = modifier.fillMaxWidth(0.92f).align(Alignment.BottomCenter).padding(bottom = MeshifyDesignSystem.Spacing.Xl),
                shape = MeshifyDesignSystem.Shapes.Dialog, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (permission.iconType) {
                                PermissionIconType.Wifi -> Icons.Filled.Wifi
                                PermissionIconType.Bluetooth -> Icons.AutoMirrored.Filled.BluetoothSearching
                                PermissionIconType.Notifications -> Icons.Filled.Notifications
                                PermissionIconType.Location -> Icons.Filled.LocationOn
                            },
                            contentDescription = null, modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XXL), tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                    Text(text = stringResource(permission.labelRes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                    InfoSection(titleRes = R.string.ob_card_why_title, pointsRes = permission.whatHappensRes, iconTint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))
                    InfoSection(titleRes = R.string.ob_card_deny_title, pointsRes = permission.ifDenyRes, iconTint = MaterialTheme.colorScheme.error)

                    Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)) {
                        Surface(
                            onClick = { haptics.perform(HapticPattern.Tick); onDenyClick() }, modifier = Modifier.weight(1f).height(56.dp),
                            shape = MeshifyDesignSystem.Shapes.Button, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.ob_card_deny), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }

                        Surface(
                            onClick = { haptics.perform(HapticPattern.Pop); onAllowClick() }, modifier = Modifier.weight(1f).height(56.dp),
                            shape = MeshifyDesignSystem.Shapes.Button, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.ob_card_allow), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun InfoSection(titleRes: Int, pointsRes: List<Int>, iconTint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MeshifyDesignSystem.Shapes.CardSmall, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = iconTint)
            Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xs))
            pointsRes.forEach { pointRes ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconTint)
                    Text(text = stringResource(pointRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PermissionResultCard(permission: PermissionInfo, result: PermissionRequestResult, modifier: Modifier = Modifier) {
    val (icon, iconTint, statusText) = when (result) {
        PermissionRequestResult.Granted -> Triple(Icons.Default.Check, StatusOnline, R.string.ob_perm_granted)
        PermissionRequestResult.Denied -> Triple(Icons.Default.Close, MaterialTheme.colorScheme.error, R.string.ob_perm_denied)
        PermissionRequestResult.DeniedPermanently -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, R.string.ob_perm_denied_permanent)
    }

    Surface(modifier = modifier.fillMaxWidth(0.92f), shape = MeshifyDesignSystem.Shapes.Dialog, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 4.dp) {
        Row(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Lg)) {
            Box(modifier = Modifier.size(64.dp).background(iconTint.copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(MeshifyDesignSystem.IconSizes.XXL), tint = iconTint)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(permission.labelRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = stringResource(statusText), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = iconTint)
            }
        }
    }
}

@Composable
fun PermissionSummaryDialog(grantedCount: Int, totalCount: Int, permissionResults: Map<String, PermissionRequestResult>, onStartClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalPremiumHaptics.current
    val allGranted = grantedCount == totalCount

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(modifier = modifier.fillMaxWidth(0.92f), shape = MeshifyDesignSystem.Shapes.Dialog, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 12.dp) {
            Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(88.dp).background(color = (if (allGranted) StatusOnline else MaterialTheme.colorScheme.error).copy(alpha = 0.1f), shape = MeshifyDesignSystem.Shapes.IconContainer), contentAlignment = Alignment.Center) {
                    Icon(imageVector = if (allGranted) Icons.Default.Check else Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = if (allGranted) StatusOnline else MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))
                Text(text = stringResource(R.string.ob_summary_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))
                Text(text = stringResource(R.string.ob_summary_desc), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

                Surface(shape = MeshifyDesignSystem.Shapes.CardSmall, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!allGranted) {
                            Text(text = stringResource(R.string.ob_summary_count, grantedCount, totalCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = stringResource(R.string.ob_summary_partial), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        } else {
                            Text(text = stringResource(R.string.ob_summary_all_granted), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusOnline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

                Surface(
                    onClick = { haptics.perform(HapticPattern.Success); onStartClick() }, modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MeshifyDesignSystem.Shapes.Button, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.ob_summary_start), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

object PermissionDefinitions {
    fun getPermissions(): List<PermissionInfo> {
        val permissions = mutableListOf<PermissionInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(PermissionInfo(id = "nearby_wifi", iconType = PermissionIconType.Wifi, labelRes = R.string.ob_perm_label_nearby, importanceLabelRes = R.string.ob_perm_required, isRequired = true,
                whatHappensRes = listOf(R.string.ob_perm_nearby_why_1, R.string.ob_perm_nearby_why_2),
                ifDenyRes = listOf(R.string.ob_perm_nearby_deny_1, R.string.ob_perm_nearby_deny_2),
                androidPermissions = listOf(Manifest.permission.NEARBY_WIFI_DEVICES)))
        } else {
            permissions.add(PermissionInfo(id = "location", iconType = PermissionIconType.Location, labelRes = R.string.ob_perm_label_nearby, importanceLabelRes = R.string.ob_perm_required, isRequired = true,
                whatHappensRes = listOf(R.string.ob_perm_loc_why_1, R.string.ob_perm_loc_why_2),
                ifDenyRes = listOf(R.string.ob_perm_loc_deny_1, R.string.ob_perm_loc_deny_2),
                androidPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)))
        }

        permissions.add(PermissionInfo(id = "bluetooth", iconType = PermissionIconType.Bluetooth, labelRes = R.string.ob_perm_label_bt, importanceLabelRes = R.string.ob_perm_optional, isRequired = false,
            whatHappensRes = listOf(R.string.ob_perm_bt_why_1, R.string.ob_perm_bt_why_2),
            ifDenyRes = listOf(R.string.ob_perm_bt_deny_1, R.string.ob_perm_bt_deny_2),
            androidPermissions = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(PermissionInfo(id = "notifications", iconType = PermissionIconType.Notifications, labelRes = R.string.ob_perm_label_notif, importanceLabelRes = R.string.ob_perm_optional, isRequired = false,
                whatHappensRes = listOf(R.string.ob_perm_notif_why_1, R.string.ob_perm_notif_why_2),
                ifDenyRes = listOf(R.string.ob_perm_notif_deny_1, R.string.ob_perm_notif_deny_2),
                androidPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS)))
        }

        return permissions
    }
}
