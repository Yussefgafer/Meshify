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
import com.p2p.meshify.domain.model.MessageType

/**
 * Chat input bar with media staging, attachment buttons, and text input.
 * Handles image/video picker launchers and staged media display.
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

    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                onStageAttachment(it, bytes, MessageType.IMAGE)
            }
        }
    }

    // Video picker launcher
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                onStageAttachment(it, bytes, MessageType.VIDEO)
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
            hasAttachments = stagedAttachments.isNotEmpty(),
            isSending = isSending
        )
    }
}
