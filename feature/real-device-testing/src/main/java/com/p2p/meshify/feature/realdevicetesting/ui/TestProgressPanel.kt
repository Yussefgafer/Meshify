package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus

@Composable
fun TestProgressPanel(
    scenarios: List<TestScenario>,
    currentIndex: Int,
    results: Map<String, TestResult>,
    targetPeerName: String,
    isCancelling: Boolean,
    progressFraction: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            // Header
            Text(
                text = stringResource(R.string.test_progress_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.screen_test_x_of_y, minOf(currentIndex + 1, scenarios.size), scenarios.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xxs))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            // Current test status
            if (currentIndex < scenarios.size) {
                val currentScenario = scenarios[currentIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(currentScenario.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.screen_connecting_to, targetPeerName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))
            }

            // Test list
            scenarios.forEachIndexed { index, scenario ->
                val result = results[scenario.id]
                val isCompleted = index < currentIndex
                val isCurrent = index == currentIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MeshifyDesignSystem.Spacing.Xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCompleted) {
                            result?.let {
                                StatusIcon(it.status)
                            }
                        } else if (isCurrent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                shape = MeshifyDesignSystem.Shapes.Pill
                            ) {}
                        }
                        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                        Text(
                            text = stringResource(scenario.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isCompleted -> MaterialTheme.colorScheme.onSurface
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    result?.durationMs?.let { duration ->
                        Text(
                            text = "${duration}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            // Cancel button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = onCancel,
                    enabled = !isCancelling
                ) {
                    Text(
                        text = if (isCancelling) stringResource(R.string.screen_cancelling) else stringResource(R.string.screen_cancel_tests),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: TestStatus) {
    val (icon, color) = when (status) {
        TestStatus.PASSED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        TestStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        TestStatus.TIMEOUT -> Icons.Default.Timer to MaterialTheme.colorScheme.tertiary
        TestStatus.RUNNING -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        TestStatus.PENDING -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(16.dp),
        tint = color
    )
}
