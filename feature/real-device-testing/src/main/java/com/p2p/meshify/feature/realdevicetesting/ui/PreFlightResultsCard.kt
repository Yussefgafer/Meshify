package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.preflight.CheckResult
import com.p2p.meshify.feature.realdevicetesting.preflight.CheckStatus
import com.p2p.meshify.feature.realdevicetesting.preflight.PreFlightResult

@Composable
fun PreFlightResultsCard(
    preFlightResult: PreFlightResult,
    isRunning: Boolean,
    elapsedMs: Long,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MeshifyDesignSystem.Motion.expressiveSpring(),
        label = "preflight_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MeshifyDesignSystem.Motion.expressiveSpring()),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRunning -> MaterialTheme.colorScheme.surfaceContainerLow
                preFlightResult.allPassed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Large),
                        tint = when {
                            isRunning -> MaterialTheme.colorScheme.primary
                            preFlightResult.allPassed -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                    Column {
                        Text(
                            text = stringResource(R.string.preflight_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isRunning -> "${stringResource(R.string.preflight_checking)} (${elapsedMs / 1000}s)"
                                preFlightResult.allPassed -> stringResource(R.string.preflight_all_pass)
                                else -> stringResource(
                                    R.string.preflight_some_fail,
                                    preFlightResult.failedChecks.size
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = if (expanded) stringResource(R.string.screen_collapse) else stringResource(R.string.screen_expand),
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

                // Permission checks
                preFlightResult.permissionResults.forEach { check ->
                    CheckResultRow(check)
                }

                // Connectivity check
                CheckResultRow(preFlightResult.connectivityResult)

                // Security check
                CheckResultRow(preFlightResult.securityResult)
            }
        }
    }
}

@Composable
private fun CheckResultRow(check: CheckResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshifyDesignSystem.Spacing.Xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (check.status) {
                CheckStatus.PASS -> Icons.Default.CheckCircle
                CheckStatus.FAIL -> Icons.Default.Error
                CheckStatus.SKIP -> Icons.Default.Warning
            },
            contentDescription = null,
            modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Medium),
            tint = when (check.status) {
                CheckStatus.PASS -> MaterialTheme.colorScheme.primary
                CheckStatus.FAIL -> MaterialTheme.colorScheme.error
                CheckStatus.SKIP -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.name,
                style = MaterialTheme.typography.bodyMedium,
                color = when (check.status) {
                    CheckStatus.FAIL -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
