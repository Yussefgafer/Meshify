package com.p2p.meshify.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.*
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.ui.components.*
import com.p2p.meshify.ui.theme.LocalMeshifyThemeConfig
import com.p2p.meshify.ui.theme.StatusOnline
import com.p2p.meshify.ui.theme.getBubbleShape
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, peerId: String, peerName: String, onBackClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var selectedFullImage by remember { mutableStateOf<String?>(null) }
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MorphingAvatar(initials = peerName.take(1), isOnline = uiState.isOnline, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(peerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (uiState.isOnline) Color.Green else Color.Gray, CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text(if (uiState.isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelSmall, color = if (uiState.isOnline) StatusOnline else Color.Gray)
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            Column {
                uiState.replyTo?.let { reply ->
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(40.dp).background(MaterialTheme.colorScheme.primary))
                            Column(Modifier.padding(horizontal = 8.dp).weight(1f)) {
                                Text(if (reply.isFromMe) "You" else peerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(reply.text ?: "[Image]", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(onClick = { viewModel.setReplyTo(null) }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                }
                StandardChatInput(text = uiState.inputText, onTextChange = viewModel::onInputChanged, onSend = viewModel::sendMessage, onAttachClick = { })
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            itemsIndexed(uiState.messages, key = { _, m -> m.id }) { index, message ->
                MessageBubble(
                    message = message,
                    peerName = peerName,
                    onLongClick = { menuMessage = message },
                    onImageClick = { selectedFullImage = it },
                    onReactionClick = { viewModel.addReaction(message.id, it) }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (menuMessage != null) {
        ModalBottomSheet(onDismissRequest = { menuMessage = null }) {
            val msg = menuMessage!!
            Column(Modifier.padding(bottom = 32.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceEvenly) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "❌").forEach { emoji ->
                        TextButton(onClick = { viewModel.addReaction(msg.id, emoji); menuMessage = null }) { Text(emoji, fontSize = 24.sp) }
                    }
                }
                ListItem(headlineContent = { Text("Reply") }, leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, modifier = Modifier.clickable { viewModel.setReplyTo(msg); menuMessage = null })
                ListItem(headlineContent = { Text("Copy") }, leadingContent = { Icon(Icons.Default.ContentCopy, null) }, modifier = Modifier.clickable { clipboard.setText(AnnotatedString(msg.text ?: "")); menuMessage = null })
                ListItem(headlineContent = { Text("Delete for Me") }, leadingContent = { Icon(Icons.Default.Delete, null) }, modifier = Modifier.clickable { viewModel.deleteMessage(msg.id, DeleteType.DELETE_FOR_ME); menuMessage = null })
                if (msg.isFromMe) ListItem(headlineContent = { Text("Delete for Everyone", color = Color.Red) }, leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = Color.Red) }, modifier = Modifier.clickable { viewModel.deleteMessage(msg.id, DeleteType.DELETE_FOR_EVERYONE); menuMessage = null })
            }
        }
    }

    if (selectedFullImage != null) FullImageViewer(selectedFullImage!!) { selectedFullImage = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: MessageEntity, peerName: String, onLongClick: () -> Unit, onImageClick: (String) -> Unit, onReactionClick: (String?) -> Unit) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { }, onLongClick = onLongClick), horizontalAlignment = alignment) {
        Surface(shape = RoundedCornerShape(16.dp), color = containerColor) {
            Column(Modifier.padding(8.dp)) {
                if (message.isDeletedForEveryone) {
                    Text("This message was deleted", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (message.replyToId != null) {
                        Text("Replying to...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    message.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    message.mediaPath?.let { AsyncImage(it, null, Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).clickable { onImageClick(it) }, contentScale = ContentScale.Crop) }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    if (message.isFromMe) {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(message.status)
                    }
                }
            }
        }
        message.reaction?.let { reaction ->
            Surface(modifier = Modifier.offset(y = (-8).dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, onClick = { onReactionClick(null) }) {
                Text(reaction, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun StatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.QUEUED -> Icon(Icons.Default.Schedule, null, Modifier.size(12.dp))
        MessageStatus.SENDING -> CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.dp)
        MessageStatus.SENT -> Icon(Icons.Default.Check, null, Modifier.size(12.dp))
        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, Modifier.size(12.dp))
        MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        MessageStatus.FAILED -> Icon(Icons.Default.Error, null, Modifier.size(12.dp), tint = Color.Red)
        else -> {}
    }
}
