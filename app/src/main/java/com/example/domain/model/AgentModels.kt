package com.example.domain.model

enum class AgentStatus(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    PLANNING("Planning"),
    EXECUTING("Executing"),
    WAITING_PERMISSION("Waiting for permission"),
    WAITING_CONFIRMATION("Awaiting approval"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

enum class ActionType {
    OPEN_APP,
    OPEN_URL,
    TAP_NODE,
    TAP_TEXT,
    TYPE_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    PRESS_BACK,
    PRESS_HOME,
    WAIT,
    READ_AND_SUMMARIZE,
    ASK_CONFIRMATION,
    COMPLETE,
    FAIL
}

data class AgentDecision(
    val thought: String,
    val planSteps: List<String>,
    val action: ActionType,
    val targetApp: String? = null,
    val nodeIndex: Int? = null,
    val targetText: String? = null,
    val inputText: String? = null,
    val submitAfterInput: Boolean = false,
    val url: String? = null,
    val spokenMessage: String? = null,
    val requiresConfirmation: Boolean = false,
    val confirmationReason: String? = null
)

data class ExecutionStep(
    val stepNumber: Int,
    val description: String,
    val actionType: ActionType,
    val status: StepStatus,
    val timestamp: Long = System.currentTimeMillis()
)

enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: Long = 0,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val statusTag: String? = null,
    val tokenCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TaskRunStatus {
    PENDING,
    RUNNING,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AutomationTask(
    val id: Long = 0,
    val title: String,
    val userCommand: String,
    val status: TaskRunStatus,
    val plannedSteps: List<String>,
    val currentStepIndex: Int,
    val summary: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ActionLog(
    val id: Long = 0,
    val taskId: Long,
    val stepIndex: Int,
    val actionType: String,
    val targetDescription: String,
    val packageName: String,
    val success: Boolean,
    val errorMessage: String?,
    val timestamp: Long
)

enum class ConnectionStatus {
    NOT_CONFIGURED,
    CONNECTED,
    FAILED
}

enum class VoiceLanguage(val label: String, val bcp47: String) {
    AUTO("Auto (EN / HI / Hinglish)", "en-IN"),
    ENGLISH("English", "en-US"),
    HINDI("Hindi (हिन्दी)", "hi-IN"),
    HINGLISH("Hinglish", "en-IN")
}

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val description: String = ""
)

data class AiSettings(
    val hasApiKey: Boolean = false,
    val maskedApiKey: String = "",
    val selectedModel: String = DEFAULT_MODEL_ID,
    val availableModels: List<GeminiModelOption> = DEFAULT_MODELS,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
    val connectionMessage: String = "No Gemini API key configured",
    val temperature: Float = 0.2f,
    val systemInstructions: String = DEFAULT_SYSTEM_INSTRUCTIONS,
    val voiceFeedbackEnabled: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val requireSensitiveConfirmation: Boolean = true,
    val screenContextConsentAccepted: Boolean = false,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L
) {
    val totalTokens: Long get() = totalPromptTokens + totalCompletionTokens

    companion object {
        const val DEFAULT_MODEL_ID = "gemini-2.5-flash"

        val DEFAULT_MODELS = listOf(
            GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast multimodal reasoning & agent planning"),
            GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Deep multi-step reasoning & complex tasks"),
            GeminiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Ultra-low-latency lightweight execution"),
            GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Stable high-speed execution model")
        )

        const val DEFAULT_SYSTEM_INSTRUCTIONS =
            "You are AuraPilot, a cautious and accurate native Android AI phone operator. " +
                "Analyze the current accessible UI hierarchy and execute one verified step at a time toward the user's goal. " +
                "Respond in the user's language (English, Hindi, or Hinglish). " +
                "Never attempt to read passwords, bypass authentication, or complete payments/deletions without confirmation."
    }
}

data class PermissionSnapshot(
    val isAccessibilityEnabled: Boolean = false,
    val isMicrophoneGranted: Boolean = false,
    val isNotificationsGranted: Boolean = true
)

data class PendingConfirmation(
    val reason: String,
    val actionSummary: String,
    val decision: AgentDecision
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

sealed class SafetyCheckResult {
    data object Safe : SafetyCheckResult()
    data class RequiresConfirmation(val reason: String, val summary: String) : SafetyCheckResult()
    data class Blocked(val reason: String) : SafetyCheckResult()
}
