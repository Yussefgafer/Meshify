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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.components.MediaStagingChatInput
import com.p2p.meshify.core.ui.components.StagedMediaRow
import com.p2p.meshify.core.ui.model.StagedAttachment
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
    onStageAttachment: (Uri, MessageType) -> Unit,
    isSending: Boolean = false,
    isStaging: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onStageAttachment(it, MessageType.IMAGE) }
    }

    // Video picker launcher
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onStageAttachment(it, MessageType.VIDEO) }
    }

    // Generic file picker launcher
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onStageAttachment(it, MessageType.FILE) }
    }

    Column(Modifier.navigationBarsPadding()) {
        // Reply indicator is handled by parent (passed separately)

        // Attachment read progress while the ViewModel loads bytes off-main
        AnimatedVisibility(visible = isStaging) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

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
