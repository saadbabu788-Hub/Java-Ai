package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ActionLog
import com.example.domain.model.AutomationTask
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.TaskRunStatus
import com.example.presentation.TasksUiState
import com.example.presentation.components.EmptyArtworkCard
import com.example.presentation.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LuminousIndigo
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    messages: List<ChatMessage>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearHistory: () -> Unit,
    onReuseCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (messages.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear chat history",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear", color = CoralError)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search history",
                        tint = TextSecondary
                    )
                },
                placeholder = {
                    Text("Search past commands or summaries...", color = TextMuted)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyArtworkCard(
                            drawableRes = R.drawable.img_empty_tasks,
                            title = "No Conversations Yet",
                            subtitle = "Your voice and text interactions with AuraPilot are persisted locally here for review and quick replay."
                        )
                    }
                } else {
                    items(messages.reversed(), key = { it.id }) { msg ->
                        val isUser = msg.role == MessageRole.USER
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) ObsidianElevated else ObsidianCard
                            ),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isUser) "YOU" else "AURAPILOT ${msg.statusTag?.let { "• $it" }.orEmpty()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUser) ElectricCyan else LuminousIndigo
                                    )
                                    Text(
                                        text = formatter.format(Date(msg.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (isUser) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onReuseCommand(msg.content) }) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Run command again",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Run Again")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksScreen(
    uiState: TasksUiState,
    onClearTasks: () -> Unit,
    onReplayTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Automation Tasks",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (uiState.tasks.isNotEmpty()) {
                    TextButton(
                        onClick = onClearTasks,
                        modifier = Modifier.testTag("clear_tasks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear automation logs",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear", color = CoralError)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.tasks.isEmpty()) {
                    item {
                        EmptyArtworkCard(
                            drawableRes = R.drawable.img_empty_tasks,
                            title = "No Automation Runs Logged",
                            subtitle = "Multi-step workflows, inspected screen actions, and summaries appear here with full audit logs."
                        )
                    }
                } else {
                    items(uiState.tasks, key = { it.id }) { task ->
                        AutomationTaskCard(
                            task = task,
                            logs = uiState.logsByTaskId[task.id].orEmpty(),
                            onReplay = { onReplayTask(task.userCommand) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationTaskCard(
    task: AutomationTask,
    logs: List<ActionLog>,
    onReplay: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    val statusColor = when (task.status) {
        TaskRunStatus.COMPLETED -> MintSuccess
        TaskRunStatus.FAILED -> CoralError
        TaskRunStatus.RUNNING -> ElectricCyan
        TaskRunStatus.WAITING_CONFIRMATION -> AmberWarning
        else -> TextSecondary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(label = task.status.name, color = statusColor)
                Text(
                    text = formatter.format(Date(task.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = task.userCommand,
                style = MaterialTheme.typography.titleMedium
            )

            if (!task.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${logs.size} action(s) recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReplay) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Replay automation task",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle step details",
                            tint = TextSecondary
                        )
                    }
                }
            }

            if (expanded && logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianElevated, MaterialTheme.shapes.small)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    logs.forEach { log ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = if (log.success) "Success" else "Failed",
                                tint = if (log.success) MintSuccess else CoralError,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "#${log.stepIndex} [${log.actionType}] ${log.targetDescription}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
