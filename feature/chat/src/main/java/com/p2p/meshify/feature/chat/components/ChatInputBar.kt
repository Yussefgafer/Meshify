package com.p2p.meshify.feature.chat.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import com.p2p.meshify.core.ui.components.MediaStagingChatInput
import com.p2p.meshify.core.ui.components.StagedMediaRow
import com.p2p.meshify.core.ui.model.StagedAttachment
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.MessageType

/**
 * Chat input bar with media staging, attachment buttons, and text input.
 * Handles image/video/file picker launchers and staged media display.
 */
@Composable
fun ChatInputBar(
    textState: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    stagedAttachments: List<StagedAttachment>,
    onRemoveAttachment: (Uri) -> Unit,
    onStageAttachment: (Uri, ByteArray, MessageType) -> Unit,
    isSending: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    /** Reads bytes from a content URI safely via streaming copy that aborts over the size limit. */
    fun readUriBytes(uri: Uri): ByteArray? {
        return try {
            val maxBytes = AppConstants.MAX_FILE_SIZE_BYTES
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                val out = java.io.ByteArrayOutputStream()
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > maxBytes) {
                        Logger.e("ChatInputBar -> File too large: > $maxBytes bytes")
                        return null
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: SecurityException) {
            Logger.e("ChatInputBar -> SecurityException reading URI: $uri", e)
            null
        } catch (e: java.io.FileNotFoundException) {
            Logger.e("ChatInputBar -> File not found: $uri", e)
            null
        } catch (e: Exception) {
            Logger.e("ChatInputBar -> Failed to read URI: $uri", e)
            null
        }
    }

    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = readUriBytes(it)
            if (bytes != null) {
                onStageAttachment(it, bytes, MessageType.IMAGE)
            }
        }
    }

    // Video picker launcher
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = readUriBytes(it)
            if (bytes != null) {
                onStageAttachment(it, bytes, MessageType.VIDEO)
            }
        }
    }

    // Generic file picker launcher
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = readUriBytes(it)
            if (bytes != null) {
                onStageAttachment(it, bytes, MessageType.FILE)
            }
        }
    }

    Column(Modifier.navigationBarsPadding()) {
        // Reply indicator is handled by parent (passed separately)

        // Staged media row with animation
        AnimatedVisibility(
            visible = stagedAttachments.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            StagedMediaRow(
                attachments = stagedAttachments,
                onRemoveClick = onRemoveAttachment
            )
        }

        // Chat input
        MediaStagingChatInput(
            textState = textState,
            onTextChange = onTextChange,
            onSendClick = onSendClick,
            onGalleryClick = { imageLauncher.launch("image/*") },
            onVideoClick = { videoLauncher.launch("video/*") },
            onFileClick = { fileLauncher.launch("*/*") },
            hasAttachments = stagedAttachments.isNotEmpty(),
            isSending = isSending
        )
    }
}
