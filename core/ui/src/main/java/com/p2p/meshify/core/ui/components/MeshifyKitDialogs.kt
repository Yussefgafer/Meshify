package com.p2p.meshify.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.ColorPresetAmber
import com.p2p.meshify.core.ui.theme.ColorPresetBlue
import com.p2p.meshify.core.ui.theme.ColorPresetGreen
import com.p2p.meshify.core.ui.theme.ColorPresetNeutral
import com.p2p.meshify.core.ui.theme.ColorPresetPink
import com.p2p.meshify.core.ui.theme.ColorPresetPurple
import com.p2p.meshify.core.ui.theme.ColorPresetRed
import com.p2p.meshify.core.ui.theme.ColorPresetTeal
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.domain.model.ShapeStyle
import com.p2p.meshify.domain.repository.ThemeMode
import java.io.File

@Composable
fun DeleteConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_btn_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun FullImageViewer(imagePath: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(imagePath))
                .crossfade(true)
                .build(),
            contentDescription = "Full Image",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Simple text input dialog for editing display name.
 */
@Composable
fun MeshifyTextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = ""
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MeshifyDesignSystem.Shapes.Input.topStart),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(R.string.dialog_btn_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Selection dialog for choosing from a list of options with icons.
 */
@Composable
fun <T> MeshifySelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: (T) -> String,
    optionIcon: (T) -> ImageVector? = { null }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onOptionSelected(option); onDismiss() }
                            )
                            .padding(vertical = MeshifyDesignSystem.Spacing.Xs),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            Color.Transparent,
                        shape = RoundedCornerShape(MeshifyDesignSystem.Spacing.Md)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MeshifyDesignSystem.Spacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
                        ) {
                            optionIcon(option)?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(R.string.dialog_option_desc),
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.dialog_selected),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Bottom sheet for theme selection with radio buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionBottomSheet(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        ThemeMode.SYSTEM to "System Default",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = MeshifyDesignSystem.Spacing.Md)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Spacer(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshifyDesignSystem.Spacing.Md)
        ) {
            Text(
                text = stringResource(R.string.settings_dialog_choose_theme),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Lg)
            )
            themes.forEach { (theme, label) ->
                val isSelected = theme == currentTheme
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            onClick = { onThemeSelected(theme); onDismiss() }
                        )
                        .padding(vertical = MeshifyDesignSystem.Spacing.Xs),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        Color.Transparent,
                    shape = RoundedCornerShape(MeshifyDesignSystem.Spacing.Md)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeshifyDesignSystem.Spacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Md)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onThemeSelected(theme); onDismiss() }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
        }
    }
}

/**
 * Enhanced color picker grid for seed color selection.
 */
@Composable
fun SeedColorPickerGrid(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        ColorPresetTeal,
        ColorPresetPurple,
        ColorPresetGreen,
        ColorPresetRed,
        ColorPresetAmber,
        ColorPresetBlue,
        ColorPresetPink,
        ColorPresetNeutral
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            )
                        } else {
                            Modifier.border(
                                1.dp,
                                color.copy(0.3f),
                                CircleShape
                            )
                        }
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
