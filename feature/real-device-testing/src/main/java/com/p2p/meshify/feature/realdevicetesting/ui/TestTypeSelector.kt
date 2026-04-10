package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestTypeSelector(
    scenarios: List<TestScenario>,
    selectedScenarioIds: Set<String>,
    enabled: Boolean,
    onToggleScenario: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md)) {
            Text(
                text = stringResource(R.string.test_selection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs),
                verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs)
            ) {
                scenarios.forEach { scenario ->
                    ScenarioChip(
                        scenario = scenario,
                        isSelected = scenario.id in selectedScenarioIds,
                        enabled = enabled,
                        onToggle = { onToggleScenario(scenario.id) }
                    )
                }
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSelectAll,
                    enabled = enabled && selectedScenarioIds.size < scenarios.size
                ) {
                    Text(text = stringResource(R.string.screen_select_all))
                }
                Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
                TextButton(
                    onClick = onDeselectAll,
                    enabled = enabled && selectedScenarioIds.isNotEmpty()
                ) {
                    Text(text = stringResource(R.string.screen_deselect_all))
                }
            }
        }
    }
}

@Composable
private fun ScenarioChip(
    scenario: TestScenario,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onToggle,
        enabled = enabled,
        leadingIcon = {
            Icon(
                imageVector = scenarioIconFor(scenario.icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = stringResource(scenario.titleRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    )
}

@Composable
private fun scenarioIconFor(icon: String): ImageVector = when (icon) {
    "search" -> Icons.Default.Search
    "signal" -> Icons.Default.SignalCellularAlt
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "attachment" -> Icons.Default.Attachment
    "timer" -> Icons.Default.Timer
    "sync" -> Icons.Filled.Sync
    else -> Icons.Default.Search
}
