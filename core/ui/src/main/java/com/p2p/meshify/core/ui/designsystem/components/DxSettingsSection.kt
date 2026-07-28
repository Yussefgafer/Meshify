package com.p2p.meshify.core.ui.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2p.meshify.core.ui.designsystem.foundation.DxSpacing

/**
 * Settings section with an UPPERCASE section header and a card-wrapped body.
 *
 * Follows the Koda settings grammar:
 * SettingsSection(title) wrapping a Surface card (24dp, surfaceContainer, 2dp tonalElevation).
 *
 * Usage:
 * ```
 * DxSettingsSection(title = "Appearance") {
 *     DxSettingsItem(...)
 *     DxSettingsDivider()
 *     DxSwitchSettingItem(...)
 * }
 * ```
 */
@Composable
fun DxSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        // Section header — uppercase 12sp SemiBold with letter spacing
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = DxSpacing.Sm, bottom = DxSpacing.Sm)
        )

        // Expressive card wrapper — 24dp rounded, tonal elevation, 6dp inner padding
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = DxSpacing.Xs,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(DxSpacing.Xs)) {
                content()
            }
        }
    }
}
