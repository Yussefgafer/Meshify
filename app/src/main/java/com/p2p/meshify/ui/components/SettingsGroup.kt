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

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 16.dp), Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)), Arrangement.spacedBy(2.dp), content = content)
    }
}

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
