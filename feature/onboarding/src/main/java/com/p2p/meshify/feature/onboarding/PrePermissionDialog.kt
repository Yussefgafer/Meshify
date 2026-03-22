package com.p2p.meshify.feature.onboarding

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.theme.StatusOnline
import kotlinx.coroutines.launch

/**
 * Pre-permission dialog flow.
 * Shows a sequence of dialogs explaining each permission before requesting it.
 */
@Composable
fun PrePermissionDialog(
    currentPermission: PermissionInfo?,
    onAllowClick: () -> Unit,
    onDenyClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current
    val scope = rememberCoroutineScope()
    var isAnimating by remember { mutableStateOf(false) }

    // Double-tap protection
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceDuration = 500L

    if (currentPermission == null) return

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
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Icon
                val infiniteTransition = rememberInfiniteTransition(label = "icon_transition")
                val iconScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "icon_scale_animation"
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(iconScale)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentPermission.icon,
                        contentDescription = stringResource(R.string.permission_icon_desc),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = currentPermission.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = currentPermission.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // What happens section
                PermissionInfoSection(
                    title = "What happens:",
                    points = currentPermission.whatHappens,
                    iconTint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // If you deny section
                PermissionInfoSection(
                    title = "If you deny:",
                    points = currentPermission.ifDeny,
                    iconTint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deny Button
                    TextButton(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < debounceDuration || isAnimating) return@TextButton
                            lastClickTime = currentTime
                            isAnimating = true

                            scope.launch {
                                haptics.perform(HapticPattern.Tick)
                                kotlinx.coroutines.delay(200)
                                onDenyClick()
                                isAnimating = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Deny ${currentPermission.title}" }
                    ) {
                        Text(
                            text = "Deny",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Allow Button
                    Button(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < debounceDuration || isAnimating) return@Button
                            lastClickTime = currentTime
                            isAnimating = true

                            scope.launch {
                                haptics.perform(HapticPattern.Pop)
                                kotlinx.coroutines.delay(200)
                                onAllowClick()
                                isAnimating = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Allow ${currentPermission.title}" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MeshifyDesignSystem.Shapes.Button
                    ) {
                        Text(
                            text = "Allow",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info section showing bullet points.
 */
@Composable
private fun PermissionInfoSection(
    title: String,
    points: List<String>,
    iconTint: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = iconTint
        )

        Spacer(modifier = Modifier.height(8.dp))

        points.forEach { point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.permission_check),
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Summary dialog shown after all permissions are processed.
 */
@Composable
fun PermissionSummaryDialog(
    grantedCount: Int,
    totalCount: Int,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current
    val scope = rememberCoroutineScope()
    var isAnimating by remember { mutableStateOf(false) }

    // Double-tap protection
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceDuration = 500L

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = StatusOnline.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.permission_all_set),
                        modifier = Modifier.size(56.dp),
                        tint = StatusOnline
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = "You're All Set!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = "Meshify is ready to use. Start discovering nearby devices and send messages!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Summary
                val hasPartialPermissions = grantedCount < totalCount
                if (hasPartialPermissions) {
                    Text(
                        text = "Permissions granted: $grantedCount/$totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Some features may be limited.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "All permissions granted: $grantedCount/$totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusOnline,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Start Button
                Button(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < debounceDuration || isAnimating) return@Button
                        lastClickTime = currentTime
                        isAnimating = true

                        scope.launch {
                            haptics.perform(HapticPattern.Success)
                            kotlinx.coroutines.delay(200)
                            onStartClick()
                            isAnimating = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Start Messaging" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MeshifyDesignSystem.Shapes.Button
                ) {
                    Text(
                        text = "Start Messaging",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Permission info data class.
 */
data class PermissionInfo(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val whatHappens: List<String>,
    val ifDeny: List<String>
)

/**
 * All permission definitions.
 */
object PermissionDefinitions {
    
    fun getPermissions(): List<PermissionInfo> {
        val permissions = mutableListOf<PermissionInfo>()

        // 1. Bluetooth
        permissions.add(
            PermissionInfo(
                id = "bluetooth",
                icon = Icons.Default.BluetoothSearching,
                title = "Bluetooth Permission",
                description = "We use Bluetooth to discover nearby devices and establish connections.",
                whatHappens = listOf(
                    "Scan for nearby Meshify users",
                    "Create secure connections"
                ),
                ifDeny = listOf(
                    "Cannot discover new devices",
                    "Cannot connect via Bluetooth"
                )
            )
        )

        // 2. Location (Android < 13) OR Nearby WiFi (Android 13+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionInfo(
                    id = "location",
                    icon = Icons.Default.LocationOn,
                    title = "Location Permission",
                    description = "Location access is required for WiFi Direct discovery on older Android versions.",
                    whatHappens = listOf(
                        "Enable mDNS peer discovery",
                        "Find devices on same network"
                    ),
                    ifDeny = listOf(
                        "Cannot discover peers via LAN",
                        "App works in offline mode only"
                    )
                )
            )
        } else {
            permissions.add(
                PermissionInfo(
                    id = "nearby_wifi",
                    icon = Icons.Default.Wifi,
                    title = "Nearby WiFi Devices",
                    description = "This permission allows direct device-to-device connections without internet.",
                    whatHappens = listOf(
                        "Connect to nearby Meshify users",
                        "Transfer messages via WiFi"
                    ),
                    ifDeny = listOf(
                        "Cannot use WiFi Direct",
                        "Limited to Bluetooth only"
                    )
                )
            )
        }

        // 3. Notifications
        permissions.add(
            PermissionInfo(
                id = "notifications",
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Get notified when you receive new messages from nearby devices.",
                whatHappens = listOf(
                    "Show message alerts",
                    "Notify about new connections"
                ),
                ifDeny = listOf(
                    "No message notifications",
                    "Must open app to see messages"
                )
            )
        )

        // 4. Storage/Photos
        permissions.add(
            PermissionInfo(
                id = "storage",
                icon = Icons.Default.Folder,
                title = "Photos & Files",
                description = "Access photos and files to send them to nearby devices.",
                whatHappens = listOf(
                    "Select images to send",
                    "Share files with peers"
                ),
                ifDeny = listOf(
                    "Cannot send images",
                    "Cannot share files",
                    "Text messages still work"
                )
            )
        )

        return permissions
    }
}
