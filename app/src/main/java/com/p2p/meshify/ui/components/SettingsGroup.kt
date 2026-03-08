package com.p2p.meshify.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.ui.hooks.HapticPattern
import com.p2p.meshify.ui.hooks.LocalPremiumHaptics

/**
 * Displays a labeled settings group with a title header and a rounded container for child items.
 *
 * The title is rendered as a bold, primary-colored label above a clipped, rounded Column that
 * hosts the provided composable children with small vertical spacing.
 *
 * @param title The header text shown above the group.
 * @param content Composable children placed inside the group's rounded content area.
 */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 16.dp), Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)), Arrangement.spacedBy(2.dp), content = content)
    }
}

/**
 * Renders a row item for a settings group with optional leading icon, subtitle, and trailing content.
 *
 * Displays the title and optional subtitle, applies rounded corners according to `position`, scales while pressed, and becomes clickable when `onClick` is provided (invoking a short haptic feedback before calling `onClick`).
 *
 * @param title Primary label text shown for the item.
 * @param subtitle Optional secondary text shown beneath the title.
 * @param icon Optional composable displayed at the leading edge.
 * @param trailing Optional composable displayed at the trailing edge; if omitted and `onClick` is provided, a chevron icon is shown.
 * @param position Controls the item corner radii and visual grouping within a settings list (FIRST, MIDDLE, LAST, ONLY).
 * @param settingsRepository Settings repository dependency (provided for callers that supply repository-scoped objects).
 * @param onClick Optional click handler; when non-null the item is enabled and clickable.
 */
@Composable
fun SettingGroupItem(title: String, subtitle: String? = null, icon: (@Composable () -> Unit)? = null, trailing: @Composable (() -> Unit)? = null, position: ItemPosition = ItemPosition.MIDDLE, settingsRepository: ISettingsRepository, onClick: (() -> Unit)? = null) {
    val haptics = LocalPremiumHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(0.6f, 400f), label = "scale")
    val shape = when (position) {
        ItemPosition.FIRST -> RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp)
        ItemPosition.LAST -> RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp)
        ItemPosition.ONLY -> RoundedCornerShape(28.dp)
        ItemPosition.MIDDLE -> RoundedCornerShape(4.dp)
    }
    Surface(onClick = { if (onClick != null) { haptics.perform(HapticPattern.Pop); onClick() } }, enabled = onClick != null, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, interactionSource = interactionSource, modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) Box(Modifier.size(24.dp), Alignment.Center) { icon() }
            Column(Modifier.weight(1f).padding(end = 8.dp), Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}
