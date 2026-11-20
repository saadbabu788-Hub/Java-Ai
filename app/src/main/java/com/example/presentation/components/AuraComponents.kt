package com.example.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AgentStatus
import com.example.domain.model.ExecutionStep
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.StepStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ListeningViolet
import com.example.ui.theme.LuminousIndigo
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextSecondary

@Composable
fun AgentOrbIndicator(
    status: AgentStatus,
    rmsLevel: Float,
    isInspectingScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (status) {
            AgentStatus.IDLE -> ElectricCyan
            AgentStatus.LISTENING -> ListeningViolet
            AgentStatus.THINKING, AgentStatus.PLANNING -> LuminousIndigo
            AgentStatus.EXECUTING -> ElectricCyan
            AgentStatus.WAITING_PERMISSION, AgentStatus.WAITING_CONFIRMATION -> AmberWarning
            AgentStatus.COMPLETED -> MintSuccess
            AgentStatus.FAILED -> CoralError
        },
        label = "orbColor"
    )

    val isActive = status in setOf(
        AgentStatus.LISTENING,
        AgentStatus.THINKING,
        AgentStatus.PLANNING,
        AgentStatus.EXECUTING
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 900 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val voiceBoost by animateFloatAsState(
        targetValue = if (status == AgentStatus.LISTENING) rmsLevel * 0.28f else 0f,
        animationSpec = spring(),
        label = "voiceBoost"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 3200 else 9000),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .testTag("agent_status_orb"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val dynamicScale = pulse + voiceBoost

                // Outer atmospheric glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isActive) 0.42f else 0.18f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * dynamicScale
                    ),
                    radius = radius * dynamicScale,
                    center = center
                )

                // Orbiting arc ring
                drawArc(
                    color = accentColor.copy(alpha = 0.75f),
                    startAngle = rotation,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = LuminousIndigo.copy(alpha = 0.45f),
                    startAngle = rotation + 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Core orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            accentColor,
                            accentColor.copy(alpha = 0.4f)
                        ),
                        center = center,
                        radius = radius * 0.42f
                    ),
                    radius = radius * 0.36f * (1f + voiceBoost * 0.4f),
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(label = status.label, color = accentColor)
            if (isInspectingScreen) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AmberWarning.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Reading screen content",
                            tint = AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Reading Screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TaskProgressCard(
    currentAction: String,
    steps: List<ExecutionStep>,
    isRunning: Boolean,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Execution progress",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = currentAction.ifBlank { "Executing automation plan..." },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isRunning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralError,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("stop_execution_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Emergency stop",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ElectricCyan,
                    trackColor = ObsidianElevated
                )
            }

            if (steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.takeLast(5).forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val (icon, tint) = when (step.status) {
                                StepStatus.SUCCEEDED -> Icons.Default.CheckCircle to MintSuccess
                                StepStatus.FAILED -> Icons.Default.ErrorOutline to CoralError
                                StepStatus.RUNNING -> Icons.Default.Pending to ElectricCyan
                                else -> Icons.Default.Pending to TextSecondary
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = step.status.name,
                                tint = tint,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Step ${step.stepNumber}: ${step.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (step.status == StepStatus.RUNNING) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyConfirmationDialog(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = ObsidianCard,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Safety confirmation required",
                tint = AmberWarning,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = pending.reason,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pending.actionSummary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Surface(
                    color = AmberWarning.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "User consent required",
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AuraPilot paused before executing a sensitive action. Review carefully.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                modifier = Modifier.testTag("approve_sensitive_action_button")
            ) {
                Text("Approve & Continue")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.testTag("deny_sensitive_action_button")
            ) {
                Text("Cancel Action")
            }
        }
    )
}

@Composable
fun EmptyArtworkCard(
    drawableRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
