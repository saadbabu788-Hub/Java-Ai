package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AgentStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.presentation.HomeUiState
import com.example.presentation.components.AgentOrbIndicator
import com.example.presentation.components.SafetyConfirmationDialog
import com.example.presentation.components.TaskProgressCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ListeningViolet
import com.example.ui.theme.LuminousIndigo
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

private val quickCommands = listOf(
    "Open YouTube and search for AI news",
    "Open Chrome and search weather today",
    "Open Settings",
    "Read visible screen and summarize",
    "YouTube kholo aur lofi music search karo"
)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onCommandChange: (String) -> Unit,
    onSendCommand: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onNewSession: () -> Unit,
    onApproveConfirmation: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.sessionMessages.size) {
        if (uiState.sessionMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.sessionMessages.lastIndex)
        }
    }

    val isExecuting = uiState.engine.status in setOf(
        AgentStatus.THINKING,
        AgentStatus.PLANNING,
        AgentStatus.EXECUTING,
        AgentStatus.WAITING_CONFIRMATION
    )

    uiState.engine.pendingConfirmation?.let { pending ->
        SafetyConfirmationDialog(
            pending = pending,
            onApprove = { onApproveConfirmation(true) },
            onDeny = { onApproveConfirmation(false) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top AI Cockpit Header with Hero Art + Status Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ai_banner),
                    contentDescription = "AuraPilot neural cockpit background",
                    modifier = Modifier
                        .matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.28f
                )
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AuraPilot",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.settings.hasApiKey) {
                                        "Model: ${uiState.settings.selectedModel}"
                                    } else {
                                        "Gemini API Key Required"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.settings.hasApiKey) ElectricCyan else AmberWarning
                                )
                            }
                            IconButton(
                                onClick = onNewSession,
                                modifier = Modifier.testTag("new_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddComment,
                                    contentDescription = "Start new conversation",
                                    tint = TextSecondary
                                )
                            }
                        }

                        AgentOrbIndicator(
                            status = uiState.effectiveStatus,
                            rmsLevel = uiState.voice.rmsLevel,
                            isInspectingScreen = uiState.engine.isInspectingScreen
                        )

                        if (uiState.voice.isListening && uiState.voice.partialTranscript.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"${uiState.voice.partialTranscript}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ListeningViolet
                            )
                        }
                    }
                }
            }

            // Setup Banners if API Key or Accessibility is not configured
            if (!uiState.settings.hasApiKey) {
                ActionAlertBanner(
                    icon = Icons.Default.Key,
                    title = "Gemini API Key Needed",
                    message = "Add your personal Gemini API key in AI Settings to enable AI planning.",
                    ctaLabel = "Configure Key",
                    accent = AmberWarning,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else if (!uiState.permissions.isAccessibilityEnabled) {
                ActionAlertBanner(
                    icon = Icons.Default.AccessibilityNew,
                    title = "Accessibility Operator Off",
                    message = "Enable Accessibility Service in Permissions to tap, scroll & read other apps.",
                    ctaLabel = "Enable",
                    accent = ElectricCyan,
                    onClick = onNavigateToPermissions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Live Task Execution Progress
            AnimatedVisibility(
                visible = isExecuting || uiState.engine.plannedSteps.isNotEmpty()
            ) {
                TaskProgressCard(
                    currentAction = uiState.engine.currentActionDescription,
                    steps = uiState.engine.plannedSteps,
                    isRunning = isExecuting,
                    onStopClick = onStopClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Conversation History Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.sessionMessages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Try a voice or text command (English, Hindi, or Hinglish):",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(quickCommands) { suggestion ->
                                    AssistChip(
                                        onClick = { onQuickCommand(suggestion) },
                                        label = {
                                            Text(
                                                text = suggestion,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = ObsidianCard,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.sessionMessages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                    }
                }
            }

            // Bottom Command Dock: Large Microphone + Text Input + Send / Emergency Stop
            Surface(
                color = ObsidianSurface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Large Microphone Button
                    val micActive = uiState.voice.isListening
                    FilledIconButton(
                        onClick = onMicClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (micActive) ListeningViolet else CyanContainer,
                            contentColor = if (micActive) Color.White else ElectricCyan
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("microphone_button")
                    ) {
                        Icon(
                            imageVector = if (micActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (micActive) "Stop listening" else "Start voice command",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    OutlinedTextField(
                        value = uiState.commandInput,
                        onValueChange = onCommandChange,
                        placeholder = {
                            Text(
                                text = "Ask AuraPilot to operate your phone...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSendCommand() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field")
                    )

                    if (isExecuting) {
                        FilledIconButton(
                            onClick = onStopClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = CoralError,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("dock_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop execution"
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSendCommand,
                            enabled = uiState.commandInput.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianVoid,
                                disabledContainerColor = ObsidianElevated,
                                disabledContentColor = TextMuted
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("send_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send command"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionAlertBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    ctaLabel: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            TextButton(onClick = onClick) {
                Text(text = ctaLabel, color = accent)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) CyanContainer else ObsidianCard
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) ElectricCyan.copy(alpha = 0.4f) else ObsidianBorder
            ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && !message.statusTag.isNullOrBlank()) {
                    Text(
                        text = message.statusTag.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousIndigo
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
