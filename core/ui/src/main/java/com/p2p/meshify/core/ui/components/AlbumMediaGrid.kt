package com.p2p.meshify.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.domain.model.MessageType
import java.io.File

/**
 * Album-style media grid for grouped message attachments.
 * Layout logic:
 * - 1 image: Full size
 * - 2-4 images: 2x2 Grid
 * - 5+ images: 3x3 Grid (Telegram/WhatsApp style)
 */
@Composable
fun AlbumMediaGrid(
    attachments: List<MessageAttachmentEntity>,
    caption: String?,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Caption (if exists)
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Divider between caption and media
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // Media Grid
        val columns = when {
            attachments.size == 1 -> 1
            attachments.size <= 4 -> 2
            else -> 3
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(attachments.size) { index ->
                val attachment = attachments[index]
                AlbumMediaItem(
                    attachment = attachment,
                    onClick = onImageClick,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

/**
 * Individual media item in the album grid.
 * Shows play icon overlay for videos.
 */
@Composable
private fun AlbumMediaItem(
    attachment: MessageAttachmentEntity,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFullImage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .clickable { onClick(attachment.filePath) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(attachment.filePath))
                .crossfade(true)
                .build(),
            contentDescription = "Attachment",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Play icon overlay for videos
        if (attachment.type == MessageType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }

    // Full image viewer dialog
    selectedFullImage?.let { path ->
        FullImageViewer(imagePath = path) { selectedFullImage = null }
    }
}
