package com.p2p.meshify.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.theme.MeshifyPrimary
import com.p2p.meshify.core.ui.theme.StatusOnline

// ============================================================
// PAGE 1: WELCOME
// ============================================================

@Composable
fun WelcomePage(
    onLangMenuToggle: () -> Unit,
    isLangMenuOpen: Boolean,
    currentLang: String,
    onLangSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        // Illustration
        OnboardingIllustration(
            illustrationType = IllustrationType.MeshNetwork,
            modifier = Modifier.size(220.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        // Title + Description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
        ) {
            Text(
                text = stringResource(R.string.ob_welcome_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.ob_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
            )
        }

        // Language Selector
        LanguageSelector(
            onLangMenuToggle = onLangMenuToggle,
            currentLang = currentLang,
            onLangSelected = onLangSelected
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xxl))
    }
}

@Composable
private fun LanguageSelector(
    onLangMenuToggle: () -> Unit,
    currentLang: String,
    onLangSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.ob_language_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Xs)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = currentLang == "en",
                onClick = { if (currentLang != "en") onLangSelected("en") },
                label = { Text(stringResource(R.string.ob_lang_en)) },
                leadingIcon = {
                    if (currentLang == "en") {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                shape = MeshifyDesignSystem.Shapes.Pill
            )

            FilterChip(
                selected = currentLang == "ar",
                onClick = { if (currentLang != "ar") onLangSelected("ar") },
                label = { Text(stringResource(R.string.ob_lang_ar)) },
                leadingIcon = {
                    if (currentLang == "ar") {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                shape = MeshifyDesignSystem.Shapes.Pill
            )
        }
    }
}

// ============================================================
// PAGE 2: HOW IT WORKS
// ============================================================

@Composable
fun HowItWorksPage(modifier: Modifier = Modifier) {
    val steps = listOf(
        HowItWorksStep(
            titleRes = R.string.ob_step_discover_title,
            descRes = R.string.ob_step_discover_desc,
            illustrationType = IllustrationType.DiscoveryScreen
        ),
        HowItWorksStep(
            titleRes = R.string.ob_step_connect_title,
            descRes = R.string.ob_step_connect_desc,
            illustrationType = IllustrationType.ConnectScreen
        ),
        HowItWorksStep(
            titleRes = R.string.ob_step_chat_title,
            descRes = R.string.ob_step_chat_desc,
            illustrationType = IllustrationType.ChatScreen
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeshifyDesignSystem.Spacing.Xl)
    ) {
        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        // Page title
        Text(
            text = stringResource(R.string.ob_how_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xl))

        // Vertical steps
        steps.forEachIndexed { index, step ->
            StepCard(
                stepNumber = index + 1,
                titleRes = step.titleRes,
                descRes = step.descRes,
                illustrationType = step.illustrationType
            )
            if (index < steps.size - 1) {
                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))
            }
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    titleRes: Int,
    descRes: Int,
    illustrationType: IllustrationType,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
        ) {
            // Step number + illustration
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                OnboardingIllustration(
                    illustrationType = illustrationType,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Title + description
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MeshifyDesignSystem.Spacing.Xs),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Xxs))
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                )
            }
        }
    }
}

// ============================================================
// PAGE 3: PERMISSIONS OVERVIEW
// ============================================================

@Composable
fun PermissionsOverviewPage(
    permissions: List<PermissionInfo>,
    permissionStatuses: Map<String, PermissionStatus>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        // Illustration
        OnboardingIllustration(
            illustrationType = IllustrationType.ShieldCheck,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))

        // Title + Description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
        ) {
            Text(
                text = stringResource(R.string.ob_perm_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.ob_perm_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Permission list
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MeshifyDesignSystem.Shapes.CardLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
            ) {
                permissions.forEach { perm ->
                    val status = permissionStatuses[perm.id] ?: PermissionStatus.NotAsked
                    PermissionRow(
                        iconType = perm.iconType,
                        labelRes = perm.labelRes,
                        importanceLabelRes = perm.importanceLabelRes,
                        status = status
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))
    }
}

@Composable
private fun PermissionRow(
    iconType: PermissionIconType,
    labelRes: Int,
    importanceLabelRes: Int,
    status: PermissionStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MeshifyDesignSystem.Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
        ) {
            // Icon
            Icon(
                imageVector = when (iconType) {
                    PermissionIconType.Wifi -> Icons.Filled.Wifi
                    PermissionIconType.Bluetooth -> Icons.AutoMirrored.Filled.BluetoothSearching
                    PermissionIconType.Notifications -> Icons.Filled.Notifications
                    PermissionIconType.Location -> Icons.Filled.LocationOn
                },
                contentDescription = null,
                modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Large),
                tint = MaterialTheme.colorScheme.primary
            )

            Column {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(importanceLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status badge
        StatusBadge(status = status)
    }
}

@Composable
private fun StatusBadge(status: PermissionStatus, modifier: Modifier = Modifier) {
    val (textRes, badgeColor) = when (status) {
        PermissionStatus.NotAsked -> R.string.ob_perm_not_asked to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        PermissionStatus.Granted -> R.string.ob_perm_granted to StatusOnline
        PermissionStatus.Denied -> R.string.ob_perm_denied to MaterialTheme.colorScheme.error
        PermissionStatus.DeniedPermanently -> R.string.ob_perm_denied_permanent to MaterialTheme.colorScheme.error
        PermissionStatus.Skipped -> R.string.ob_perm_skipped to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        PermissionStatus.AlreadyGranted -> R.string.ob_perm_already_granted to StatusOnline
    }
    val bgColor = badgeColor.copy(alpha = 0.1f)

    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelMedium,
        color = badgeColor,
        modifier = modifier
            .clip(MeshifyDesignSystem.Shapes.Pill)
            .background(bgColor)
            .padding(horizontal = MeshifyDesignSystem.Spacing.Xs, vertical = MeshifyDesignSystem.Spacing.Xxs)
    )
}

// ============================================================
// SHARED: ILLUSTRATIONS
// ============================================================

@Composable
fun OnboardingIllustration(
    illustrationType: IllustrationType,
    modifier: Modifier = Modifier
) {
    when (illustrationType) {
        IllustrationType.MeshNetwork -> MeshNetworkIllustration(modifier)
        IllustrationType.DiscoveryScreen -> DiscoveryScreenIllustration(modifier)
        IllustrationType.ConnectScreen -> ConnectScreenIllustration(modifier)
        IllustrationType.ChatScreen -> ChatScreenIllustration(modifier)
        IllustrationType.ShieldCheck -> ShieldCheckIllustration(modifier)
    }
}

@Composable
private fun MeshNetworkIllustration(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    // Continuous 360° rotation — LinearEasing is intentional here
    // Spring physics would create unnatural oscillation for infinite rotation
    @Suppress("BanLinearEasing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

    Canvas(modifier = modifier.rotate(rotation)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 3
        val nodeCount = 6
        val nodes = List(nodeCount) { i ->
            val angle = (i * 360f / nodeCount) * (Math.PI / 180f).toFloat()
            Offset(center.x + radius * kotlin.math.cos(angle), center.y + radius * kotlin.math.sin(angle))
        }

        nodes.forEachIndexed { i, n1 ->
            nodes.drop(i + 1).forEach { n2 ->
                drawLine(MeshifyPrimary.copy(alpha = 0.2f), n1, n2, strokeWidth = 2.dp.toPx())
            }
        }
        nodes.forEach { node ->
            drawCircle(MeshifyPrimary, 8.dp.toPx(), node)
        }
        drawCircle(StatusOnline, 14.dp.toPx(), center)
    }
}

@Composable
private fun DiscoveryScreenIllustration(modifier: Modifier) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryAlpha = MeshifyPrimary.copy(alpha = 0.15f)
    val onSurfaceVariantAlpha = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val primaryAlpha3 = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Phone frame
        drawRoundRect(
            color = outlineVariant,
            size = Size(w, h),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Top bar
        drawRoundRect(
            color = primaryAlpha,
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.15f),
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        // Peer items
        val itemH = h * 0.12f
        val gap = 4.dp.toPx()
        for (i in 0 until 3) {
            val y = h * 0.2f + i * (itemH + gap)
            val alpha = 1f - i * 0.2f
            drawRoundRect(
                color = surfaceContainerHighest.copy(alpha = alpha),
                topLeft = Offset(8.dp.toPx(), y),
                size = Size(w - 16.dp.toPx(), itemH),
                cornerRadius = CornerRadius(12.dp.toPx())
            )
            // Avatar circle
            drawCircle(
                color = primaryAlpha3,
                radius = itemH * 0.35f,
                center = Offset(w * 0.15f, y + itemH / 2)
            )
            // Text lines
            drawRoundRect(
                color = onSurfaceVariantAlpha,
                topLeft = Offset(w * 0.3f, y + itemH * 0.25f),
                size = Size(w * 0.4f, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
private fun ConnectScreenIllustration(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "connect")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val phoneW = size.width * 0.22f
        val phoneH = size.height * 0.45f

        // Left phone
        drawRoundRect(
            color = MeshifyPrimary,
            topLeft = Offset(center.x - size.width * 0.35f - phoneW / 2, center.y - phoneH / 2),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(10.dp.toPx())
        )

        // Right phone
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(center.x + size.width * 0.35f - phoneW / 2, center.y - phoneH / 2),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(10.dp.toPx())
        )

        // Connection arc
        val startX = center.x - size.width * 0.35f + phoneW / 2
        val endX = center.x + size.width * 0.35f - phoneW / 2

        val path = Path().apply {
            moveTo(startX, center.y)
            quadraticTo(center.x, center.y - 30.dp.toPx(), endX, center.y)
        }
        drawPath(path, color = StatusOnline.copy(alpha = alpha), style = Stroke(width = 3.dp.toPx()))

        // Lock icon in center
        drawCircle(StatusOnline.copy(alpha = 0.2f), 16.dp.toPx(), center)
        drawCircle(StatusOnline, 8.dp.toPx(), center)
    }
}

@Composable
private fun ChatScreenIllustration(modifier: Modifier) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryAlpha8 = MeshifyPrimary.copy(alpha = 0.8f)
    val statusOnlineAlpha = StatusOnline.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Phone frame
        drawRoundRect(
            color = outlineVariant,
            size = Size(w, h),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Chat bubbles
        val bubbleW = w * 0.65f
        val bubbleH = h * 0.12f
        val gap = 6.dp.toPx()

        // Bubble 1 — right (sent)
        var y = h * 0.15f
        drawRoundRect(
            color = primaryAlpha8,
            topLeft = Offset(w - bubbleW - 8.dp.toPx(), y),
            size = Size(bubbleW, bubbleH),
            cornerRadius = CornerRadius(12.dp.toPx())
        )

        // Bubble 2 — left (received)
        y += bubbleH + gap
        drawRoundRect(
            color = surfaceContainerHighest,
            topLeft = Offset(8.dp.toPx(), y),
            size = Size(bubbleW * 0.8f, bubbleH),
            cornerRadius = CornerRadius(12.dp.toPx())
        )

        // Bubble 3 — right (sent)
        y += bubbleH + gap
        drawRoundRect(
            color = primaryAlpha8,
            topLeft = Offset(w - bubbleW * 0.7f - 8.dp.toPx(), y),
            size = Size(bubbleW * 0.7f, bubbleH * 0.8f),
            cornerRadius = CornerRadius(12.dp.toPx())
        )

        // Lock badge
        drawCircle(StatusOnline.copy(alpha = 0.3f), 10.dp.toPx(), Offset(w / 2, h * 0.88f))
    }
}

@Composable
private fun ShieldCheckIllustration(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val shieldW = size.width * 0.6f
        val shieldH = size.height * 0.7f

        // Shield
        val shieldPath = Path().apply {
            moveTo(center.x, center.y - shieldH / 2)
            lineTo(center.x + shieldW / 2, center.y - shieldH / 3)
            lineTo(center.x + shieldW / 2, center.y + shieldH / 3)
            lineTo(center.x, center.y + shieldH / 2)
            lineTo(center.x - shieldW / 2, center.y + shieldH / 3)
            lineTo(center.x - shieldW / 2, center.y - shieldH / 3)
            close()
        }
        drawPath(
            path = shieldPath,
            color = StatusOnline.copy(alpha = 0.2f),
            style = Stroke(width = 3.dp.toPx())
        )

        // Checkmark
        val checkSize = shieldW * 0.3f
        drawCircle(StatusOnline, checkSize / 2, center)
    }
}
