package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.VoiceLanguage
import com.example.presentation.SettingsUiState
import com.example.presentation.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onTestConnection: () -> Unit,
    onRemoveApiKey: () -> Unit,
    onSelectModel: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onSaveSystemInstructions: (String) -> Unit,
    onToggleVoiceFeedback: (Boolean) -> Unit,
    onSelectVoiceLanguage: (VoiceLanguage) -> Unit,
    onToggleSensitiveConfirmation: (Boolean) -> Unit,
    onResetTokens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var instructionsDraft by remember(settings.systemInstructions) {
        mutableStateOf(settings.systemInstructions)
    }
    var temperatureSlider by remember(settings.temperature) {
        mutableStateOf(settings.temperature)
    }

    LaunchedEffect(settings.temperature) {
        temperatureSlider = settings.temperature
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "AI Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Configure your personal Google Gemini API key, model, voice language, and safety guardrails.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // MANDATORY GEMINI API CONFIGURATION SECTION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Gemini API Configuration",
                                    tint = ElectricCyan
                                )
                                Column {
                                    Text(
                                        text = "Gemini API Configuration",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    // 1. AI Provider: Google Gemini
                                    Text(
                                        text = "AI Provider: Google Gemini",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }

                        // 7. Connection Status Indicator
                        val (statusLabel, statusColor) = when (settings.connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Connected" to MintSuccess
                            ConnectionStatus.FAILED -> "Connection Failed" to CoralError
                            ConnectionStatus.NOT_CONFIGURED ->
                                (if (settings.hasApiKey) "Saved (Unverified)" else "Not Configured") to AmberWarning
                        }

                        Surface(
                            color = statusColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connection_status_indicator")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (settings.connectionStatus == ConnectionStatus.CONNECTED) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.ErrorOutline
                                    },
                                    contentDescription = statusLabel,
                                    tint = statusColor
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    StatusBadge(label = statusLabel, color = statusColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = settings.connectionMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    if (settings.hasApiKey) {
                                        Text(
                                            text = "Stored Key (Keystore Encrypted): ${settings.maskedApiKey}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Gemini API Key input field + 3. Show/Hide API Key button
                        OutlinedTextField(
                            value = uiState.apiKeyDraft,
                            onValueChange = onApiKeyDraftChange,
                            label = {
                                Text(
                                    if (settings.hasApiKey) "Update Gemini API Key" else "Enter Gemini API Key"
                                )
                            },
                            placeholder = { Text("AIza...", color = TextMuted) },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showApiKey = !showApiKey },
                                    modifier = Modifier.testTag("toggle_api_key_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedContainerColor = ObsidianElevated,
                                unfocusedContainerColor = ObsidianElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        // 4. Save API Key, 5. Test API Connection, 6. Remove API Key buttons
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onSaveApiKey,
                                enabled = uiState.apiKeyDraft.isNotBlank(),
                                modifier = Modifier.testTag("save_api_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save API Key",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save API Key")
                            }

                            OutlinedButton(
                                onClick = onTestConnection,
                                enabled = !uiState.isTestingConnection &&
                                    (uiState.apiKeyDraft.isNotBlank() || settings.hasApiKey),
                                modifier = Modifier.testTag("test_api_connection_button")
                            ) {
                                if (uiState.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = "Test API Connection",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isTestingConnection) "Testing..." else "Test API Connection")
                            }

                            if (settings.hasApiKey) {
                                OutlinedButton(
                                    onClick = onRemoveApiKey,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                                    border = BorderStroke(1.dp, CoralError.copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("remove_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove API Key",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remove API Key")
                                }
                            }
                        }

                        if (!uiState.feedbackMessage.isNullOrBlank()) {
                            Text(
                                text = uiState.feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }

                        // 8. Selected Gemini Model dropdown
                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                        ) {
                            val selectedLabel = settings.availableModels
                                .firstOrNull { it.id == settings.selectedModel }
                                ?.let { "${it.displayName} (${it.id})" }
                                ?: settings.selectedModel

                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Gemini Model") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianElevated,
                                    unfocusedContainerColor = ObsidianElevated
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("gemini_model_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                settings.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = model.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectModel(model.id)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Planner Tuning & System Instructions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Model Tuning",
                                tint = ElectricCyan
                            )
                            Text("Planner Parameters", style = MaterialTheme.typography.titleLarge)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format("%.2f", temperatureSlider),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = temperatureSlider,
                            onValueChange = { temperatureSlider = (it * 20f).roundToInt() / 20f },
                            onValueChangeFinished = { onTemperatureChange(temperatureSlider) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan
                            )
                        )

                        OutlinedTextField(
                            value = instructionsDraft,
                            onValueChange = { instructionsDraft = it },
                            label = { Text("System Instructions") },
                            minLines = 3,
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedContainerColor = ObsidianElevated,
                                unfocusedContainerColor = ObsidianElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = { onSaveSystemInstructions(instructionsDraft) }) {
                                Text("Update Instructions")
                            }
                        }
                    }
                }
            }

            // Voice & Safety Guardrails
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Voice & Safety Guardrails", style = MaterialTheme.typography.titleLarge)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Spoken Voice Responses (TTS)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Speak step confirmations and page summaries aloud",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = settings.voiceFeedbackEnabled,
                                onCheckedChange = onToggleVoiceFeedback
                            )
                        }

                        Text("Voice Language (EN / HI / Hinglish)", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VoiceLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.voiceLanguage == lang,
                                    onClick = { onSelectVoiceLanguage(lang) },
                                    label = { Text(lang.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricCyan
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Confirm Before Sending / Deleting", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Require explicit user approval for sensitive actions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = settings.requireSensitiveConfirmation,
                                onCheckedChange = onToggleSensitiveConfirmation
                            )
                        }
                    }
                }
            }

            // Token Usage Telemetry
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Token Usage Telemetry", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = onResetTokens) {
                                Text("Reset", color = TextSecondary)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TokenStat("Prompt", settings.totalPromptTokens)
                            TokenStat("Completion", settings.totalCompletionTokens)
                            TokenStat("Total", settings.totalTokens)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenStat(label: String, count: Long) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = ElectricCyan
        )
    }
}
