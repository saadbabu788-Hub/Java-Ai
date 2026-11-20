package com.example.automation

import android.content.Context
import com.example.accessibility.AccessibilityBridge
import com.example.accessibility.ScreenSnapshot
import com.example.ai.ErrorType
import com.example.ai.GeminiClient
import com.example.ai.GeminiResult
import com.example.database.ActionLogDao
import com.example.database.ActionLogEntity
import com.example.database.AutomationTaskDao
import com.example.database.AutomationTaskEntity
import com.example.database.ChatDao
import com.example.database.ChatMessageEntity
import com.example.domain.model.ActionType
import com.example.domain.model.AgentDecision
import com.example.domain.model.AgentStatus
import com.example.domain.model.ExecutionStep
import com.example.domain.model.MessageRole
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.SafetyCheckResult
import com.example.domain.model.StepStatus
import com.example.domain.model.TaskRunStatus
import com.example.permissions.PermissionManager
import com.example.settings.SettingsRepository
import com.example.voice.VoiceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AgentEngineState(
    val status: AgentStatus = AgentStatus.IDLE,
    val currentCommand: String = "",
    val currentActionDescription: String = "",
    val plannedSteps: List<ExecutionStep> = emptyList(),
    val activeStepIndex: Int = 0,
    val pendingConfirmation: PendingConfirmation? = null,
    val isInspectingScreen: Boolean = false,
    val activeSessionId: String = UUID.randomUUID().toString(),
    val lastError: String? = null
)

class AgentOrchestrator(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
    private val installedAppsHelper: InstalledAppsHelper,
    private val permissionManager: PermissionManager,
    private val voiceManager: VoiceManager,
    private val chatDao: ChatDao,
    private val taskDao: AutomationTaskDao,
    private val actionLogDao: ActionLogDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    private val _engineState = MutableStateFlow(AgentEngineState())
    val engineState: StateFlow<AgentEngineState> = _engineState.asStateFlow()

    private var activeJob: Job? = null
    @Volatile
    private var activeRunId: String? = null
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    init {
        AgentForegroundService.onEmergencyStopRequested = {
            stopExecution()
        }
        scope.launch {
            AccessibilityBridge.isInspectingScreen.collect { inspecting ->
                _engineState.value = _engineState.value.copy(isInspectingScreen = inspecting)
            }
        }
    }

    fun startNewSession() {
        _engineState.value = _engineState.value.copy(
            activeSessionId = UUID.randomUUID().toString(),
            status = AgentStatus.IDLE,
            plannedSteps = emptyList(),
            currentActionDescription = "",
            lastError = null
        )
    }

    fun submitCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return

        activeJob?.cancel()
        confirmationDeferred?.cancel()
        voiceManager.stopSpeaking()

        activeJob = scope.launch {
            val sessionId = _engineState.value.activeSessionId
            chatDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = MessageRole.USER.name,
                    content = command
                )
            )

            val settings = settingsRepository.settingsFlow.first()
            val apiKey = settingsRepository.getDecryptedApiKey()
            if (apiKey.isNullOrBlank()) {
                val msg = "No Gemini API key configured. Please open AI Settings to add and test your personal Gemini API key."
                _engineState.value = _engineState.value.copy(
                    status = AgentStatus.FAILED,
                    currentCommand = command,
                    currentActionDescription = msg,
                    lastError = msg
                )
                postAssistantMessage(sessionId, msg, "API Key Required")
                if (settings.voiceFeedbackEnabled) voiceManager.speak(msg, settings.voiceLanguage)
                return@launch
            }

            permissionManager.refresh()
            val isAccessibilityReady = permissionManager.isAccessibilityServiceEnabled()

            // Create persistent AutomationTask record
            val taskId = taskDao.insertTask(
                AutomationTaskEntity(
                    title = command.take(64),
                    userCommand = command,
                    status = TaskRunStatus.RUNNING.name,
                    plannedStepsJson = "[]",
                    currentStepIndex = 0
                )
            )

            val currentRunId = UUID.randomUUID().toString()
            activeRunId = currentRunId
            AccessibilityBridge.setAutomationActive(true)
            AgentForegroundService.startOrUpdate(context, "Planning task (${command.take(48)})")

            val stepHistory = mutableListOf<String>()
            val executionSteps = mutableListOf<ExecutionStep>()
            var lastPlannedLabels = emptyList<String>()

            try {
                val installedApps = installedAppsHelper.getLaunchableApps()

                for (stepNumber in 1..MAX_STEPS) {
                    _engineState.value = _engineState.value.copy(
                        status = if (stepNumber == 1) AgentStatus.THINKING else AgentStatus.PLANNING,
                        currentCommand = command,
                        currentActionDescription = "Step $stepNumber: Analyzing screen & planning...",
                        activeStepIndex = stepNumber - 1,
                        lastError = null
                    )

                    val service = AccessibilityBridge.activeService
                    val snapshot: ScreenSnapshot? = service?.captureFreshSnapshot()

                    if (snapshot?.isProtectedScreen == true) {
                        val protectedMsg = "Paused for security: AuraPilot does not inspect or automate protected screens (${snapshot.packageName})."
                        _engineState.value = _engineState.value.copy(
                            status = AgentStatus.FAILED,
                            currentActionDescription = protectedMsg,
                            lastError = protectedMsg
                        )
                        taskDao.updateTaskProgress(
                            id = taskId,
                            status = TaskRunStatus.FAILED.name,
                            stepIndex = stepNumber - 1,
                            stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                            summary = protectedMsg,
                            updatedAt = System.currentTimeMillis()
                        )
                        postAssistantMessage(sessionId, protectedMsg, "Protected Screen")
                        if (settings.voiceFeedbackEnabled) voiceManager.speak(protectedMsg, settings.voiceLanguage)
                        return@launch
                    }

                    val decisionResult = geminiClient.planNextStep(
                        apiKey = apiKey,
                        settings = settings,
                        userCommand = command,
                        stepIndex = stepNumber,
                        stepHistory = stepHistory,
                        screenSnapshot = snapshot,
                        installedApps = installedApps
                    )

                    val decision = when (decisionResult) {
                        is GeminiResult.Success -> {
                            settingsRepository.recordTokenUsage(
                                decisionResult.promptTokens,
                                decisionResult.completionTokens
                            )
                            decisionResult.data
                        }
                        is GeminiResult.Failure -> {
                            val failMsg = decisionResult.message
                            if (decisionResult.type == ErrorType.MISSING_API_KEY ||
                                decisionResult.type == ErrorType.INVALID_API_KEY
                            ) {
                                _engineState.value = _engineState.value.copy(
                                    status = AgentStatus.FAILED,
                                    currentActionDescription = failMsg,
                                    lastError = failMsg
                                )
                            } else {
                                _engineState.value = _engineState.value.copy(
                                    status = AgentStatus.FAILED,
                                    currentActionDescription = failMsg,
                                    lastError = failMsg
                                )
                            }
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.FAILED.name,
                                stepIndex = stepNumber - 1,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = failMsg,
                                updatedAt = System.currentTimeMillis()
                            )
                            postAssistantMessage(sessionId, failMsg, "Error")
                            if (settings.voiceFeedbackEnabled) voiceManager.speak(failMsg, settings.voiceLanguage)
                            return@launch
                        }
                    }

                    if (decision.planSteps.isNotEmpty()) {
                        lastPlannedLabels = decision.planSteps
                    }

                    val stepDesc = decision.spokenMessage?.takeIf { it.isNotBlank() }
                        ?: decision.thought.takeIf { it.isNotBlank() }
                        ?: decision.action.name

                    executionSteps.add(
                        ExecutionStep(
                            stepNumber = stepNumber,
                            description = stepDesc,
                            actionType = decision.action,
                            status = StepStatus.RUNNING
                        )
                    )
                    _engineState.value = _engineState.value.copy(
                        plannedSteps = executionSteps.toList(),
                        currentActionDescription = stepDesc
                    )

                    // Check if Accessibility Service is required for this action
                    if (requiresAccessibility(decision.action) && (!isAccessibilityReady || service == null)) {
                        val permMsg = "Accessibility Operator is needed to interact with '${snapshot?.packageName ?: "other apps"}' on screen. Enable AuraPilot in Permissions."
                        markLastStep(executionSteps, StepStatus.FAILED)
                        _engineState.value = _engineState.value.copy(
                            status = AgentStatus.WAITING_PERMISSION,
                            plannedSteps = executionSteps.toList(),
                            currentActionDescription = permMsg,
                            lastError = permMsg
                        )
                        taskDao.updateTaskProgress(
                            id = taskId,
                            status = TaskRunStatus.FAILED.name,
                            stepIndex = stepNumber - 1,
                            stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                            summary = permMsg,
                            updatedAt = System.currentTimeMillis()
                        )
                        postAssistantMessage(sessionId, permMsg, "Permission Required")
                        if (settings.voiceFeedbackEnabled) voiceManager.speak(permMsg, settings.voiceLanguage)
                        return@launch
                    }

                    // Safety Guard evaluation
                    when (val safety = SafetyGuard.evaluate(decision, snapshot, settings.requireSensitiveConfirmation)) {
                        is SafetyCheckResult.Blocked -> {
                            markLastStep(executionSteps, StepStatus.FAILED)
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.FAILED,
                                plannedSteps = executionSteps.toList(),
                                currentActionDescription = safety.reason,
                                lastError = safety.reason
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.FAILED.name,
                                stepIndex = stepNumber - 1,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = safety.reason,
                                updatedAt = System.currentTimeMillis()
                            )
                            postAssistantMessage(sessionId, safety.reason, "Blocked by Safety Guard")
                            if (settings.voiceFeedbackEnabled) voiceManager.speak(safety.reason, settings.voiceLanguage)
                            return@launch
                        }
                        is SafetyCheckResult.RequiresConfirmation -> {
                            val deferred = CompletableDeferred<Boolean>()
                            confirmationDeferred = deferred
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.WAITING_CONFIRMATION,
                                currentActionDescription = safety.reason,
                                pendingConfirmation = PendingConfirmation(
                                    reason = safety.reason,
                                    actionSummary = safety.summary,
                                    decision = decision
                                )
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.WAITING_CONFIRMATION.name,
                                stepIndex = stepNumber - 1,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = safety.summary,
                                updatedAt = System.currentTimeMillis()
                            )
                            if (settings.voiceFeedbackEnabled) {
                                voiceManager.speak(
                                    "Please confirm: ${safety.summary}",
                                    settings.voiceLanguage
                                )
                            }
                            val approved = deferred.await()
                            _engineState.value = _engineState.value.copy(pendingConfirmation = null)
                            if (!approved) {
                                markLastStep(executionSteps, StepStatus.SKIPPED)
                                val cancelMsg = "Action cancelled by user before execution."
                                _engineState.value = _engineState.value.copy(
                                    status = AgentStatus.IDLE,
                                    plannedSteps = executionSteps.toList(),
                                    currentActionDescription = cancelMsg
                                )
                                taskDao.updateTaskProgress(
                                    id = taskId,
                                    status = TaskRunStatus.CANCELLED.name,
                                    stepIndex = stepNumber - 1,
                                    stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                    summary = cancelMsg,
                                    updatedAt = System.currentTimeMillis()
                                )
                                postAssistantMessage(sessionId, cancelMsg, "Cancelled")
                                return@launch
                            }
                        }
                        SafetyCheckResult.Safe -> Unit
                    }

                    // Handle terminal / summary decisions
                    when (decision.action) {
                        ActionType.COMPLETE -> {
                            markLastStep(executionSteps, StepStatus.SUCCEEDED)
                            val finalMsg = decision.spokenMessage ?: "Task completed."
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.COMPLETED,
                                plannedSteps = executionSteps.toList(),
                                currentActionDescription = finalMsg
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.COMPLETED.name,
                                stepIndex = stepNumber,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = finalMsg,
                                updatedAt = System.currentTimeMillis()
                            )
                            postAssistantMessage(sessionId, finalMsg, "Completed")
                            if (settings.voiceFeedbackEnabled) voiceManager.speak(finalMsg, settings.voiceLanguage)
                            return@launch
                        }

                        ActionType.FAIL -> {
                            markLastStep(executionSteps, StepStatus.FAILED)
                            val failMsg = decision.spokenMessage ?: decision.thought.ifBlank {
                                "Could not complete this task on the current screen."
                            }
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.FAILED,
                                plannedSteps = executionSteps.toList(),
                                currentActionDescription = failMsg,
                                lastError = failMsg
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.FAILED.name,
                                stepIndex = stepNumber,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = failMsg,
                                updatedAt = System.currentTimeMillis()
                            )
                            postAssistantMessage(sessionId, failMsg, "Failed")
                            if (settings.voiceFeedbackEnabled) voiceManager.speak(failMsg, settings.voiceLanguage)
                            return@launch
                        }

                        ActionType.READ_AND_SUMMARIZE -> {
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.EXECUTING,
                                currentActionDescription = "Reading visible screen content & summarizing..."
                            )
                            val latestReadSnapshot = service?.captureFreshSnapshot() ?: snapshot
                            if (latestReadSnapshot?.isProtectedScreen == true) {
                                val protectedMsg = "Protected screen detected — AuraPilot never reads or summarizes sensitive screens."
                                markLastStep(executionSteps, StepStatus.FAILED)
                                _engineState.value = _engineState.value.copy(
                                    status = AgentStatus.FAILED,
                                    plannedSteps = executionSteps.toList(),
                                    currentActionDescription = protectedMsg,
                                    lastError = protectedMsg
                                )
                                taskDao.updateTaskProgress(
                                    id = taskId,
                                    status = TaskRunStatus.FAILED.name,
                                    stepIndex = stepNumber - 1,
                                    stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                    summary = protectedMsg,
                                    updatedAt = System.currentTimeMillis()
                                )
                                postAssistantMessage(sessionId, protectedMsg, "Protected Screen")
                                return@launch
                            }
                            val freshText = latestReadSnapshot?.fullReadableText().orEmpty()

                            val summaryText = if (freshText.isBlank()) {
                                decision.spokenMessage ?: "No readable text found on the current screen."
                            } else {
                                when (val sumRes = geminiClient.summarizeContent(apiKey, settings, command, freshText)) {
                                    is GeminiResult.Success -> {
                                        settingsRepository.recordTokenUsage(
                                            sumRes.promptTokens,
                                            sumRes.completionTokens
                                        )
                                        sumRes.data
                                    }
                                    is GeminiResult.Failure -> sumRes.message
                                }
                            }
                            markLastStep(executionSteps, StepStatus.SUCCEEDED)
                            actionLogDao.insertLog(
                                ActionLogEntity(
                                    taskId = taskId,
                                    stepIndex = stepNumber,
                                    actionType = ActionType.READ_AND_SUMMARIZE.name,
                                    targetDescription = "Read screen (${freshText.length} chars)",
                                    packageName = snapshot?.packageName.orEmpty(),
                                    success = true
                                )
                            )
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.COMPLETED,
                                plannedSteps = executionSteps.toList(),
                                currentActionDescription = "Summary ready"
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.COMPLETED.name,
                                stepIndex = stepNumber,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = summaryText,
                                updatedAt = System.currentTimeMillis()
                            )
                            postAssistantMessage(sessionId, summaryText, "Summary")
                            if (settings.voiceFeedbackEnabled) voiceManager.speak(summaryText, settings.voiceLanguage)
                            return@launch
                        }

                        else -> {
                            // Execute UI / App Action
                            _engineState.value = _engineState.value.copy(
                                status = AgentStatus.EXECUTING,
                                currentActionDescription = stepDesc
                            )
                            AgentForegroundService.startOrUpdate(context, stepDesc)

                            val execResult = executeSingleAction(decision, snapshot)
                            markLastStep(
                                executionSteps,
                                if (execResult.success) StepStatus.SUCCEEDED else StepStatus.FAILED
                            )
                            actionLogDao.insertLog(
                                ActionLogEntity(
                                    taskId = taskId,
                                    stepIndex = stepNumber,
                                    actionType = decision.action.name,
                                    targetDescription = execResult.detail,
                                    packageName = snapshot?.packageName.orEmpty(),
                                    success = execResult.success,
                                    errorMessage = if (execResult.success) null else execResult.detail
                                )
                            )

                            stepHistory.add(
                                "${decision.action.name} (${execResult.detail}) -> ${if (execResult.success) "SUCCESS" else "FAILED"}"
                            )
                            _engineState.value = _engineState.value.copy(
                                plannedSteps = executionSteps.toList()
                            )
                            taskDao.updateTaskProgress(
                                id = taskId,
                                status = TaskRunStatus.RUNNING.name,
                                stepIndex = stepNumber,
                                stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                                summary = execResult.detail,
                                updatedAt = System.currentTimeMillis()
                            )

                            if (settings.voiceFeedbackEnabled && !decision.spokenMessage.isNullOrBlank() && stepNumber == 1) {
                                voiceManager.speak(decision.spokenMessage, settings.voiceLanguage)
                            }

                            // Allow UI transition & window content settle before next inspection
                            delay(if (decision.action == ActionType.OPEN_APP || decision.action == ActionType.OPEN_URL) 1800L else 1000L)
                        }
                    }
                }

                // Reached step limit
                val limitMsg = "Stopped after $MAX_STEPS steps. You can continue with a follow-up command."
                _engineState.value = _engineState.value.copy(
                    status = AgentStatus.COMPLETED,
                    currentActionDescription = limitMsg
                )
                taskDao.updateTaskProgress(
                    id = taskId,
                    status = TaskRunStatus.COMPLETED.name,
                    stepIndex = MAX_STEPS,
                    stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                    summary = limitMsg,
                    updatedAt = System.currentTimeMillis()
                )
                postAssistantMessage(sessionId, limitMsg, "Step Limit")
            } catch (_: CancellationException) {
                val stoppedMsg = "Execution stopped by user."
                _engineState.value = _engineState.value.copy(
                    status = AgentStatus.IDLE,
                    pendingConfirmation = null,
                    currentActionDescription = stoppedMsg
                )
                taskDao.updateTaskProgress(
                    id = taskId,
                    status = TaskRunStatus.CANCELLED.name,
                    stepIndex = executionSteps.size,
                    stepsJson = stringListAdapter.toJson(lastPlannedLabels),
                    summary = stoppedMsg,
                    updatedAt = System.currentTimeMillis()
                )
                postAssistantMessage(sessionId, stoppedMsg, "Stopped")
            } finally {
                if (activeRunId == currentRunId) {
                    activeRunId = null
                    AccessibilityBridge.setAutomationActive(false)
                    AgentForegroundService.stop(context)
                }
            }
        }
    }

    fun respondToConfirmation(approved: Boolean) {
        confirmationDeferred?.complete(approved)
    }

    fun stopExecution() {
        activeRunId = null
        AccessibilityBridge.setAutomationActive(false)
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        confirmationDeferred?.complete(false)
        activeJob?.cancel()
        AgentForegroundService.stop(context)
        _engineState.value = _engineState.value.copy(
            status = AgentStatus.IDLE,
            pendingConfirmation = null,
            currentActionDescription = "Stopped by user."
        )
    }

    private suspend fun executeSingleAction(
        decision: AgentDecision,
        snapshot: ScreenSnapshot?
    ): ActionExecResult {
        val service = AccessibilityBridge.activeService
        return when (decision.action) {
            ActionType.OPEN_APP -> {
                val target = decision.targetApp.orEmpty()
                when (val res = installedAppsHelper.launchApp(target)) {
                    is InstalledAppsHelper.LaunchResult.Launched ->
                        ActionExecResult(true, "Opened ${res.label}")
                    is InstalledAppsHelper.LaunchResult.Failed ->
                        ActionExecResult(false, res.reason)
                }
            }
            ActionType.OPEN_URL -> {
                val url = decision.url.orEmpty()
                when (val res = installedAppsHelper.openUrl(url)) {
                    is InstalledAppsHelper.LaunchResult.Launched ->
                        ActionExecResult(true, "Opened URL ${res.label}")
                    is InstalledAppsHelper.LaunchResult.Failed ->
                        ActionExecResult(false, res.reason)
                }
            }
            ActionType.TAP_NODE -> {
                val idx = decision.nodeIndex ?: return ActionExecResult(false, "No node index provided")
                val label = snapshot?.nodes?.getOrNull(idx)?.let {
                    it.text.ifBlank { it.contentDescription }.ifBlank { "node #$idx" }
                } ?: "node #$idx"
                val ok = service?.tapNodeByIndex(idx) ?: false
                ActionExecResult(ok, if (ok) "Tapped $label" else "Failed to tap $label")
            }
            ActionType.TAP_TEXT -> {
                val targetText = decision.targetText.orEmpty()
                val ok = service?.tapByTextOrDescription(targetText) ?: false
                ActionExecResult(ok, if (ok) "Tapped '$targetText'" else "Could not find '$targetText'")
            }
            ActionType.TYPE_TEXT -> {
                val text = decision.inputText.orEmpty()
                val ok = service?.inputTextIntoNode(
                    nodeIndex = decision.nodeIndex,
                    text = text,
                    submitAfter = decision.submitAfterInput
                ) ?: false
                ActionExecResult(ok, if (ok) "Typed \"$text\"" else "Failed to type \"$text\"")
            }
            ActionType.SCROLL_DOWN -> {
                val ok = service?.scrollScreen(scrollDown = true) ?: false
                ActionExecResult(ok, if (ok) "Scrolled down" else "Scroll down failed")
            }
            ActionType.SCROLL_UP -> {
                val ok = service?.scrollScreen(scrollDown = false) ?: false
                ActionExecResult(ok, if (ok) "Scrolled up" else "Scroll up failed")
            }
            ActionType.PRESS_BACK -> {
                val ok = service?.performBack() ?: false
                ActionExecResult(ok, if (ok) "Navigated back" else "Back action failed")
            }
            ActionType.PRESS_HOME -> {
                val ok = service?.performHome() ?: false
                ActionExecResult(ok, if (ok) "Went to Home screen" else "Home action failed")
            }
            ActionType.WAIT -> {
                delay(1500L)
                ActionExecResult(true, "Waited for screen update")
            }
            else -> ActionExecResult(false, "Unsupported action ${decision.action}")
        }
    }

    private fun requiresAccessibility(action: ActionType): Boolean {
        return when (action) {
            ActionType.OPEN_APP,
            ActionType.OPEN_URL,
            ActionType.WAIT,
            ActionType.COMPLETE,
            ActionType.FAIL -> false
            else -> true
        }
    }

    private fun markLastStep(steps: MutableList<ExecutionStep>, status: StepStatus) {
        if (steps.isEmpty()) return
        val last = steps.removeAt(steps.lastIndex)
        steps.add(last.copy(status = status))
    }

    private suspend fun postAssistantMessage(sessionId: String, text: String, tag: String?) {
        chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT.name,
                content = text,
                statusTag = tag
            )
        )
    }

    private data class ActionExecResult(val success: Boolean, val detail: String)

    companion object {
        private const val MAX_STEPS = 12
    }
}
