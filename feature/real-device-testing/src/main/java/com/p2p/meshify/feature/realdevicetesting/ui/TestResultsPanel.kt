package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus

private val RESULTS_LIST_MAX_HEIGHT = 400.dp

@Composable
fun TestResultsPanel(
    scenarios: List<TestScenario>,
    results: Map<String, TestResult>,
    isExporting: Boolean,
    isCleaning: Boolean,
    exportSuccess: Boolean?,
    cleanupSuccess: Boolean?,
    cleanupError: String? = null,
    onExportLog: () -> Unit,
    onCleanupData: () -> Unit,
    onRerunFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val passedCount = results.count { (_, r) -> r.status == TestStatus.PASSED }
    val failedCount = results.count { (_, r) -> r.status == TestStatus.FAILED || r.status == TestStatus.TIMEOUT }
    val totalCount = results.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.test_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MeshifyDesignSystem.Shapes.Pill,
                    color = if (failedCount == 0) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = "$passedCount/$totalCount",
                        modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Xs, vertical = MeshifyDesignSystem.Spacing.Xxs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (failedCount == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            // Results list
            LazyColumn(
                modifier = Modifier.heightIn(max = RESULTS_LIST_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    val result = results[scenario.id]
                    if (result != null) {
                        ResultItem(scenario, result)
                    }
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            // Export status
            exportSuccess?.let { success ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MeshifyDesignSystem.Shapes.CardSmall,
                    color = if (success) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = if (success) stringResource(R.string.screen_log_exported) else stringResource(R.string.screen_export_failed),
                        modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
            }

            // Cleanup status
            cleanupSuccess?.let { success ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MeshifyDesignSystem.Shapes.CardSmall,
                    color = if (success) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = if (success) stringResource(R.string.cleanup_success) else stringResource(R.string.cleanup_failure, cleanupError ?: "Unknown"),
                        modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
            ) {
                OutlinedButton(
                    onClick = onExportLog,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                    }
                    Text(text = stringResource(R.string.test_results_export))
                }

                if (failedCount > 0) {
                    OutlinedButton(
                        onClick = onRerunFailed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                        Text(text = stringResource(R.string.screen_rerun_failed, failedCount))
                    }
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))

            TextButton(
                onClick = onCleanupData,
                enabled = !isCleaning,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isCleaning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                } else {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                }
                Text(text = stringResource(R.string.test_results_cleanup))
            }
        }
    }
}

@Composable
private fun ResultItem(
    scenario: TestScenario,
    result: TestResult,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(result.error != null) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MeshifyDesignSystem.Motion.expressiveSpring(),
        label = "result_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MeshifyDesignSystem.Motion.expressiveSpring()),
        shape = MeshifyDesignSystem.Shapes.CardSmall,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StatusIcon(result.status)
                    Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                    Text(
                        text = stringResource(scenario.titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    result.durationMs?.let { duration ->
                        Text(
                            text = "${duration}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                    }
                    if (result.details.isNotEmpty() || result.error != null) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = if (expanded) stringResource(R.string.screen_collapse) else stringResource(R.string.screen_expand),
                                modifier = Modifier.rotate(rotation),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xxs))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MeshifyDesignSystem.Shapes.CardSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = result.error ?: result.details,
                        modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Sm),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = when (result.status) {
                            TestStatus.FAILED -> MaterialTheme.colorScheme.error
                            TestStatus.TIMEOUT -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
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
        TestStatus.RUNNING -> Icons.Default.Timer to MaterialTheme.colorScheme.primary
        TestStatus.PENDING -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(18.dp),
        tint = color
    )
}
