package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val statusTag: String? = null,
    val tokenCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_tasks")
data class AutomationTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val userCommand: String,
    val status: String,
    val plannedStepsJson: String,
    val currentStepIndex: Int = 0,
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val stepIndex: Int,
    val actionType: String,
    val targetDescription: String,
    val packageName: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
