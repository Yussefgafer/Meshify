package com.p2p.meshify.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.theme.MeshifyPrimary
import com.p2p.meshify.core.ui.theme.StatusOnline
import kotlinx.coroutines.delay

/**
 * Reusable composable for a single onboarding page.
 * Displays illustration, title, subtitle, and description.
 */
@Composable
fun OnboardingPage(
    pageInfo: OnboardingPageInfo,
    modifier: Modifier = Modifier,
    onPrivacyPolicyClick: () -> Unit = {},
    onTermsClick: () -> Unit = {}
) {
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MeshifyDesignSystem.Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Illustration Section
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            OnboardingIllustration(
                illustrationType = pageInfo.illustrationType,
                modifier = Modifier.size(280.dp)
            )
        }
        
        // Text Content Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
        ) {
            // Title
            Text(
                text = stringResource(id = getPageTitleRes(pageInfo)),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            // Subtitle
            Text(
                text = stringResource(id = getPageSubtitleRes(pageInfo)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            // Description
            Text(
                text = stringResource(id = getPageDescriptionRes(pageInfo)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4
            )
            
            // Links (Privacy Policy & Terms)
            if (pageInfo.showLinks) {
                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))
                OnboardingLinks(
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onTermsClick = onTermsClick,
                    isRtl = isRtl
                )
            }
            
            // Get Started Button
            if (pageInfo.showGetStartedButton) {
                Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Lg))
                GetStartedButton(
                    onClick = { /* Handled by parent */ }
                )
            }
        }
    }
}

/**
 * Animated illustration for onboarding pages.
 * Uses simple geometric shapes as placeholders.
 */
@Composable
private fun OnboardingIllustration(
    illustrationType: IllustrationType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "illustration")
    
    // Animation specs
    val rotationSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 350f
    )
    
    val scaleSpec = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 300f
    )
    
    when (illustrationType) {
        is IllustrationType.MeshNetwork -> {
            MeshNetworkIllustration(
                modifier = modifier,
                rotationSpec = rotationSpec,
                infiniteTransition = infiniteTransition
            )
        }
        is IllustrationType.PrivacyShield -> {
            PrivacyShieldIllustration(
                modifier = modifier,
                scaleSpec = scaleSpec,
                infiniteTransition = infiniteTransition
            )
        }
        is IllustrationType.P2PDevices -> {
            P2PDevicesIllustration(
                modifier = modifier,
                rotationSpec = rotationSpec,
                infiniteTransition = infiniteTransition
            )
        }
        is IllustrationType.GetStarted -> {
            GetStartedIllustration(
                modifier = modifier,
                scaleSpec = scaleSpec,
                infiniteTransition = infiniteTransition
            )
        }
    }
}

/**
 * Mesh Network Illustration — Connected nodes in a network pattern.
 */
@Composable
private fun MeshNetworkIllustration(
    modifier: Modifier,
    rotationSpec: SpringSpec<Float>,
    infiniteTransition: InfiniteTransition
) {
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshRotation"
    )
    
    Canvas(modifier = modifier.rotate(rotation)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 3
        
        // Draw connection lines
        val nodeCount = 6
        val angles = List(nodeCount) { index ->
            (index * 360f / nodeCount) * (Math.PI / 180f).toFloat()
        }
        
        val nodes = angles.map { angle ->
            Offset(
                center.x + radius * kotlin.math.cos(angle),
                center.y + radius * kotlin.math.sin(angle)
            )
        }
        
        // Draw connections between all nodes
        nodes.forEachIndexed { i, node1 ->
            nodes.drop(i + 1).forEach { node2 ->
                drawLine(
                    color = MeshifyPrimary.copy(alpha = 0.3f),
                    start = node1,
                    end = node2,
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        // Draw nodes
        nodes.forEach { node ->
            drawCircle(
                color = MeshifyPrimary,
                radius = 12.dp.toPx(),
                center = node
            )
        }
        
        // Draw center node
        drawCircle(
            color = StatusOnline,
            radius = 20.dp.toPx(),
            center = center
        )
    }
}

/**
 * Privacy Shield Illustration — Shield with lock.
 */
@Composable
private fun PrivacyShieldIllustration(
    modifier: Modifier,
    scaleSpec: SpringSpec<Float>,
    infiniteTransition: InfiniteTransition
) {
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldScale"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val shieldWidth = size.width * 0.6f
        val shieldHeight = size.height * 0.7f
        
        // Draw shield outline
        val shieldPath = Path().apply {
            moveTo(center.x, center.y - shieldHeight / 2)
            lineTo(center.x + shieldWidth / 2, center.y - shieldHeight / 3)
            lineTo(center.x + shieldWidth / 2, center.y + shieldHeight / 3)
            lineTo(center.x, center.y + shieldHeight / 2)
            lineTo(center.x - shieldWidth / 2, center.y + shieldHeight / 3)
            lineTo(center.x - shieldWidth / 2, center.y - shieldHeight / 3)
            close()
        }
        
        drawPath(
            path = shieldPath,
            color = MeshifyPrimary,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Draw lock icon in center
        val lockSize = shieldWidth * 0.3f
        drawCircle(
            color = StatusOnline,
            radius = lockSize / 2,
            center = center
        )
    }
}

/**
 * P2P Devices Illustration — Connected devices.
 */
@Composable
private fun P2PDevicesIllustration(
    modifier: Modifier,
    rotationSpec: SpringSpec<Float>,
    infiniteTransition: InfiniteTransition
) {
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deviceRotation"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val deviceWidth = size.width * 0.25f
        val deviceHeight = size.height * 0.15f
        val spacing = size.width * 0.15f
        
        // Draw three devices
        val positions = listOf(
            Offset(center.x - spacing - deviceWidth / 2, center.y),
            Offset(center.x, center.y),
            Offset(center.x + spacing + deviceWidth / 2, center.y)
        )
        
        positions.forEachIndexed { index, pos ->
            // Device body
            drawRoundRect(
                color = if (index == 1) MeshifyPrimary else Color.Gray.copy(alpha = 0.5f),
                topLeft = Offset(pos.x - deviceWidth / 2, pos.y - deviceHeight / 2),
                size = Size(deviceWidth, deviceHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
            
            // Connection lines between devices
            if (index < positions.size - 1) {
                val nextPos = positions[index + 1]
                drawLine(
                    color = StatusOnline.copy(alpha = 0.6f),
                    start = Offset(pos.x + deviceWidth / 2, pos.y),
                    end = Offset(nextPos.x - deviceWidth / 2, nextPos.y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

/**
 * Get Started Illustration — Permission icons.
 */
@Composable
private fun GetStartedIllustration(
    modifier: Modifier,
    scaleSpec: SpringSpec<Float>,
    infiniteTransition: InfiniteTransition
) {
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "getStartedScale"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val iconRadius = size.minDimension / 4
        
        // Draw circle background
        drawCircle(
            color = MeshifyPrimary.copy(alpha = 0.1f),
            radius = iconRadius * 1.5f,
            center = center
        )
        
        // Draw permission icons (simplified as circles with icons)
        val iconPositions = listOf(
            Offset(center.x, center.y - iconRadius * 0.8f), // WiFi
            Offset(center.x - iconRadius * 0.7f, center.y + iconRadius * 0.4f), // Bluetooth
            Offset(center.x + iconRadius * 0.7f, center.y + iconRadius * 0.4f)  // Location
        )
        
        iconPositions.forEach { pos ->
            drawCircle(
                color = StatusOnline,
                radius = iconRadius * 0.35f,
                center = pos
            )
        }
    }
}

/**
 * Links section for Privacy Policy and Terms of Service.
 */
@Composable
private fun OnboardingLinks(
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    isRtl: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = onPrivacyPolicyClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(id = R.string.onboarding_privacy_policy_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        TextButton(
            onClick = onTermsClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(id = R.string.onboarding_terms_of_service_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Get Started button for the final onboarding page.
 */
@Composable
private fun GetStartedButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MeshifyDesignSystem.Shapes.Button
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_btn_get_started),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Helper functions to get string resource IDs from page info.
 */
private fun getPageTitleRes(pageInfo: OnboardingPageInfo): Int {
    return when (pageInfo.title) {
        "onboarding_welcome_title" -> R.string.onboarding_welcome_title
        "onboarding_privacy_title" -> R.string.onboarding_privacy_title
        "onboarding_p2p_title" -> R.string.onboarding_p2p_title
        "onboarding_get_started_title" -> R.string.onboarding_get_started_title
        else -> R.string.onboarding_welcome_title
    }
}

private fun getPageSubtitleRes(pageInfo: OnboardingPageInfo): Int {
    return when (pageInfo.subtitle) {
        "onboarding_welcome_subtitle" -> R.string.onboarding_welcome_subtitle
        "onboarding_privacy_subtitle" -> R.string.onboarding_privacy_subtitle
        "onboarding_p2p_subtitle" -> R.string.onboarding_p2p_subtitle
        "onboarding_get_started_subtitle" -> R.string.onboarding_get_started_subtitle
        else -> R.string.onboarding_welcome_subtitle
    }
}

private fun getPageDescriptionRes(pageInfo: OnboardingPageInfo): Int {
    return when (pageInfo.description) {
        "onboarding_welcome_desc" -> R.string.onboarding_welcome_desc
        "onboarding_privacy_desc" -> R.string.onboarding_privacy_desc
        "onboarding_p2p_desc" -> R.string.onboarding_p2p_desc
        "onboarding_get_started_desc" -> R.string.onboarding_get_started_desc
        else -> R.string.onboarding_welcome_desc
    }
}
