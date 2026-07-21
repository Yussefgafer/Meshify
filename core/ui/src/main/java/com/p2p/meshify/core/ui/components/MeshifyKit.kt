package com.p2p.meshify.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.p2p.meshify.core.ui.theme.StatusOnline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.core.ui.R
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.util.FileUtils
import java.io.File

@Composable
fun MeshifyAvatar(
    initials: String,
    avatarHash: String? = null,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanInitials = initials.filter { it.isLetterOrDigit() }.take(2).uppercase()
    val avatarFile = remember(avatarHash) {
        avatarHash?.let { hash ->
            FileUtils.getFilePath(context, hash, "avatars")?.let { File(it) }
        }
    }

    Box(modifier = modifier.size(size)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MeshifyDesignSystem.Shapes.Avatar,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            if (avatarFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarFile).crossfade(true).build(),
                    contentDescription = stringResource(R.string.avatar_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = cleanInitials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MeshifyAvatarWithOnline(
    initials: String,
    avatarHash: String? = null,
    isOnline: Boolean = false,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        MeshifyAvatar(initials = initials, avatarHash = avatarHash, size = size)
        if (isOnline) {
            Box(
                Modifier
                    .size(size / 4.5f)
                    .align(Alignment.BottomEnd)
                    .background(StatusOnline)
                    .border(2.dp, MaterialTheme.colorScheme.surface)
            )
        }
    }
}



