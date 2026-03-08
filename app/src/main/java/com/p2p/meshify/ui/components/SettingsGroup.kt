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
import com.p2p.meshify.ui.hooks.rememberPremiumHaptics

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
fun SettingGroupItem(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    position: ItemPosition = ItemPosition.MIDDLE,
    settingsRepository: ISettingsRepository,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberPremiumHaptics(settingsRepository)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )
    
    val shape = when (position) {
        ItemPosition.FIRST -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        ItemPosition.LAST -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        ItemPosition.ONLY -> RoundedCornerShape(28.dp)
        ItemPosition.MIDDLE -> RoundedCornerShape(4.dp)
    }
    
    Surface(
        onClick = {
            if (onClick != null) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
        },
        enabled = onClick != null,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (icon != null) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
    }
}
