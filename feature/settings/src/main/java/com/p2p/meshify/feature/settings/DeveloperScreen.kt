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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsDivider
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsItem
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsSection
import com.p2p.meshify.core.ui.designsystem.foundation.DxSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    onBackClick: () -> Unit,
    onRealDeviceTestingClick: () -> Unit = {},
    onResetOnboardingClick: () -> Unit = {}
) {
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showClearDataConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_content_desc_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DxSpacing.Lg)
        ) {
            Spacer(Modifier.height(DxSpacing.Lg))

            // Mock Data Section
            DxSettingsSection(title = stringResource(R.string.developer_group_mock_data)) {
                DxSettingsItem(
                    icon = Icons.Default.Chat,
                    title = stringResource(R.string.developer_add_mock_conversations),
                    subtitle = stringResource(R.string.developer_mock_conversations_subtitle),
                    onClick = {
                        viewModel.insertMockConversations { statusMessage = it }
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.developer_add_media_messages),
                    subtitle = stringResource(R.string.developer_mock_media_subtitle),
                    onClick = {
                        viewModel.insertMockMediaMessages { statusMessage = it }
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.EmojiEmotions,
                    title = stringResource(R.string.developer_add_reactions_demo),
                    subtitle = stringResource(R.string.developer_mock_reactions_subtitle),
                    onClick = {
                        viewModel.insertMockChatWithReactions { statusMessage = it }
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.Reply,
                    title = stringResource(R.string.developer_add_replies_demo),
                    subtitle = stringResource(R.string.developer_mock_replies_subtitle),
                    onClick = {
                        viewModel.insertMockChatWithReplies { statusMessage = it }
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.FormatListNumbered,
                    title = stringResource(R.string.developer_add_long_conversation),
                    subtitle = stringResource(R.string.developer_mock_long_chat_subtitle),
                    onClick = {
                        viewModel.insertMockLongConversation { statusMessage = it }
                    }
                )
            }

            Spacer(Modifier.height(DxSpacing.Lg))

            // Testing Section
            DxSettingsSection(title = stringResource(R.string.developer_testing_section)) {
                DxSettingsItem(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.developer_real_device_testing_title),
                    subtitle = stringResource(R.string.developer_real_device_testing_subtitle),
                    onClick = onRealDeviceTestingClick
                )
            }

            Spacer(Modifier.height(DxSpacing.Lg))

            // Cleanup Section
            DxSettingsSection(title = stringResource(R.string.developer_group_cleanup)) {
                DxSettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.developer_clear_mock_data),
                    subtitle = stringResource(R.string.developer_mock_clear_subtitle),
                    onClick = {
                        viewModel.clearMockData { statusMessage = it }
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.developer_clear_all_data),
                    subtitle = stringResource(R.string.developer_clear_all_warning),
                    onClick = {
                        showClearDataConfirmation = true
                    }
                )

                DxSettingsDivider()

                DxSettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.developer_reset_onboarding_title),
                    subtitle = stringResource(R.string.developer_reset_onboarding_subtitle),
                    onClick = onResetOnboardingClick
                )
            }

            Spacer(Modifier.height(DxSpacing.Lg))
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

    // Status Snackbar via Scaffold's SnackbarHost
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }
}
