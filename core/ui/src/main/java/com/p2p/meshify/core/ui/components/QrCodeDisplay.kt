package com.p2p.meshify.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem

/**
 * Displays a QR code for Out-Of-Band identity verification.
 *
 * This component renders the user's public key fingerprint as a QR code
 * that can be scanned by a peer to verify identity and prevent MITM attacks.
 *
 * @param qrData The QR code data string (JSON format with fingerprint)
 * @param title Display title above QR code
 * @param subtitle Optional subtitle below title
 * @param modifier Modifier for the root layout
 */
@Composable
fun QrCodeDisplay(
    qrData: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MeshifyDesignSystem.Spacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )

        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Xs)
            )
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Md))

        Surface(
            modifier = Modifier.size(240.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MeshifyDesignSystem.Shapes.CardMedium,
            tonalElevation = MeshifyDesignSystem.Elevation.Level2
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(MeshifyDesignSystem.Spacing.Sm))

        // Show fingerprint for manual verification
        Text(
            text = "Fingerprint:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = qrData.take(16) + "...",
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
