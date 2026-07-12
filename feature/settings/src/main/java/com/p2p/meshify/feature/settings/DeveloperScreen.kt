package com.p2p.meshify.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.components.MeshifySettingsGroup
import com.p2p.meshify.core.ui.components.MeshifySettingsItem
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    onBackClick: () -> Unit,
    onRealDeviceTestingClick: () -> Unit = {},
    onResetOnboardingClick: () -> Unit = {}
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
                            text = stringResource(R.string.developer_screen_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.developer_screen_subtitle),
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
            MeshifySettingsGroup(title = stringResource(R.string.developer_group_mock_data)) {
                MeshifySettingsItem(
                    title = stringResource(R.string.developer_add_mock_conversations),
                    subtitle = stringResource(R.string.developer_mock_conversations_subtitle),
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
                    title = stringResource(R.string.developer_add_media_messages),
                    subtitle = stringResource(R.string.developer_mock_media_subtitle),
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
                    title = stringResource(R.string.developer_add_reactions_demo),
                    subtitle = stringResource(R.string.developer_mock_reactions_subtitle),
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
                    title = stringResource(R.string.developer_add_replies_demo),
                    subtitle = stringResource(R.string.developer_mock_replies_subtitle),
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
                    title = stringResource(R.string.developer_add_long_conversation),
                    subtitle = stringResource(R.string.developer_mock_long_chat_subtitle),
                    icon = Icons.Default.FormatListNumbered,
                    onClick = {
                        viewModel.insertMockLongConversation { statusMessage = it }
                    }
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Testing Section
            MeshifySettingsGroup(title = stringResource(R.string.developer_testing_section)) {
                MeshifySettingsItem(
                    title = stringResource(R.string.developer_real_device_testing_title),
                    subtitle = stringResource(R.string.developer_real_device_testing_subtitle),
                    icon = Icons.Default.Build,
                    onClick = onRealDeviceTestingClick
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))

            // Cleanup Section
            MeshifySettingsGroup(title = stringResource(R.string.developer_group_cleanup)) {
                MeshifySettingsItem(
                    title = stringResource(R.string.developer_clear_mock_data),
                    subtitle = stringResource(R.string.developer_mock_clear_subtitle),
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
                    title = stringResource(R.string.developer_clear_all_data),
                    subtitle = stringResource(R.string.developer_clear_all_warning),
                    icon = Icons.Default.Warning,
                    onClick = {
                        showClearDataConfirmation = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MeshifyDesignSystem.Spacing.Md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                MeshifySettingsItem(
                    title = stringResource(R.string.developer_reset_onboarding_title),
                    subtitle = stringResource(R.string.developer_reset_onboarding_subtitle),
                    icon = Icons.Default.Info,
                    onClick = onResetOnboardingClick
                )
            }

            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))
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
                val successMsg = stringResource(R.string.developer_clear_success)
                TextButton(
                    onClick = {
                        showClearDataConfirmation = false
                        viewModel.clearAllData { statusMessage = successMsg }
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
            delay(3000)
            statusMessage = null
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
                action = {
                    TextButton(onClick = { statusMessage = null }) {
                        Text(stringResource(R.string.developer_action_ok))
                    }
                }
            ) {
                Text(statusMessage ?: "")
            }
        }
    }
}
