package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeSessionMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface AutomationTaskDao {
    @Query("SELECT * FROM automation_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<AutomationTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AutomationTaskEntity): Long

    @Query(
        "UPDATE automation_tasks SET status = :status, currentStepIndex = :stepIndex, " +
            "plannedStepsJson = :stepsJson, summary = :summary, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateTaskProgress(
        id: Long,
        status: String,
        stepIndex: Int,
        stepsJson: String,
        summary: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM automation_tasks")
    suspend fun clearAllTasks()
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun observeAllLogs(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE taskId = :taskId ORDER BY stepIndex ASC, timestamp ASC")
    fun observeLogsForTask(taskId: Long): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearAllLogs()
}
