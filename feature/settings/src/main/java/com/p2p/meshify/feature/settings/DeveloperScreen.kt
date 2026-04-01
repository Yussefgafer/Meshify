package com.p2p.meshify.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.ui.components.MeshifySettingsGroup
import com.p2p.meshify.core.ui.components.MeshifySettingsItem
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.core.common.R
import com.p2p.meshify.domain.model.MessageType
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class DeveloperViewModel(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ViewModel() {

    fun requestClearAllData(onConfirm: () -> Unit) {
        // Show confirmation dialog first
        onConfirm()
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                messageDao.deleteMessages(messageDao.getAllAttachments().map { it.messageId ?: "" }.filter { it.isNotEmpty() })
                messageDao.deleteAllMessagesForChat("")
                val chats = chatDao.getAllChats().firstOrNull() ?: emptyList()
                chats.forEach { chatDao.deleteChatById(it.peerId) }
                onComplete()
            } catch (e: Exception) {
                onComplete() // Still complete even on error
            }
        }
    }

    fun insertMockConversations(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val mockPeers = listOf(
                    MockPeer("peer_ahmed", "Ahmed Mohamed"),
                    MockPeer("peer_sara", "Sara Ali"),
                    MockPeer("peer_omar", "Omar Hassan"),
                    MockPeer("peer_fatima", "Fatima Ibrahim"),
                    MockPeer("peer_khaled", "Khaled Youssef"),
                    MockPeer("peer_nour", "Nour Mahmoud"),
                    MockPeer("peer_youssef", "Youssef Adel")
                )

                val baseTime = System.currentTimeMillis()

                mockPeers.forEachIndexed { peerIndex, peer ->
                    val chatTime = baseTime - (peerIndex * 3600000L)
                    chatDao.insertChat(
                        ChatEntity(
                            peerId = peer.id,
                            peerName = peer.name,
                            lastMessage = null,
                            lastTimestamp = chatTime
                        )
                    )

                    val messages = generateMockMessages(peer.id, peer.name, chatTime, peerIndex)
                    messages.forEach { msg ->
                        messageDao.insertMessage(msg)
                    }

                    // Update last message
                    val lastMsg = messages.lastOrNull()
                    chatDao.insertChat(
                        ChatEntity(
                            peerId = peer.id,
                            peerName = peer.name,
                            lastMessage = lastMsg?.text ?: "[Media]",
                            lastTimestamp = lastMsg?.timestamp ?: chatTime
                        )
                    )
                }

                onComplete("Added ${mockPeers.size} conversations with messages")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    fun insertMockMediaMessages(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val peerId = "peer_media_test"
                val peerName = "Media Test"
                val baseTime = System.currentTimeMillis()

                chatDao.insertChat(ChatEntity(peerId, peerName, "[Media Messages]", baseTime))

                val mediaMessages = listOf(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Check out this photo!",
                        mediaPath = null,
                        type = MessageType.IMAGE,
                        timestamp = baseTime - 300000,
                        isFromMe = true,
                        status = MessageStatus.READ
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = peerId,
                        text = null,
                        mediaPath = null,
                        type = MessageType.IMAGE,
                        timestamp = baseTime - 240000,
                        isFromMe = false
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Here's a video",
                        mediaPath = null,
                        type = MessageType.VIDEO,
                        timestamp = baseTime - 180000,
                        isFromMe = true,
                        status = MessageStatus.DELIVERED
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = peerId,
                        text = null,
                        mediaPath = null,
                        type = MessageType.FILE,
                        timestamp = baseTime - 120000,
                        isFromMe = false
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Document.pdf",
                        mediaPath = null,
                        type = MessageType.FILE,
                        timestamp = baseTime - 60000,
                        isFromMe = true,
                        status = MessageStatus.SENT
                    )
                )

                mediaMessages.forEach { messageDao.insertMessage(it) }
                onComplete("Added media test conversation")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    fun insertMockChatWithReactions(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val peerId = "peer_reactions"
                val peerName = "Reactions Demo"
                val baseTime = System.currentTimeMillis()

                chatDao.insertChat(ChatEntity(peerId, peerName, "👍", baseTime))

                val messages = listOf(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Hey! How are you?",
                        timestamp = baseTime - 500000,
                        isFromMe = true,
                        status = MessageStatus.READ,
                        reaction = "👋"
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = peerId,
                        text = "I'm great! Thanks for asking 😊",
                        timestamp = baseTime - 400000,
                        isFromMe = false
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Want to grab coffee later?",
                        timestamp = baseTime - 300000,
                        isFromMe = true,
                        status = MessageStatus.READ,
                        reaction = "👍"
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = peerId,
                        text = "Sounds good! Where?",
                        timestamp = baseTime - 200000,
                        isFromMe = false,
                        reaction = "❤️"
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "The usual place 🎉",
                        timestamp = baseTime - 100000,
                        isFromMe = true,
                        status = MessageStatus.DELIVERED
                    )
                )

                messages.forEach { messageDao.insertMessage(it) }
                onComplete("Added reactions demo conversation")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    fun insertMockChatWithReplies(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val peerId = "peer_replies"
                val peerName = "Replies Demo"
                val baseTime = System.currentTimeMillis()

                chatDao.insertChat(ChatEntity(peerId, peerName, "Got it!", baseTime))

                val msg1Id = UUID.randomUUID().toString()
                val msg2Id = UUID.randomUUID().toString()
                val msg3Id = UUID.randomUUID().toString()

                val messages = listOf(
                    MessageEntity(
                        id = msg1Id,
                        chatId = peerId,
                        senderId = "me",
                        text = "Can you send me the report?",
                        timestamp = baseTime - 300000,
                        isFromMe = true,
                        status = MessageStatus.READ
                    ),
                    MessageEntity(
                        id = msg2Id,
                        chatId = peerId,
                        senderId = peerId,
                        text = "Sure, I'll send it now",
                        timestamp = baseTime - 250000,
                        isFromMe = false,
                        replyToId = msg1Id
                    ),
                    MessageEntity(
                        id = msg3Id,
                        chatId = peerId,
                        senderId = peerId,
                        text = "Here it is!",
                        timestamp = baseTime - 200000,
                        isFromMe = false,
                        type = MessageType.FILE,
                        replyToId = msg2Id
                    ),
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = peerId,
                        senderId = "me",
                        text = "Thanks! Got it 👍",
                        timestamp = baseTime - 100000,
                        isFromMe = true,
                        status = MessageStatus.READ,
                        replyToId = msg3Id
                    )
                )

                messages.forEach { messageDao.insertMessage(it) }
                onComplete("Added replies demo conversation")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    fun insertMockLongConversation(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val peerId = "peer_long_chat"
                val peerName = "Long Chat"
                val baseTime = System.currentTimeMillis()

                val messages = mutableListOf<MessageEntity>()
                repeat(50) { i ->
                    messages.add(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            chatId = peerId,
                            senderId = if (i % 2 == 0) "me" else peerId,
                            text = getMockMessageText(i),
                            timestamp = baseTime - ((50 - i) * 60000L),
                            isFromMe = i % 2 == 0,
                            status = if (i % 2 == 0) MessageStatus.READ else MessageStatus.SENT,
                            reaction = if (i % 5 == 0) listOf("👍", "❤️", "😂", "😮").random() else null
                        )
                    )
                }

                chatDao.insertChat(
                    ChatEntity(
                        peerId = peerId,
                        peerName = peerName,
                        lastMessage = messages.last().text,
                        lastTimestamp = messages.last().timestamp
                    )
                )

                messages.forEach { messageDao.insertMessage(it) }
                onComplete("Added 50 message conversation")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    fun clearMockData(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val mockPeerIds = listOf(
                    "peer_ahmed", "peer_sara", "peer_omar", "peer_fatima",
                    "peer_khaled", "peer_nour", "peer_youssef",
                    "peer_media_test", "peer_reactions", "peer_replies", "peer_long_chat"
                )

                mockPeerIds.forEach { peerId ->
                    messageDao.deleteAllMessagesForChat(peerId)
                    chatDao.deleteChatById(peerId)
                }

                onComplete("Cleared all mock data")
            } catch (e: Exception) {
                onComplete("Error: ${e.message}")
            }
        }
    }

    private fun generateMockMessages(
        peerId: String,
        peerName: String,
        baseTime: Long,
        seed: Int
    ): List<MessageEntity> {
        val myMessages = listOf(
            "Hey! How's it going?",
            "Did you see the news today?",
            "Let's meet up this weekend",
            "I just finished the project",
            "Check this out!",
            "Can you help me with something?",
            "Thanks for your help earlier",
            "What do you think about this?",
            "That's awesome!",
            "See you tomorrow"
        )

        val theirMessages = listOf(
            "Hi! I'm doing well, thanks!",
            "Yeah, it's quite interesting",
            "Sounds great! When?",
            "Nice work! Congratulations",
            "Wow, that's cool!",
            "Sure, what do you need?",
            "No problem anytime!",
            "I think it's a good idea",
            "I know right?!",
            "Perfect, see you then"
        )

        return listOf(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = peerId,
                text = theirMessages[seed % theirMessages.size],
                timestamp = baseTime + 60000,
                isFromMe = false
            ),
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = "me",
                text = myMessages[seed % myMessages.size],
                timestamp = baseTime + 120000,
                isFromMe = true,
                status = MessageStatus.READ
            ),
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = peerId,
                text = theirMessages[(seed + 3) % theirMessages.size],
                timestamp = baseTime + 180000,
                isFromMe = false
            ),
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = "me",
                text = myMessages[(seed + 3) % myMessages.size],
                timestamp = baseTime + 240000,
                isFromMe = true,
                status = MessageStatus.DELIVERED
            )
        )
    }

    private fun getMockMessageText(index: Int): String {
        return when (index % 10) {
            0 -> "Hey there!"
            1 -> "How's everything going?"
            2 -> "Did you finish the task?"
            3 -> "I'll send you the details"
            4 -> "That sounds perfect"
            5 -> "Let me check and get back to you"
            6 -> "Great idea! Let's do it"
            7 -> "I'm on my way"
            8 -> "Almost there, 5 minutes"
            9 -> "See you soon! 👋"
            else -> "Message $index"
        }
    }

    private data class MockPeer(val id: String, val name: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showClearDataConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Developer Tools",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mock data & debugging",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeshifyDesignSystem.Spacing.Md)
        ) {
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Mock Data Section
            MeshifySettingsGroup(title = "Mock Data") {
                MeshifySettingsItem(
                    title = "Add Mock Conversations",
                    subtitle = "7 chats with different message types",
                    icon = Icons.Default.Chat,
                    onClick = {
                        viewModel.insertMockConversations { statusMessage = it }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Add Media Messages",
                    subtitle = "Images, videos, and files in a chat",
                    icon = Icons.Default.Image,
                    onClick = {
                        viewModel.insertMockMediaMessages { statusMessage = it }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Add Reactions Demo",
                    subtitle = "Chat with emoji reactions",
                    icon = Icons.Default.EmojiEmotions,
                    onClick = {
                        viewModel.insertMockChatWithReactions { statusMessage = it }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Add Replies Demo",
                    subtitle = "Chat with reply threads",
                    icon = Icons.Default.Reply,
                    onClick = {
                        viewModel.insertMockChatWithReplies { statusMessage = it }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Add Long Conversation",
                    subtitle = "50 messages with reactions",
                    icon = Icons.Default.FormatListNumbered,
                    onClick = {
                        viewModel.insertMockLongConversation { statusMessage = it }
                    }
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Cleanup Section
            MeshifySettingsGroup(title = "Cleanup") {
                MeshifySettingsItem(
                    title = "Clear Mock Data",
                    subtitle = "Remove all mock conversations",
                    icon = Icons.Default.DeleteSweep,
                    onClick = {
                        viewModel.clearMockData { statusMessage = it }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = "Clear ALL Data",
                    subtitle = "Delete everything - cannot be undone!",
                    icon = Icons.Default.Warning,
                    onClick = {
                        showClearDataConfirmation = true
                    }
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Xxl))
        }
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(stringResource(R.string.developer_clear_data_title))
            },
            text = {
                Text(stringResource(R.string.developer_clear_data_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirmation = false
                        viewModel.clearAllData {
                            statusMessage = context.getString(R.string.developer_clear_success)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.developer_clear_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmation = false }) {
                    Text(stringResource(R.string.developer_clear_data_cancel))
                }
            }
        )
    }

    // Status Snackbar
    if (statusMessage != null) {
        LaunchedEffect(statusMessage) {
            kotlinx.coroutines.delay(3000)
            statusMessage = null
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
                action = {
                    TextButton(onClick = { statusMessage = null }) {
                        Text("OK")
                    }
                }
            ) {
                Text(statusMessage ?: "")
            }
        }
    }
}
