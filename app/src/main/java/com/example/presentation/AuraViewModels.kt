package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.accessibility.AccessibilityBridge
import com.example.ai.GeminiClient
import com.example.ai.GeminiResult
import com.example.automation.AgentEngineState
import com.example.automation.AgentOrchestrator
import com.example.data.AppContainer
import com.example.data.ChatRepository
import com.example.data.TaskRepository
import com.example.domain.model.ActionLog
import com.example.domain.model.AgentStatus
import com.example.domain.model.AiSettings
import com.example.domain.model.AutomationTask
import com.example.domain.model.ChatMessage
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.PermissionSnapshot
import com.example.domain.model.VoiceLanguage
import com.example.permissions.PermissionManager
import com.example.settings.SettingsRepository
import com.example.voice.VoiceManager
import com.example.voice.VoiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val engine: AgentEngineState = AgentEngineState(),
    val voice: VoiceState = VoiceState(),
    val settings: AiSettings = AiSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val sessionMessages: List<ChatMessage> = emptyList(),
    val commandInput: String = "",
    val currentPackageName: String = ""
) {
    val effectiveStatus: AgentStatus
        get() = if (voice.isListening) AgentStatus.LISTENING else engine.status
}

class HomeViewModel(
    private val orchestrator: AgentOrchestrator,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager,
    private val voiceManager: VoiceManager
) : ViewModel() {

    private val _commandInput = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionMessagesFlow = orchestrator.engineState
        .map { it.activeSessionId }
        .flatMapLatest { sessionId -> chatRepository.observeSessionMessages(sessionId) }

    val uiState: StateFlow<HomeUiState> = combine(
        orchestrator.engineState,
        voiceManager.voiceState,
        settingsRepository.settingsFlow,
        permissionManager.permissions,
        sessionMessagesFlow,
        _commandInput,
        AccessibilityBridge.latestSnapshot
    ) { values ->
        val engine = values[0] as AgentEngineState
        val voice = values[1] as VoiceState
        val settings = values[2] as AiSettings
        val permissions = values[3] as PermissionSnapshot
        @Suppress("UNCHECKED_CAST")
        val messages = values[4] as List<ChatMessage>
        val input = values[5] as String
        val snapshot = values[6] as com.example.accessibility.ScreenSnapshot?

        HomeUiState(
            engine = engine,
            voice = voice,
            settings = settings,
            permissions = permissions,
            sessionMessages = messages,
            commandInput = input,
            currentPackageName = snapshot?.packageName.orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onCommandInputChange(value: String) {
        _commandInput.value = value
    }

    fun sendCurrentCommand() {
        val text = _commandInput.value.trim()
        if (text.isEmpty()) return
        _commandInput.value = ""
        orchestrator.submitCommand(text)
    }

    fun submitQuickCommand(command: String) {
        _commandInput.value = ""
        orchestrator.submitCommand(command)
    }

    fun startVoiceListening() {
        viewModelScope.launch {
            val lang = settingsRepository.settingsFlow.first().voiceLanguage
            voiceManager.startListening(
                language = lang,
                onResult = { transcript ->
                    _commandInput.value = ""
                    orchestrator.submitCommand(transcript)
                },
                onError = { _ -> }
            )
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun stopExecution() {
        orchestrator.stopExecution()
    }

    fun respondToConfirmation(approved: Boolean) {
        orchestrator.respondToConfirmation(approved)
    }

    fun startNewSession() {
        orchestrator.startNewSession()
    }

    fun refreshPermissions() {
        permissionManager.refresh()
    }
}

class HistoryViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = combine(
        chatRepository.observeAllMessages(),
        _searchQuery
    ) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.content.contains(query, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
        }
    }
}

data class TasksUiState(
    val tasks: List<AutomationTask> = emptyList(),
    val logsByTaskId: Map<Long, List<ActionLog>> = emptyMap()
)

class TasksViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = combine(
        taskRepository.observeTasks(),
        taskRepository.observeAllLogs()
    ) { tasks, logs ->
        TasksUiState(
            tasks = tasks,
            logsByTaskId = logs.groupBy { it.taskId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState()
    )

    fun clearTasks() {
        viewModelScope.launch {
            taskRepository.clearAllTasks()
        }
    }
}

data class SettingsUiState(
    val settings: AiSettings = AiSettings(),
    val apiKeyDraft: String = "",
    val isTestingConnection: Boolean = false,
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient
) : ViewModel() {

    private val _apiKeyDraft = MutableStateFlow("")
    private val _isTesting = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _apiKeyDraft,
        _isTesting,
        _feedback
    ) { settings, draft, testing, feedback ->
        SettingsUiState(
            settings = settings,
            apiKeyDraft = draft,
            isTestingConnection = testing,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onApiKeyDraftChange(value: String) {
        _apiKeyDraft.value = value
    }

    fun saveApiKey() {
        val raw = _apiKeyDraft.value.trim()
        if (raw.isEmpty()) {
            _feedback.value = "Please enter a valid Gemini API key."
            return
        }
        viewModelScope.launch {
            settingsRepository.saveApiKey(raw)
            _apiKeyDraft.value = ""
            _feedback.value = "API key encrypted with Android Keystore and saved."
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTesting.value = true
            _feedback.value = null

            val draft = _apiKeyDraft.value.trim()
            val keyToTest = if (draft.isNotEmpty()) {
                settingsRepository.saveApiKey(draft)
                _apiKeyDraft.value = ""
                draft
            } else {
                settingsRepository.getDecryptedApiKey().orEmpty()
            }

            if (keyToTest.isBlank()) {
                settingsRepository.updateConnectionResult(
                    status = ConnectionStatus.FAILED,
                    message = "No Gemini API key configured. Enter your key first."
                )
                _feedback.value = "Enter a Gemini API key first."
                _isTesting.value = false
                return@launch
            }

            when (val result = geminiClient.testConnection(keyToTest)) {
                is GeminiResult.Success -> {
                    val models = result.data
                    val msg = "Connected (${models.size} Gemini models available)"
                    settingsRepository.updateConnectionResult(
                        status = ConnectionStatus.CONNECTED,
                        message = msg,
                        fetchedModels = models
                    )
                    _feedback.value = "Connection succeeded! Loaded ${models.size} live Gemini models."
                }
                is GeminiResult.Failure -> {
                    settingsRepository.updateConnectionResult(
                        status = ConnectionStatus.FAILED,
                        message = "Connection Failed: ${result.message}"
                    )
                    _feedback.value = result.message
                }
            }
            _isTesting.value = false
        }
    }

    fun removeApiKey() {
        viewModelScope.launch {
            settingsRepository.removeApiKey()
            _apiKeyDraft.value = ""
            _feedback.value = "API key removed from encrypted storage."
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.updateSelectedModel(modelId)
        }
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch {
            settingsRepository.updateTemperature(temp)
        }
    }

    fun updateSystemInstructions(instructions: String) {
        viewModelScope.launch {
            settingsRepository.updateSystemInstructions(instructions)
            _feedback.value = "System instructions updated."
        }
    }

    fun toggleVoiceFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateVoiceFeedback(enabled)
        }
    }

    fun selectVoiceLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            settingsRepository.updateVoiceLanguage(language)
        }
    }

    fun toggleSensitiveConfirmation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateRequireConfirmation(enabled)
        }
    }

    fun resetTokenUsage() {
        viewModelScope.launch {
            settingsRepository.resetTokenUsage()
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}

class PermissionsViewModel(
    private val permissionManager: PermissionManager
) : ViewModel() {

    val permissions: StateFlow<PermissionSnapshot> = permissionManager.permissions

    fun refresh() {
        permissionManager.refresh()
    }

    fun openAccessibilitySettings() {
        permissionManager.openAccessibilitySettings()
    }

    fun openAppSettings() {
        permissionManager.openAppSettings()
    }
}

class AuraViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
                container.agentOrchestrator,
                container.chatRepository,
                container.settingsRepository,
                container.permissionManager,
                container.voiceManager
            ) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(
                container.chatRepository
            ) as T
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(
                container.taskRepository
            ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                container.settingsRepository,
                container.geminiClient
            ) as T
            modelClass.isAssignableFrom(PermissionsViewModel::class.java) -> PermissionsViewModel(
                container.permissionManager
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
