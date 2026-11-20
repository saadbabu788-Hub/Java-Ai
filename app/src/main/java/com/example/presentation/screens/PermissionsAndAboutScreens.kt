package com.example.presentation.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PermissionSnapshot
import com.example.presentation.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextSecondary

@Composable
fun PermissionsScreen(
    permissions: PermissionSnapshot,
    onRefresh: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permissions", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "AuraPilot never enables or hides permissions automatically. Review and grant only what you want to use.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh permission status",
                            tint = ElectricCyan
                        )
                    }
                }
            }

            item {
                PermissionCard(
                    icon = Icons.Default.AccessibilityNew,
                    title = "Accessibility UI Operator",
                    granted = permissions.isAccessibilityEnabled,
                    whyNeeded = "Required so AuraPilot can inspect visible screen layouts (buttons, search bars, page text) and execute taps, scrolling, and text input when you issue an automation command.",
                    privacyNote = "Password fields are strictly masked and never read or sent to Gemini. You can disable this service anytime in Android Accessibility Settings.",
                    actionLabel = if (permissions.isAccessibilityEnabled) "Manage in Settings" else "Open Accessibility Settings",
                    onAction = onOpenAccessibilitySettings,
                    testTag = "accessibility_permission_button"
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone (Voice Commands)",
                    granted = permissions.isMicrophoneGranted,
                    whyNeeded = "Used exclusively when you tap the Microphone button to dictate commands in English, Hindi, or Hinglish via Android SpeechRecognizer.",
                    privacyNote = "No passive background listening occurs when the microphone is idle.",
                    actionLabel = if (permissions.isMicrophoneGranted) "Revoke in App Settings" else "Grant Microphone Access",
                    onAction = if (permissions.isMicrophoneGranted) onOpenAppSettings else onRequestMicrophone,
                    testTag = "microphone_permission_button"
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Task Runner Notifications",
                    granted = permissions.isNotificationsGranted,
                    whyNeeded = "Displays a live foreground indicator with an Emergency Stop button while AuraPilot runs multi-step actions across other apps.",
                    privacyNote = "Only shown while an automation task is actively executing.",
                    actionLabel = if (permissions.isNotificationsGranted) "Manage in App Settings" else "Grant Notification Access",
                    onAction = if (permissions.isNotificationsGranted) onOpenAppSettings else onRequestNotifications,
                    testTag = "notifications_permission_button"
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    whyNeeded: String,
    privacyNote: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = ElectricCyan)
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                }
                StatusBadge(
                    label = if (granted) "Enabled" else "Not Granted",
                    color = if (granted) MintSuccess else AmberWarning
                )
            }

            Text(text = whyNeeded, style = MaterialTheme.typography.bodyMedium)

            Surface(
                color = ObsidianElevated,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy commitment",
                        tint = MintSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = privacyNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (granted) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.testTag(testTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            } else {
                Button(
                    onClick = onAction,
                    modifier = Modifier.testTag(testTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.img_ai_banner),
                    contentDescription = "AuraPilot banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }

            item {
                Text("About AuraPilot", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "AuraPilot is a native Android AI Phone Operator powered by Google Gemini, Android Accessibility APIs, Jetpack Compose, and Room local persistence.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            item {
                AboutInfoCard(
                    title = "How Multi-Step Execution Works",
                    body = "1. Understands your voice or text command in English, Hindi, or Hinglish.\n" +
                        "2. Inspects the live accessible UI hierarchy while masking password fields.\n" +
                        "3. Plans one verified action at a time with Google Gemini.\n" +
                        "4. Pauses for your confirmation before sending messages, deleting items, or sensitive operations."
                )
            }

            item {
                AboutInfoCard(
                    title = "Security & Key Ownership",
                    body = "• Bring Your Own Key: Your Gemini API key is encrypted locally using Android Keystore AES-256 GCM.\n" +
                        "• Zero Hidden Automation: An active foreground notification and in-app Stop button let you halt any workflow immediately.\n" +
                        "• Strict Boundaries: Financial transactions, payment confirmations, and password fields are blocked from automation."
                )
            }
        }
    }
}

@Composable
private fun AboutInfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
