package com.example.data

import android.content.Context
import com.example.ai.GeminiClient
import com.example.automation.AgentOrchestrator
import com.example.automation.InstalledAppsHelper
import com.example.database.ActionLogDao
import com.example.database.AutomationTaskDao
import com.example.database.AuraDatabase
import com.example.database.ChatDao
import com.example.domain.model.ActionLog
import com.example.domain.model.AutomationTask
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.TaskRunStatus
import com.example.permissions.PermissionManager
import com.example.settings.AndroidKeystoreCryptoManager
import com.example.settings.SettingsRepository
import com.example.voice.VoiceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatDao: ChatDao) {

    fun observeAllMessages(): Flow<List<ChatMessage>> =
        chatDao.observeAllMessages().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    role = runCatching { MessageRole.valueOf(entity.role) }
                        .getOrDefault(MessageRole.ASSISTANT),
                    content = entity.content,
                    statusTag = entity.statusTag,
                    tokenCount = entity.tokenCount,
                    timestamp = entity.timestamp
                )
            }
        }

    fun observeSessionMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.observeSessionMessages(sessionId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    role = runCatching { MessageRole.valueOf(entity.role) }
                        .getOrDefault(MessageRole.ASSISTANT),
                    content = entity.content,
                    statusTag = entity.statusTag,
                    tokenCount = entity.tokenCount,
                    timestamp = entity.timestamp
                )
            }
        }

    suspend fun clearHistory() {
        chatDao.clearAll()
    }
}

class TaskRepository(
    private val taskDao: AutomationTaskDao,
    private val actionLogDao: ActionLogDao
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val stepsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun observeTasks(): Flow<List<AutomationTask>> =
        taskDao.observeAllTasks().map { list ->
            list.map { entity ->
                val steps = runCatching { stepsAdapter.fromJson(entity.plannedStepsJson) }
                    .getOrNull().orEmpty()
                AutomationTask(
                    id = entity.id,
                    title = entity.title,
                    userCommand = entity.userCommand,
                    status = runCatching { TaskRunStatus.valueOf(entity.status) }
                        .getOrDefault(TaskRunStatus.COMPLETED),
                    plannedSteps = steps,
                    currentStepIndex = entity.currentStepIndex,
                    summary = entity.summary,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }

    fun observeAllLogs(): Flow<List<ActionLog>> =
        actionLogDao.observeAllLogs().map { list ->
            list.map { entity ->
                ActionLog(
                    id = entity.id,
                    taskId = entity.taskId,
                    stepIndex = entity.stepIndex,
                    actionType = entity.actionType,
                    targetDescription = entity.targetDescription,
                    packageName = entity.packageName,
                    success = entity.success,
                    errorMessage = entity.errorMessage,
                    timestamp = entity.timestamp
                )
            }
        }

    suspend fun clearAllTasks() {
        actionLogDao.clearAllLogs()
        taskDao.clearAllTasks()
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AuraDatabase by lazy { AuraDatabase.getInstance(appContext) }
    val cryptoManager: AndroidKeystoreCryptoManager by lazy { AndroidKeystoreCryptoManager() }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext, cryptoManager)
    }
    val geminiClient: GeminiClient by lazy { GeminiClient() }
    val installedAppsHelper: InstalledAppsHelper by lazy { InstalledAppsHelper(appContext) }
    val permissionManager: PermissionManager by lazy { PermissionManager(appContext) }
    val voiceManager: VoiceManager by lazy { VoiceManager(appContext) }
    val chatRepository: ChatRepository by lazy { ChatRepository(database.chatDao()) }
    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.automationTaskDao(), database.actionLogDao())
    }
    val agentOrchestrator: AgentOrchestrator by lazy {
        AgentOrchestrator(
            context = appContext,
            settingsRepository = settingsRepository,
            geminiClient = geminiClient,
            installedAppsHelper = installedAppsHelper,
            permissionManager = permissionManager,
            voiceManager = voiceManager,
            chatDao = database.chatDao(),
            taskDao = database.automationTaskDao(),
            actionLogDao = database.actionLogDao()
        )
    }
}
