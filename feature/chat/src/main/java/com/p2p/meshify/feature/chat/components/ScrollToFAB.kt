package com.p2p.meshify.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.common.R

/**
 * Floating action button that appears when user scrolls away from bottom.
 * Scrolls the chat to the most recent message when clicked.
 *
 * The FAB is internally positioned at the bottom-end with proper padding and navigation bar insets.
 * Pass a parent modifier from the caller if additional positioning is needed (e.g., Box alignment).
 */
@Composable
fun ScrollToFAB(
    isVisible: Boolean,
    onScrollToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalPremiumHaptics.current

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .padding(MeshifyDesignSystem.Spacing.Md)
            .navigationBarsPadding()
    ) {
        FloatingActionButton(
            onClick = {
                haptics.perform(HapticPattern.Tick)
                onScrollToBottom()
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.content_desc_scroll_to_bottom),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
